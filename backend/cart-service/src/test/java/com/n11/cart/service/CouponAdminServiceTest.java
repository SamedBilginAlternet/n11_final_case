package com.n11.cart.service;

import com.n11.cart.api.dto.CouponDto;
import com.n11.cart.api.dto.CouponWriteRequest;
import com.n11.cart.domain.Coupon;
import com.n11.cart.domain.CouponType;
import com.n11.cart.repository.CouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponAdminServiceTest {

    @Mock CouponRepository couponRepository;

    @InjectMocks CouponAdminService service;

    private CouponWriteRequest req(String code, CouponType type, String value) {
        return new CouponWriteRequest(code, "test", type, new BigDecimal(value),
                null, null, null, null, true);
    }

    @Test
    void createUppercasesAndSavesCode() {
        when(couponRepository.findByCodeIgnoreCase("yeni20")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> {
            Coupon c = inv.getArgument(0);
            c.setId(42L);
            return c;
        });

        CouponDto out = service.create(req("yeni20", CouponType.PERCENT, "20"));

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("YENI20");
        assertThat(out.id()).isEqualTo(42L);
    }

    @Test
    void createWithDuplicateCodeThrows409() {
        Coupon existing = Coupon.builder().id(1L).code("YENI20").build();
        when(couponRepository.findByCodeIgnoreCase("yeni20")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(req("yeni20", CouponType.PERCENT, "20")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("zaten kullanılıyor");
        verify(couponRepository, never()).save(any());
    }

    @Test
    void createPercentOver100Throws400() {
        // No findByCodeIgnoreCase stub needed — validatePercentRange runs before
        // the duplicate check, so the test must avoid stubbing an unused mock
        // (Mockito strict-stub mode would otherwise fail with UnnecessaryStubbingException).

        assertThatThrownBy(() -> service.create(req("BIG", CouponType.PERCENT, "150")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("0-100 aralığında");
    }

    @Test
    void createFixedAmountSkipsPercentBoundsCheck() {
        when(couponRepository.findByCodeIgnoreCase("tl500")).thenReturn(Optional.empty());
        when(couponRepository.save(any(Coupon.class))).thenAnswer(inv -> {
            Coupon c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });

        // value=500 would fail the percent 0-100 bounds, but FIXED bypasses it.
        CouponDto out = service.create(req("tl500", CouponType.FIXED, "500"));
        assertThat(out.id()).isEqualTo(5L);
    }

    @Test
    void deleteRefusesIfRedeemed() {
        Coupon c = Coupon.builder().id(1L).code("X").redemptions(3).build();
        when(couponRepository.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("daha önce kullanıldı");
        verify(couponRepository, never()).delete(any(Coupon.class));
    }

    @Test
    void deleteAllowsIfNeverRedeemed() {
        Coupon c = Coupon.builder().id(2L).code("Y").redemptions(0).build();
        when(couponRepository.findById(2L)).thenReturn(Optional.of(c));

        service.delete(2L);
        verify(couponRepository).delete(c);
    }

    @Test
    void listFiltersByActiveFlag() {
        Coupon active = Coupon.builder().id(1L).code("A").active(true).redemptions(0).build();
        Coupon inactive = Coupon.builder().id(2L).code("B").active(false).redemptions(0).build();
        Page<Coupon> page = new PageImpl<>(List.of(active, inactive));
        when(couponRepository.findAll(any(PageRequest.class))).thenReturn(page);

        List<CouponDto> activeOnly = service.list(true, PageRequest.of(0, 10));
        assertThat(activeOnly).hasSize(1);
        assertThat(activeOnly.get(0).code()).isEqualTo("A");
    }
}
