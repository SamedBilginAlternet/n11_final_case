package com.n11.payment.repository;

import com.n11.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByCreatedAtAsc(Long orderId);

    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);
}
