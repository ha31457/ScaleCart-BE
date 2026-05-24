package com.hss.scalecart.service;

import com.hss.scalecart.dto.request.LoginRequest;
import com.hss.scalecart.dto.request.RegisterRequest;
import com.hss.scalecart.dto.response.AuthResponse;
import com.hss.scalecart.entity.RefreshToken;
import com.hss.scalecart.entity.User;
import com.hss.scalecart.repository.RefreshTokenRepository;
import com.hss.scalecart.repository.UserRepository;
import com.hss.scalecart.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.expiration-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshTokenExpiryMs;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null
                        ? com.hss.scalecart.enums.UserRole.valueOf(request.getRole().toUpperCase())
                        : com.hss.scalecart.enums.UserRole.CUSTOMER)
                .build();

        User saved = userRepository.save(user);
        return issueTokens(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        // Revoke all existing refresh tokens for this user on new login
        refreshTokenRepository.revokeAllByUserId(user.getId());

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        // Reuse detection — token already used means potential breach
        if (stored.isUsed()) {
            log.warn("Refresh token reuse detected for userId={} — revoking all tokens", stored.getUserId());
            refreshTokenRepository.revokeAllByUserId(stored.getUserId());
            throw new IllegalArgumentException("Refresh token already used. Please login again.");
        }

        if (stored.isRevoked()) {
            throw new IllegalArgumentException("Refresh token has been revoked. Please login again.");
        }

        if (Instant.now().isAfter(stored.getExpiresAt())) {
            throw new IllegalArgumentException("Refresh token has expired. Please login again.");
        }

        // Mark old token as used
        stored.setUsed(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByToken(rawRefreshToken)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        String rawRefreshToken = jwtService.generateRefreshToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .token(rawRefreshToken)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpiryMs))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenExpiryMs / 1000)
                .role(user.getRole().name())
                .email(user.getEmail())
                .build();
    }
}