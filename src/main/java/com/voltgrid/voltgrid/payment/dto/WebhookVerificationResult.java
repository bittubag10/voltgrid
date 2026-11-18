package com.voltgrid.voltgrid.payment.dto;

import java.math.BigDecimal;

public record WebhookVerificationResult(
        boolean valid,
        String eventType,
        String paymentIntentId,
        Long bookingId,
        BigDecimal amount,
        String errorMessage
) {
    public static WebhookVerificationResult success(String eventType, String paymentIntentId, Long bookingId, BigDecimal amount) {
        return new WebhookVerificationResult(true, eventType, paymentIntentId, bookingId, amount, null);
    }

    public static WebhookVerificationResult failure(String errorMessage) {
        return new WebhookVerificationResult(false, null, null, null, null, errorMessage);
    }
}
