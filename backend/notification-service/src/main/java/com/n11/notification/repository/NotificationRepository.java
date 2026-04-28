package com.n11.notification.repository;

import com.n11.notification.domain.Notification;
import com.n11.notification.domain.NotificationKind;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByOrderIdAndKind(Long orderId, NotificationKind kind);
}
