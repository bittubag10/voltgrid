package com.voltgrid.voltgrid.payment.service;

import com.voltgrid.voltgrid.booking.dto.BookingResponse;
import com.voltgrid.voltgrid.booking.entity.Booking;
import com.voltgrid.voltgrid.booking.entity.BookingStatus;
import com.voltgrid.voltgrid.booking.repository.BookingRepository;
import com.voltgrid.voltgrid.exception.BadRequestException;
import com.voltgrid.voltgrid.exception.ConflictException;
import com.voltgrid.voltgrid.exception.ResourceNotFoundException;
import com.voltgrid.voltgrid.payment.dto.*;
import com.voltgrid.voltgrid.payment.entity.Payment;
import com.voltgrid.voltgrid.payment.entity.PaymentStatus;
import com.voltgrid.voltgrid.payment.gateway.PaymentGateway;
import com.voltgrid.voltgrid.payment.repository.PaymentRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    @Getter
    @Value("${app.payment.mock-enabled:false}")
    private boolean mockPaymentEnabled;

    @Value("${app.payment.currency:INR}")
    private String currency;

    @Transactional
    public PaymentIntentResponse preAuthorize(Long driverId, Long bookingId, String idempotencyKey) {
        Instant now = clock.instant();
        Booking booking = bookingRepository.findByIdAndDriverId(bookingId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("Booking is not pending payment (status: " + booking.getStatus() + ")");
        }

        if (booking.getExpiresAt() != null && !booking.getExpiresAt().isAfter(now)) {
            throw new ConflictException("Booking payment hold has expired");
        }

        BigDecimal amount = (booking.getTotalAmount() != null && booking.getTotalAmount().compareTo(BigDecimal.ZERO) > 0)
                ? booking.getTotalAmount()
                : (booking.getBaseAmount() != null ? booking.getBaseAmount() : new BigDecimal("100.00"));

        PaymentPreAuthRequest preAuthRequest = new PaymentPreAuthRequest(
                booking.getId(),
                amount,
                currency,
                idempotencyKey,
                booking.getDriver().getEmail(),
                Map.of("bookingId", booking.getId().toString())
        );

        PaymentIntentResponse intent = paymentGateway.preAuthorize(preAuthRequest);

        booking.setPaymentIntentId(intent.paymentIntentId());

        Payment payment = Payment.builder()
                .booking(booking)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.AUTHORIZED)
                .provider(paymentGateway.getProviderName())
                .transactionId(intent.paymentIntentId())
                .idempotencyKey(idempotencyKey)
                .build();
        paymentRepository.save(payment);

        return intent;
    }

    @Transactional
    public WebhookVerificationResult handleStripeWebhook(String payload, String signatureHeader) {
        WebhookVerificationResult result = paymentGateway.verifyWebhook(payload, signatureHeader);
        if (!result.valid()) {
            log.warn("Invalid Stripe webhook signature: {}", result.errorMessage());
            throw new BadRequestException("Stripe webhook verification failed: " + result.errorMessage());
        }

        String eventType = result.eventType();
        log.info("Received valid Stripe webhook event: {}", eventType);

        boolean isPaymentSuccessEvent = "payment_intent.succeeded".equalsIgnoreCase(eventType)
                || "payment_intent.amount_capturable_updated".equalsIgnoreCase(eventType)
                || "charge.succeeded".equalsIgnoreCase(eventType);

        if (isPaymentSuccessEvent) {
            Booking booking = null;
            if (result.bookingId() != null) {
                booking = bookingRepository.findById(result.bookingId()).orElse(null);
            }
            if (booking == null && result.paymentIntentId() != null) {
                booking = bookingRepository.findByPaymentIntentId(result.paymentIntentId()).orElse(null);
            }

            if (booking != null) {
                final Booking targetBooking = booking;
                if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                    booking.setStatus(BookingStatus.CONFIRMED);
                    booking.setExpiresAt(null);
                    if (result.paymentIntentId() != null) {
                        booking.setPaymentIntentId(result.paymentIntentId());
                    }
                    bookingRepository.save(booking);

                    Payment payment = paymentRepository.findByTransactionId(result.paymentIntentId())
                            .orElseGet(() -> Payment.builder()
                                    .booking(targetBooking)
                                    .amount(result.amount() != null ? result.amount() : targetBooking.getTotalAmount())
                                    .currency(currency)
                                    .provider("STRIPE")
                                    .transactionId(result.paymentIntentId())
                                    .build());
                    payment.setStatus(PaymentStatus.AUTHORIZED);
                    paymentRepository.save(payment);

                    log.info("Booking #{} moved to CONFIRMED via Stripe webhook", booking.getId());
                }
            } else {
                log.warn("Stripe webhook received for unknown booking: bookingId={}, paymentIntentId={}",
                        result.bookingId(), result.paymentIntentId());
            }
        }

        return result;
    }

    @Transactional
    public BookingResponse mockConfirmPayment(Long driverId, Long bookingId) {
        if (!mockPaymentEnabled) {
            throw new ConflictException("Mock payment confirmation is disabled in this environment");
        }

        Instant now = clock.instant();
        Booking booking = (driverId != null)
                ? bookingRepository.findByIdAndDriverId(bookingId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"))
                : bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return BookingResponse.from(booking, now);
        }

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ConflictException("Cannot confirm booking with status " + booking.getStatus());
        }

        if (booking.getExpiresAt() != null && !booking.getExpiresAt().isAfter(now)) {
            throw new ConflictException("Payment hold for this booking has expired");
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setExpiresAt(null);

        String mockTxId = "mock_pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        booking.setPaymentIntentId(mockTxId);

        BigDecimal amount = (booking.getTotalAmount() != null && booking.getTotalAmount().compareTo(BigDecimal.ZERO) > 0)
                ? booking.getTotalAmount()
                : (booking.getBaseAmount() != null ? booking.getBaseAmount() : new BigDecimal("100.00"));

        Payment payment = Payment.builder()
                .booking(booking)
                .amount(amount)
                .currency(currency)
                .status(PaymentStatus.AUTHORIZED)
                .provider("MOCK")
                .transactionId(mockTxId)
                .build();
        paymentRepository.save(payment);

        log.info("Booking #{} confirmed via mock payment endpoint", booking.getId());
        return BookingResponse.from(bookingRepository.save(booking), now);
    }

    @Transactional
    public void settleFinalAmount(Booking booking) {
        if (booking.getPaymentIntentId() != null) {
            BigDecimal finalAmount = booking.getTotalAmount() != null ? booking.getTotalAmount() : booking.getBaseAmount();
            try {
                paymentGateway.capture(booking.getPaymentIntentId(), finalAmount);
                paymentRepository.findByTransactionId(booking.getPaymentIntentId())
                        .ifPresent(p -> {
                            p.setAmount(finalAmount);
                            p.setStatus(PaymentStatus.COMPLETED);
                        });
            } catch (Exception e) {
                log.warn("Failed to settle final amount with gateway for booking #{}: {}", booking.getId(), e.getMessage());
            }
        }
    }
}
