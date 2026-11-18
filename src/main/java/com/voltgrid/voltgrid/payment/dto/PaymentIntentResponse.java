package com.voltgrid.voltgrid.payment.dto;

import java.math.BigDecimal;

public record PaymentIntentResponse(
        String paymentIntentId,
        String clientSecret,
        BigDecimal amount,
        String currency,
        String status
) {
}
