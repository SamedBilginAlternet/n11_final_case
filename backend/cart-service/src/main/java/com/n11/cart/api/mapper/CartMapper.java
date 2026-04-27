package com.n11.cart.api.mapper;

import com.n11.cart.api.dto.CartDto;
import com.n11.cart.api.dto.CartItemDto;
import com.n11.cart.domain.Cart;
import com.n11.cart.domain.CartItem;
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

    default CartDto toDto(Cart cart) {
        if (cart == null) return null;
        List<CartItemDto> items = toItemDtos(cart.getItems());
        BigDecimal total = items.stream().map(CartItemDto::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int qty = items.stream().mapToInt(CartItemDto::quantity).sum();
        String currency = items.isEmpty() ? "TRY" : items.get(0).currency();
        return new CartDto(cart.getId(), cart.getUserId(), items, total, currency, qty);
    }

    default CartDto empty(Long userId) {
        return new CartDto(null, userId, List.of(), BigDecimal.ZERO, "TRY", 0);
    }
}
