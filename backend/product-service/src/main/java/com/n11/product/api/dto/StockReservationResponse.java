package com.n11.product.api.dto;

import java.util.List;

public record StockReservationResponse(
        boolean ok,
        List<Long> insufficientProductIds
) {}
