package com.n11.notification.service;

import com.n11.notification.config.NotificationProperties;
import com.n11.notification.domain.Notification;
import com.n11.notification.domain.NotificationKind;
import com.n11.notification.domain.NotificationStatus;
import com.n11.notification.repository.NotificationRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Renders + sends one mail and stamps an audit row.
 *
 * <p>Idempotency: the {@code notifications} table has a UNIQUE(order_id, kind)
 * constraint.  If RabbitMQ redelivers the same event, the second send hits a
 * {@link DataIntegrityViolationException} on the audit insert and we treat
 * it as "already sent, skip".  This makes the listener safe to ack
 * unconditionally — duplicate deliveries are silently absorbed instead of
 * spamming the customer.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationRepository repository;
    private final NotificationProperties properties;

    public void sendOrderMail(Long orderId,
                              Long userId,
                              String recipient,
                              NotificationKind kind,
                              String subject,
                              String templateName,
                              Map<String, Object> templateModel,
                              String correlationId) {
        if (repository.existsByOrderIdAndKind(orderId, kind)) {
            log.info("Skip duplicate notification orderId={} kind={}", orderId, kind);
            return;
        }

        Context ctx = new Context(TR);
        ctx.setVariables(templateModel);
        ctx.setVariable("storefrontUrl", properties.storefrontUrl());
        String html = templateEngine.process(templateName, ctx);

        Notification record = new Notification();
        record.setOrderId(orderId);
        record.setUserId(userId);
        record.setRecipient(recipient);
        record.setKind(kind);
        record.setSubject(subject);
        record.setCorrelationId(correlationId);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(properties.fromAddress(), properties.fromName(), StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);

            record.setStatus(NotificationStatus.SENT);
            repository.saveAndFlush(record);
            log.info("Sent {} mail to {} for orderId={}", kind, recipient, orderId);
        } catch (DataIntegrityViolationException dup) {
            // race: another consumer thread won the unique-constraint check
            log.info("Skip duplicate notification (race) orderId={} kind={}", orderId, kind);
        } catch (Exception ex) {
            log.error("Failed to send {} mail to {} for orderId={}: {}", kind, recipient, orderId, ex.getMessage(), ex);
            record.setStatus(NotificationStatus.FAILED);
            record.setError(ex.getMessage());
            try { repository.saveAndFlush(record); } catch (Exception ignored) { /* audit best-effort */ }
            // re-throw so the listener nacks → DLX picks up the failure
            throw new MailDispatchException("Failed to send " + kind + " mail", ex);
        }
    }

    /** Marker for listener layer — wraps any cause from JavaMail. */
    public static class MailDispatchException extends RuntimeException {
        public MailDispatchException(String msg, Throwable cause) { super(msg, cause); }
    }
}
