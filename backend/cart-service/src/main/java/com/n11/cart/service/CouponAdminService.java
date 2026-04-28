package com.n11.cart.service;

import com.n11.cart.api.dto.CouponDto;
import com.n11.cart.api.dto.CouponWriteRequest;
import com.n11.cart.domain.Coupon;
import com.n11.cart.domain.CouponType;
import com.n11.cart.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Admin coupon CRUD.
 *
 * <p>Why a hard-delete is gated: the {@code coupon_redemptions} table
 * audits every successful coupon use, and the FK there points at
 * {@code coupons.id}.  Deleting a coupon that has been redeemed would
 * either cascade-wipe the audit log or fail with a constraint violation.
 * Instead, the controller surfaces a 409 and tells the admin to
 * deactivate via {@code active=false} — same effect for customers
 * (isValidAt returns false) without losing history.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CouponAdminService {

    private final CouponRepository couponRepository;

    @Transactional(readOnly = true)
    public List<CouponDto> list(Boolean activeOnly, Pageable pageable) {
        return couponRepository.findAll(pageable).stream()
                .filter(c -> activeOnly == null || c.getActive().equals(activeOnly))
                .map(CouponDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponDto get(Long id) {
        return CouponDto.from(findOrThrow(id));
    }

    @CacheEvict(cacheNames = "coupons:byCode", allEntries = true)
    public CouponDto create(CouponWriteRequest req) {
        validatePercentRange(req.type(), req.value());
        couponRepository.findByCodeIgnoreCase(req.code()).ifPresent(c -> {
            throw new ResponseStatusException(CONFLICT, "Bu kupon kodu zaten kullanılıyor: " + req.code());
        });
        Coupon c = Coupon.builder()
                .code(req.code().toUpperCase())
                .label(req.label())
                .type(req.type())
                .value(req.value())
                .minCartTotal(req.minCartTotal())
                .maxRedemptions(req.maxRedemptions())
                .redemptions(0)
                .validFrom(req.validFrom())
                .validUntil(req.validUntil())
                .active(req.active() == null ? true : req.active())
                .build();
        Coupon saved = couponRepository.save(c);
        log.info("Admin created coupon id={} code={}", saved.getId(), saved.getCode());
        return CouponDto.from(saved);
    }

    @CacheEvict(cacheNames = "coupons:byCode", allEntries = true)
    public CouponDto update(Long id, CouponWriteRequest req) {
        validatePercentRange(req.type(), req.value());
        Coupon c = findOrThrow(id);

        if (!c.getCode().equalsIgnoreCase(req.code())) {
            Optional<Coupon> existing = couponRepository.findByCodeIgnoreCase(req.code());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw new ResponseStatusException(CONFLICT, "Bu kupon kodu zaten kullanılıyor: " + req.code());
            }
            c.setCode(req.code().toUpperCase());
        }
        c.setLabel(req.label());
        c.setType(req.type());
        c.setValue(req.value());
        c.setMinCartTotal(req.minCartTotal());
        c.setMaxRedemptions(req.maxRedemptions());
        c.setValidFrom(req.validFrom());
        c.setValidUntil(req.validUntil());
        if (req.active() != null) c.setActive(req.active());
        log.info("Admin updated coupon id={} code={}", c.getId(), c.getCode());
        return CouponDto.from(c);
    }

    @CacheEvict(cacheNames = "coupons:byCode", allEntries = true)
    public void delete(Long id) {
        Coupon c = findOrThrow(id);
        if (c.getRedemptions() != null && c.getRedemptions() > 0) {
            throw new ResponseStatusException(CONFLICT,
                    "Bu kupon daha önce kullanıldı, silinemez. active=false yaparak pasifleştir.");
        }
        couponRepository.delete(c);
        log.info("Admin deleted coupon id={} code={}", id, c.getCode());
    }

    private Coupon findOrThrow(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Kupon bulunamadı: " + id));
    }

    private void validatePercentRange(CouponType type, BigDecimal value) {
        if (type == CouponType.PERCENT && (value.compareTo(BigDecimal.ZERO) <= 0
                || value.compareTo(new BigDecimal("100")) > 0)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Yüzde indirim 0-100 aralığında olmalı (gönderilen: " + value + ")");
        }
    }
}
