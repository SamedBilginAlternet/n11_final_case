package com.n11.order.api.mapper;

import com.n11.order.api.dto.OrderDto;
import com.n11.order.api.dto.OrderItemDto;
import com.n11.order.domain.Order;
import com.n11.order.domain.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "shipping", source = ".", qualifiedByName = "shipping")
    @Mapping(target = "tracking", source = ".", qualifiedByName = "tracking")
    @Mapping(target = "timeline", source = ".", qualifiedByName = "timeline")
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

    @Named("shipping")
    default OrderDto.ShippingDto shipping(Order order) {
        if (order.getShippingLine1() == null) return null;
        return new OrderDto.ShippingDto(
                order.getShippingRecipient(),
                order.getShippingPhone(),
                order.getShippingLine1(),
                order.getShippingCity(),
                order.getShippingDistrict(),
                order.getShippingPostalCode()
        );
    }

    @Named("tracking")
    default OrderDto.TrackingDto tracking(Order order) {
        if (order.getCarrier() == null && order.getTrackingNumber() == null) return null;
        return new OrderDto.TrackingDto(order.getCarrier(), order.getTrackingNumber());
    }

    @Named("timeline")
    default OrderDto.TimelineDto timeline(Order order) {
        return new OrderDto.TimelineDto(
                order.getCreatedAt(),
                order.getConfirmedAt(),
                order.getProcessingAt(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getCancelledAt()
        );
    }
}
