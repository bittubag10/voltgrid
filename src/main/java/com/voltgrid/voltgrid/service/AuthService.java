package com.voltgrid.voltgrid.service;


import com.voltgrid.voltgrid.dto.LoginRequest;
import com.voltgrid.voltgrid.dto.LoginResponse;
import com.voltgrid.voltgrid.dto.RegisterRequest;
import com.voltgrid.voltgrid.dto.UserResponse;
import com.voltgrid.voltgrid.entity.Role;
import com.voltgrid.voltgrid.entity.User;
import com.voltgrid.voltgrid.exception.BadRequestException;
import com.voltgrid.voltgrid.exception.ConflictException;
import com.voltgrid.voltgrid.exception.UnauthorizedException;
import com.voltgrid.voltgrid.repository.UserRepository;
import com.voltgrid.voltgrid.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest request) {

        // Public registration can never create an ADMIN
        if (request.role() == Role.ADMIN) {
            throw new BadRequestException("Admin registration is not allowed");
        }

        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .filter(User::isEnabled)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        return new LoginResponse(
                jwtService.generateToken(user),
                "Bearer",
                jwtService.getExpirationSeconds(),
                UserResponse.from(user)
        );
    }
}