package com.n11.cart.repository;

import com.n11.cart.domain.Campaign;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    // Cached as a single 'all active' list — admin changes are rare and 60s
    // drift is acceptable. The default findActiveAt method below filters
    // the cached list in-memory by valid-from/until window, so we don't
    // also need a per-instant cache (which would have terrible hit rate).
    @Cacheable(cacheNames = "campaigns:active", key = "'all'")
    List<Campaign> findByActiveTrueOrderByPriorityAsc();

    default List<Campaign> findActiveAt(Instant instant) {
        return findByActiveTrueOrderByPriorityAsc().stream()
                .filter(c -> c.getValidFrom() == null || !instant.isBefore(c.getValidFrom()))
                .filter(c -> c.getValidUntil() == null || !instant.isAfter(c.getValidUntil()))
                .toList();
    }
}
