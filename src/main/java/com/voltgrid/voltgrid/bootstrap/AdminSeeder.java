package com.voltgrid.voltgrid.bootstrap;

import com.voltgrid.voltgrid.enums.Role;
import com.voltgrid.voltgrid.entity.User;
import com.voltgrid.voltgrid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first ADMIN account on startup from environment variables.
 * Public registration can never create an ADMIN, so this is the only way in.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.name:Platform Admin}")
    private String adminName;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("Admin seed skipped: ADMIN_EMAIL / ADMIN_PASSWORD not set");
            return;
        }
        if (adminPassword.length() < MIN_PASSWORD_LENGTH) {
            log.warn("Admin seed skipped: ADMIN_PASSWORD must be at least {} characters", MIN_PASSWORD_LENGTH);
            return;
        }

        String email = adminEmail.trim().toLowerCase();
        userRepository.findByEmail(email).ifPresentOrElse(
                user -> {
                    user.setPasswordHash(passwordEncoder.encode(adminPassword));
                    user.setRole(Role.ADMIN);
                    user.setName(adminName);
                    userRepository.save(user);
                    log.info("Admin account updated and synced for {}", email);
                },
                () -> {
                    userRepository.save(User.builder()
                            .name(adminName)
                            .email(email)
                            .passwordHash(passwordEncoder.encode(adminPassword))
                            .role(Role.ADMIN)
                            .build());
                    log.info("Admin account created for {}", email);
                }
        );
    }
}