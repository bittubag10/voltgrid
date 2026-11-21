package com.voltgrid.voltgrid.ai.controller;

import com.voltgrid.voltgrid.ai.dto.AiTripParseRequest;
import com.voltgrid.voltgrid.ai.dto.AiTripParseResponse;
import com.voltgrid.voltgrid.ai.service.AiTripAssistantService;
import com.voltgrid.voltgrid.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
@Tag(name = "AI Co-Pilot", description = "Grounded Natural-Language Trip Assistant and Parameter Extractor")
public class AiTripAssistantController {

    private final AiTripAssistantService aiTripAssistantService;

    @PostMapping("/parse-trip")
    @Operation(summary = "Parse free-form natural language travel prompt into grounded trip parameters and route")
    public AiTripParseResponse parseTrip(@AuthenticationPrincipal AuthenticatedUser user,
                                         @Valid @RequestBody AiTripParseRequest request) {
        return aiTripAssistantService.parseAndProcessTrip(user.id(), request);
    }
}
