package com.voltgrid.voltgrid.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltgrid.voltgrid.payment.dto.PaymentCaptureResponse;
import com.voltgrid.voltgrid.payment.dto.PaymentIntentResponse;
import com.voltgrid.voltgrid.payment.dto.PaymentPreAuthRequest;
import com.voltgrid.voltgrid.payment.dto.WebhookVerificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Component
public class StripePaymentGateway implements PaymentGateway {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String apiKey;
    private final String webhookSecret;
    private final String baseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public StripePaymentGateway(
            @Value("${app.stripe.api-key:${STRIPE_API_KEY:}}") String apiKey,
            @Value("${app.stripe.webhook-secret:${STRIPE_WEBHOOK_SECRET:}}") String webhookSecret,
            @Value("${app.stripe.base-url:https://api.stripe.com/v1}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.webhookSecret = webhookSecret != null ? webhookSecret.trim() : "";
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "https://api.stripe.com/v1";
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    @Override
    public String getProviderName() {
        return "STRIPE";
    }

    @Override
    public PaymentIntentResponse preAuthorize(PaymentPreAuthRequest request) {
        // Amount in minor currency unit (e.g. paise / cents)
        long amountInSmallestUnit = request.amount().multiply(BigDecimal.valueOf(100)).longValue();
        String currency = request.currency().toLowerCase();

        if (apiKey.isEmpty()) {
            // Test mode simulation when live API key is not configured
            String testId = "pi_test_" + UUID.randomUUID().toString().replace("-", "");
            String clientSecret = testId + "_secret_test";
            log.info("Stripe API key not configured, simulated pre-authorization: id={}, amount={}", testId, request.amount());
            return new PaymentIntentResponse(testId, clientSecret, request.amount(), request.currency(), "requires_capture");
        }

        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("amount", String.valueOf(amountInSmallestUnit));
            formData.add("currency", currency);
            formData.add("capture_method", "manual"); // Pre-authorization
            if (request.bookingId() != null) {
                formData.add("metadata[bookingId]", request.bookingId().toString());
            }
            if (request.customerEmail() != null) {
                formData.add("receipt_email", request.customerEmail());
            }

            RestClient.RequestBodySpec requestSpec = restClient.post()
                    .uri("/payment_intents")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData);

            if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
                requestSpec.header("Idempotency-Key", request.idempotencyKey().trim());
            }

            String response = requestSpec.retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(response);

            String id = root.path("id").asText();
            String clientSecret = root.path("client_secret").asText();
            String status = root.path("status").asText();

            return new PaymentIntentResponse(id, clientSecret, request.amount(), request.currency(), status);
        } catch (Exception e) {
            log.error("Failed to pre-authorize with Stripe: {}", e.getMessage());
            throw new RuntimeException("Stripe pre-authorization failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentCaptureResponse capture(String paymentIntentId, BigDecimal amount) {
        long amountInSmallestUnit = amount.multiply(BigDecimal.valueOf(100)).longValue();

        if (apiKey.isEmpty()) {
            log.info("Stripe API key not configured, simulated capture for intent: {}, amount={}", paymentIntentId, amount);
            return new PaymentCaptureResponse(paymentIntentId, amount, "succeeded", true);
        }

        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("amount_to_capture", String.valueOf(amountInSmallestUnit));

            String response = restClient.post()
                    .uri("/payment_intents/{id}/capture", paymentIntentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String status = root.path("status").asText();
            return new PaymentCaptureResponse(paymentIntentId, amount, status, "succeeded".equalsIgnoreCase(status));
        } catch (Exception e) {
            log.error("Failed to capture Stripe payment intent {}: {}", paymentIntentId, e.getMessage());
            return new PaymentCaptureResponse(paymentIntentId, amount, "failed", false);
        }
    }

    @Override
    public WebhookVerificationResult verifyWebhook(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return WebhookVerificationResult.failure("Missing Stripe-Signature header");
        }

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Stripe webhook secret is not configured");
            return WebhookVerificationResult.failure("Stripe webhook secret is not configured");
        }

        boolean verified = verifyStripeSignature(payload, signatureHeader, webhookSecret);
        if (!verified) {
            return WebhookVerificationResult.failure("Signature mismatch");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.path("type").asText();
            JsonNode dataObject = root.path("data").path("object");

            String paymentIntentId = dataObject.path("id").asText(null);
            Long bookingId = null;
            if (dataObject.has("metadata") && dataObject.path("metadata").has("bookingId")) {
                bookingId = dataObject.path("metadata").path("bookingId").asLong();
            }

            BigDecimal amount = null;
            if (dataObject.has("amount")) {
                amount = BigDecimal.valueOf(dataObject.path("amount").asLong())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }

            return WebhookVerificationResult.success(eventType, paymentIntentId, bookingId, amount);
        } catch (Exception e) {
            log.error("Error parsing Stripe webhook JSON payload: {}", e.getMessage());
            return WebhookVerificationResult.failure("Invalid JSON payload: " + e.getMessage());
        }
    }

    public static boolean verifyStripeSignature(String payload, String signatureHeader, String secret) {
        try {
            Long timestamp = null;
            String signatureV1 = null;

            String[] pairs = signatureHeader.split(",");
            for (String pair : pairs) {
                String[] kv = pair.trim().split("=", 2);
                if (kv.length == 2) {
                    if ("t".equals(kv[0])) {
                        timestamp = Long.parseLong(kv[1]);
                    } else if ("v1".equals(kv[0])) {
                        signatureV1 = kv[1];
                    }
                }
            }

            if (timestamp == null || signatureV1 == null) {
                return false;
            }

            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = HexFormat.of().formatHex(hash);

            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signatureV1.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.warn("Stripe signature verification exception: {}", e.getMessage());
            return false;
        }
    }

    public static String generateSignatureHeader(String payload, String secret, long timestamp) {
        try {
            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String sig = HexFormat.of().formatHex(hash);
            return "t=" + timestamp + ",v1=" + sig;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test signature header: " + e.getMessage(), e);
        }
    }
}
