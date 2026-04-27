package com.n11.cart.api.mapper;

import com.n11.cart.api.dto.AppliedDiscountDto;
import com.n11.cart.api.dto.CartDto;
import com.n11.cart.api.dto.CartItemDto;
import com.n11.cart.domain.Cart;
import com.n11.cart.domain.CartItem;
import com.n11.cart.pricing.AppliedDiscount;
import com.n11.cart.pricing.Quote;
import org.mapstruct.Mapper;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface CartMapper {

    List<CartItemDto> toItemDtos(List<CartItem> items);

    default CartItemDto toItemDto(CartItem item) {
        if (item == null) return null;
        BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemDto(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getImageUrl(),
                item.getQuantity(),
                item.getUnitPrice(),
                lineTotal,
                item.getCurrency()
        );
    }

    default AppliedDiscountDto toDiscountDto(AppliedDiscount d) {
        return d == null ? null : new AppliedDiscountDto(d.code(), d.label(), d.kind(), d.amount());
    }

    default List<AppliedDiscountDto> toDiscountDtos(List<AppliedDiscount> discounts) {
        return discounts == null ? List.of() : discounts.stream().map(this::toDiscountDto).toList();
    }

    default CartDto toDto(Cart cart, Quote quote) {
        if (cart == null) return null;
        List<CartItemDto> items = toItemDtos(cart.getItems());
        int qty = items.stream().mapToInt(CartItemDto::quantity).sum();
        String currency = items.isEmpty() ? "TRY" : items.get(0).currency();
        return new CartDto(
                cart.getId(),
                cart.getUserId(),
                items,
                quote.subtotal(),
                toDiscountDtos(quote.discounts()),
                quote.totalDiscount(),
                quote.total(),
                currency,
                qty,
                cart.getCouponCode()
        );
    }

    default CartDto empty(Long userId) {
        return new CartDto(
                null,
                userId,
                List.of(),
                BigDecimal.ZERO,
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "TRY",
                0,
                null);
    }
}
