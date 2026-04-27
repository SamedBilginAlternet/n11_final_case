package com.n11.chatbot;

import com.n11.chatbot.config.ChatbotProperties;
import com.n11.chatbot.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableConfigurationProperties({ChatbotProperties.class, RateLimitProperties.class})
@ComponentScan(basePackages = {"com.n11.chatbot", "com.n11.common"})
public class ChatbotApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }
}
