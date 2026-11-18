package com.voltgrid.voltgrid.range.controller;

import com.voltgrid.voltgrid.range.dto.RangeRequest;
import com.voltgrid.voltgrid.range.dto.RangeResponse;
import com.voltgrid.voltgrid.range.service.RangeService;
import com.voltgrid.voltgrid.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/range")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class RangeController {

    private final RangeService rangeService;

    @PostMapping("/predict")
    public RangeResponse predict(@AuthenticationPrincipal AuthenticatedUser user,
                                 @Valid @RequestBody RangeRequest request) {
        return rangeService.predict(user.id(), request);
    }
}