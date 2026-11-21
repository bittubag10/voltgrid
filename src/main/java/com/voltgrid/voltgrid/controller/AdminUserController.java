package com.voltgrid.voltgrid.controller;

import com.voltgrid.voltgrid.dto.AdminOverviewResponse;
import com.voltgrid.voltgrid.dto.PageResponse;
import com.voltgrid.voltgrid.dto.UserResponse;
import com.voltgrid.voltgrid.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    @GetMapping("/users")
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return userService.listAll(PageRequest.of(safePage, safeSize, Sort.by("id")));
    }

    @PatchMapping("/users/{id}/status")
    public UserResponse toggleUserStatus(
            @PathVariable Long id,
            @RequestParam boolean enabled) {
        return userService.toggleUserStatus(id, enabled);
    }

    @GetMapping("/overview")
    public AdminOverviewResponse getOverview() {
        return userService.getAdminOverview();
    }
}