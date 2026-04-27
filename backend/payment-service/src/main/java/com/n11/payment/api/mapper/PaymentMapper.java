package com.n11.payment.api.mapper;

import com.n11.payment.api.dto.PaymentDto;
import com.n11.payment.domain.Payment;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentDto toDto(Payment payment);

    List<PaymentDto> toDtos(List<Payment> payments);
}
