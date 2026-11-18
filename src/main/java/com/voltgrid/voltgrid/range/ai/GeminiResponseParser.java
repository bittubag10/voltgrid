package com.voltgrid.voltgrid.range.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/** Extracts {"factor": number} from a Gemini generateContent response. Never throws. */
public final class GeminiResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GeminiResponseParser() {
    }

    public static Optional<Double> parseFactor(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode textNode = MAPPER.readTree(responseJson).at("/candidates/0/content/parts/0/text");
            if (!textNode.isTextual()) {
                return Optional.empty();
            }

            JsonNode factorNode = MAPPER.readTree(stripCodeFence(textNode.asText())).get("factor");
            if (factorNode == null || !factorNode.isNumber()) {
                return Optional.empty();
            }
            return Optional.of(factorNode.asDouble());

        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private static String stripCodeFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline >= 0) {
                t = t.substring(firstNewline + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }
}