package com.voltgrid.voltgrid.range.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class GeminiRangeAiClient implements RangeAiClient {

    private static final long COOLDOWN_MS = 60_000;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    // After a failure we skip the AI for a while instead of making every request wait for the timeout
    private final AtomicLong retryAfterMillis = new AtomicLong(0);

    public GeminiRangeAiClient(@Value("${app.gemini.base-url}") String baseUrl,
                               @Value("${app.gemini.api-key:}") String apiKey,
                               @Value("${app.gemini.model:}") String model,
                               @Value("${app.gemini.timeout-ms:4000}") long timeoutMs) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.apiKey = apiKey.trim();
        this.model = model.trim();
    }


    @Override
    public Optional<Double> suggestFactor(RangeAiContext context) {
        if (apiKey.isEmpty() || model.isEmpty()) {
            return Optional.empty();   // AI not configured: formula only
        }
        if (System.currentTimeMillis() < retryAfterMillis.get()) {
            return Optional.empty();   // cooling down after a recent failure
        }

        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", RangePrompt.build(context))))),
                    "generationConfig", Map.of("temperature", 0, "responseMimeType", "application/json")
            );

            String response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)      // header, never in the URL
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            Optional<Double> factor = GeminiResponseParser.parseFactor(response);
            if (factor.isEmpty()) {
                log.warn("Gemini returned an unusable response, using the formula range");
            }
            return factor;

        } catch (Exception ex) {
            retryAfterMillis.set(System.currentTimeMillis() + COOLDOWN_MS);
            // Log only the exception type: never the key, and no request details
            log.warn("Gemini call failed ({}), using the formula range for the next {} seconds",
                    ex.getClass().getSimpleName(), COOLDOWN_MS / 1000);
            return Optional.empty();
        }
    }


}