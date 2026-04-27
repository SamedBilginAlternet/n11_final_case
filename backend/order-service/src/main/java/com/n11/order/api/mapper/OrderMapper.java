package com.n11.order.api.mapper;

import com.n11.order.api.dto.OrderDto;
import com.n11.order.api.dto.OrderItemDto;
import com.n11.order.domain.Order;
import com.n11.order.domain.OrderItem;
import org.mapstruct.Mapper;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderDto toDto(Order order);

    default OrderItemDto toItemDto(OrderItem item) {
        return new OrderItemDto(
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
        );
    }
}
