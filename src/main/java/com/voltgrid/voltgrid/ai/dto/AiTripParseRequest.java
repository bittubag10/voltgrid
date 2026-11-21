package com.voltgrid.voltgrid.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiTripParseRequest(
        @NotBlank(message = "Prompt cannot be blank")
        @Size(max = 500, message = "Prompt exceeds maximum length of 500 characters")
        String prompt,

        Long vehicleId,

        Boolean autoPlan
) {
    public boolean shouldAutoPlan() {
        return autoPlan == null || Boolean.TRUE.equals(autoPlan);
    }
}
