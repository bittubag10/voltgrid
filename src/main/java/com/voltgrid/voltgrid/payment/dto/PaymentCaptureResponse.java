package com.voltgrid.voltgrid.payment.dto;

import java.math.BigDecimal;

public record PaymentCaptureResponse(
        String paymentIntentId,
        BigDecimal amount,
        String status,
        boolean success
) {
}
