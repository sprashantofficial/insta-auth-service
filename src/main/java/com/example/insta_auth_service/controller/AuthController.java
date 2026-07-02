package com.example.insta_auth_service.controller;

import com.example.insta_auth_service.dto.ApiResponse;
import com.example.insta_auth_service.dto.AuthDtos.*;
import com.example.insta_auth_service.entity.User;
import com.example.insta_auth_service.exception.ResourceNotFoundException;
import com.example.insta_auth_service.repository.UserRepository;
import com.example.insta_auth_service.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token management")
public class AuthController {

    private final AuthService authService;

    private final UserRepository userRepository;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<MessageResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Registration successful", authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refreshToken(request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate tokens", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<MessageResponse>> logout(
            @RequestHeader("Authorization") String authHeader,
            @AuthenticationPrincipal UserDetails currentUser
            ) {
        String token = authHeader.substring(7);
        return ResponseEntity.ok(ApiResponse.ok(authService.logout(token, currentUser.getUsername())));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email address with OTP")
    public ResponseEntity<ApiResponse<MessageResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.verifyEmail(request)));
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend email verification OTP")
    public ResponseEntity<ApiResponse<MessageResponse>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.resendOtp(request)));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Resend a password reset OTP")
    public ResponseEntity<ApiResponse<MessageResponse>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.forgotPassword(request)));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using OTP")
    public ResponseEntity<ApiResponse<MessageResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.resetPassword(request)));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal UserDetails currentUser) {
        User user = userRepository.findByEmail(currentUser.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return ResponseEntity.ok(ApiResponse.ok(authService.mapToUserResponse(user)));
    }

}
