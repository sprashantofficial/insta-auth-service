package com.example.insta_auth_service.service;

import com.example.insta_auth_service.dto.AuthDtos.*;
import com.example.insta_auth_service.entity.RefreshToken;
import com.example.insta_auth_service.entity.User;
import com.example.insta_auth_service.exception.*;
import com.example.insta_auth_service.repository.RefreshTokenRepository;
import com.example.insta_auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    private final RefreshTokenRepository refreshTokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final OtpService otpService;

    private final EmailService emailService;

    @Value("${app.security.max-login-attempts:5}")
    private int maxLoginAttempts;

    @Value("${app.security.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email is already registered");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username is already taken");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .authProvider(User.AuthProvider.LOCAL)
                .emailVerified(false)
                .build();

        userRepository.save(user);

        String otp = otpService.generateAndStoreOtp(user.getEmail(), OtpService.OtpType.EMAIL_VERIFICATION);
        emailService.sendOtpEmail(user.getEmail(), user.getUsername(), otp, "Please use the code below to verify your email address.");

        log.info("User registered: {}", user.getEmail());

        return MessageResponse.builder()
                .success(true)
                .message("Registration successful. Please check your email for a verification code.")
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (storedToken.isRevoked() || storedToken.isExpired()) {
            refreshTokenRepository.revokeAllUserTokens(storedToken.getUser());
            throw new InvalidTokenException("Refresh token expired or revoked. Please log in again");
        }

        User user = storedToken.getUser();
        storedToken.setRevoked(true);

        refreshTokenRepository.save(storedToken);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

        if (!user.isAccountNonLocked()) {
            throw new AccountLockedException("Account is locked. Please try again later.");
        }

        if (!user.isEnabled()) {
            throw new EmailNotVerifiedException("Please verify your email before logging in.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleFailedLogin(user);
            throw new AuthenticationException("Invalid credentials");
        }

        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setLockExpiresAt(null);
        user.setLastLoginAt(LocalDateTime.now());

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public MessageResponse logout(String accessToken, String email) {
        jwtService.blacklistToken(accessToken);
        userRepository.findByEmail(email).ifPresent(
                user -> refreshTokenRepository.revokeAllUserTokens(user)
        );

        return MessageResponse.builder()
                .success(true)
                .message("Logged out successfully!!")
                .build();
    }

    @Transactional
    public MessageResponse verifyEmail(VerifyEmailRequest request) {
        boolean valid = otpService.validateOtp(request.getEmail(), request.getOtp(), OtpService.OtpType.EMAIL_VERIFICATION);
        if (!valid) {
            throw new InvalidOtpException("Invalid or expired OTP");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        user.setEmailVerified(true);
        userRepository.save(user);

        emailService.sendWelcomeEmail(user.getEmail(), user.getUsername());

        return MessageResponse.builder()
                .success(true)
                .message("Email verified successfully!!")
                .build();
    }

    @Transactional
    public MessageResponse resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (user.isEmailVerified()) {
            throw new ConflictException("Email is already verified");
        }

        String otp = otpService.generateAndStoreOtp(user.getEmail(), OtpService.OtpType.EMAIL_VERIFICATION);
        emailService.sendOtpEmail(user.getEmail(), user.getUsername(), otp,
                "Please use the code below to verify your email address");

        return MessageResponse.builder()
                .success(true)
                .message("OTP sent to your email")
                .build();
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String otp = otpService.generateAndStoreOtp(user.getEmail(), OtpService.OtpType.PASSWORD_RESET);
            emailService.sendOtpEmail(user.getEmail(), user.getUsername(), otp,
                    "Use this code to reset the password.");
        });

        return MessageResponse.builder()
                .success(true)
                .message("If an account with that email exists, a reset code has been sent.")
                .build();
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        boolean valid = otpService.validateOtp(request.getEmail(), request.getOtp(), OtpService.OtpType.PASSWORD_RESET);
        if (!valid) {
            throw new InvalidOtpException("Invalid or expired OTP");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllUserTokens(user);

        return MessageResponse.builder()
                .success(true)
                .message("Password reset successful")
                .build();
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);

        String refreshTokenValue = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .user(user)
                .expires_at(LocalDateTime.now().plusSeconds(refreshTokenExpiryMs / 1000))
                .build();

        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiryMs() / 1000)
                .user(mapToUserResponse(user))
                .build();
    }

    public UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .profilePicture(user.getProfilePicture())
                .role(user.getRole().name())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= maxLoginAttempts) {
            user.setAccountLocked(true);
            user.setLockExpiresAt(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
            log.warn("Account locked after {} failed attempts: {}", attempts, user.getEmail());
        }

        userRepository.save(user);
    }

}
