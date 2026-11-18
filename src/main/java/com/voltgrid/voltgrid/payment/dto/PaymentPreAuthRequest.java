package com.voltgrid.voltgrid.payment.dto;

import java.math.BigDecimal;
import java.util.Map;

public record PaymentPreAuthRequest(
        Long bookingId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        String customerEmail,
        Map<String, String> metadata
) {
}
