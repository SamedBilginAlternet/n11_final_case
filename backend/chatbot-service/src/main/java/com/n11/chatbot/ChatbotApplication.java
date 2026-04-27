package com.n11.chatbot;

import com.n11.chatbot.config.ChatbotProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableConfigurationProperties(ChatbotProperties.class)
@ComponentScan(basePackages = {"com.n11.chatbot", "com.n11.common"})
public class ChatbotApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }
}
