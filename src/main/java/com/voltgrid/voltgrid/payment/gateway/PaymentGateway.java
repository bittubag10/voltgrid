package com.voltgrid.voltgrid.payment.gateway;

import com.voltgrid.voltgrid.payment.dto.PaymentCaptureResponse;
import com.voltgrid.voltgrid.payment.dto.PaymentIntentResponse;
import com.voltgrid.voltgrid.payment.dto.PaymentPreAuthRequest;
import com.voltgrid.voltgrid.payment.dto.WebhookVerificationResult;

import java.math.BigDecimal;

/**
 * Pluggable PaymentGateway interface.
 * Providers like Stripe, Razorpay, or Mock can implement this.
 */
public interface PaymentGateway {

    String getProviderName();

    PaymentIntentResponse preAuthorize(PaymentPreAuthRequest request);

    PaymentCaptureResponse capture(String paymentIntentId, BigDecimal amount);

    WebhookVerificationResult verifyWebhook(String payload, String signatureHeader);
}
