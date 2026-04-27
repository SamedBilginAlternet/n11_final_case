package com.n11.cart.repository;

import com.n11.cart.domain.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByActiveTrueOrderByPriorityAsc();

    default List<Campaign> findActiveAt(Instant instant) {
        return findByActiveTrueOrderByPriorityAsc().stream()
                .filter(c -> c.getValidFrom() == null || !instant.isBefore(c.getValidFrom()))
                .filter(c -> c.getValidUntil() == null || !instant.isAfter(c.getValidUntil()))
                .toList();
    }
}
