package com.voltgrid.voltgrid.payment.controller;

import com.voltgrid.voltgrid.booking.dto.BookingResponse;
import com.voltgrid.voltgrid.payment.dto.MockPaymentConfirmRequest;
import com.voltgrid.voltgrid.payment.dto.PaymentIntentResponse;
import com.voltgrid.voltgrid.payment.dto.WebhookVerificationResult;
import com.voltgrid.voltgrid.payment.service.PaymentService;
import com.voltgrid.voltgrid.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/webhook")
    public ResponseEntity<String> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signatureHeader
    ) {
        WebhookVerificationResult result = paymentService.handleStripeWebhook(payload, signatureHeader);
        return ResponseEntity.ok("Webhook processed: " + result.eventType());
    }

    @PostMapping("/{bookingId}/pre-authorize")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<PaymentIntentResponse> preAuthorize(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long bookingId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return ResponseEntity.ok(paymentService.preAuthorize(user.id(), bookingId, idempotencyKey));
    }

    @PostMapping("/mock-confirm")
    public ResponseEntity<BookingResponse> mockConfirm(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody MockPaymentConfirmRequest request
    ) {
        Long driverId = user != null ? user.id() : null;
        return ResponseEntity.ok(paymentService.mockConfirmPayment(driverId, request.bookingId()));
    }

    @PostMapping("/mock-confirm/{bookingId}")
    public ResponseEntity<BookingResponse> mockConfirmByPath(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long bookingId
    ) {
        Long driverId = user != null ? user.id() : null;
        return ResponseEntity.ok(paymentService.mockConfirmPayment(driverId, bookingId));
    }
}
