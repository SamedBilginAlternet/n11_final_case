package com.n11.chatbot.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_sessions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatSession {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "guest_token", length = 64)
    private String guestToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.id == null) this.id = UUID.randomUUID().toString();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
