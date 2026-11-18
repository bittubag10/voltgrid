package com.voltgrid.voltgrid.controller;


import com.voltgrid.voltgrid.dto.UserResponse;
import com.voltgrid.voltgrid.security.AuthenticatedUser;
import com.voltgrid.voltgrid.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return userService.getById(currentUser.id());
    }
}