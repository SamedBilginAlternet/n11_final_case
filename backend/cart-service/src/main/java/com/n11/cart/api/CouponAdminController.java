package com.n11.cart.api;

import com.n11.cart.api.dto.CouponDto;
import com.n11.cart.api.dto.CouponWriteRequest;
import com.n11.cart.service.CouponAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Coupons")
public class CouponAdminController {

    private final CouponAdminService service;

    @Operation(summary = "List coupons (admin)")
    @GetMapping
    public List<CouponDto> list(@RequestParam(required = false) Boolean activeOnly,
                                @PageableDefault(size = 50) Pageable pageable) {
        return service.list(activeOnly, pageable);
    }

    @Operation(summary = "Get coupon by id (admin)")
    @GetMapping("/{id}")
    public CouponDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Create coupon (admin)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponDto create(@RequestBody @Valid CouponWriteRequest body) {
        return service.create(body);
    }

    @Operation(summary = "Update coupon (admin)")
    @PutMapping("/{id}")
    public CouponDto update(@PathVariable Long id, @RequestBody @Valid CouponWriteRequest body) {
        return service.update(id, body);
    }

    @Operation(summary = "Delete coupon (admin) — fails if it has any redemptions; deactivate instead")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
