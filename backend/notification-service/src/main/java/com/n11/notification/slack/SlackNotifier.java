package com.n11.notification.slack;

import com.n11.notification.config.NotificationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class SlackNotifier {

    private final NotificationProperties props;
    private final RestClient client;

    public SlackNotifier(NotificationProperties props) {
        this.props = props;
        this.client = RestClient.create();
    }

    public void send(String text) {
        if (!props.enabled() || props.webhookUrl() == null || props.webhookUrl().isBlank()) {
            log.info("[slack-disabled] {}", text);
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", text);
        if (props.channel() != null && !props.channel().isBlank()) payload.put("channel", props.channel());
        if (props.username() != null && !props.username().isBlank()) payload.put("username", props.username());
        try {
            client.post().uri(props.webhookUrl()).contentType(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Slack webhook delivery failed: {}", ex.getMessage());
        }
    }
}
