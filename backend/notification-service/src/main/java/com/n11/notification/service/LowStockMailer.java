package com.n11.notification.service;

import com.n11.common.event.LowStockReportEvent;
import com.n11.notification.config.NotificationProperties;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Renders + sends the low-stock alert mail.  Separate from EmailService
 * because there is no per-order audit row to enforce idempotency on —
 * this email is a recurring digest, not a transactional notification.
 * If a duplicate report arrives (RabbitMQ redelivery), the admin gets
 * two copies of the same digest; that's noisy but not actively harmful.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LowStockMailer {

    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationProperties properties;

    public void send(String recipient, LowStockReportEvent event) {
        Context ctx = new Context(TR);
        ctx.setVariable("items", event.items());
        ctx.setVariable("threshold", event.threshold());
        ctx.setVariable("count", event.items().size());
        ctx.setVariable("adminPanelUrl", properties.adminPanelUrl());
        String html = templateEngine.process("low-stock-alert", ctx);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(properties.fromAddress(), properties.fromName(),
                    StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            helper.setSubject("[n11 Admin] Düşük stok uyarısı — " + event.items().size() + " ürün");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Sent low-stock alert to {} ({} items)", recipient, event.items().size());
        } catch (Exception ex) {
            log.error("Failed to send low-stock alert to {}: {}", recipient, ex.getMessage(), ex);
            throw new RuntimeException("Failed to send low-stock alert", ex);
        }
    }
}
