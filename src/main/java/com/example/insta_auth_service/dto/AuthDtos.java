package com.example.insta_auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

public class AuthDtos {

    @Data
    public static class RegisterRequest {

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be 3-30 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._]+$", message = "Username can only contain letters, numbers, dots, and underscores")
        private String username;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
                message = "Password must contain uppercase, lowercase, digit, and special character")
        private String password;

        @NotBlank(message = "Full name is required")
        @Size(max = 100)
        private String fullName;

    }

    @Data
    public static class LoginRequest {

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;

    }

    @Data
    public static class RefreshTokenRequest {

        @NotBlank(message = "Refresh token is required")
        private String refreshToken;

    }

    @Data
    public static class VerifyEmailRequest {

        @NotBlank(message = "Email is required")
        @Email
        private String email;

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be 6 digits")
        private String otp;

    }

    @Data
    public static class ResendOtpRequest {

        @NotBlank(message = "Email is required")
        @Email
        private String email;

    }

    @Data
    public static class ForgotPasswordRequest {

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

    }

    @Data
    public static class ResetPasswordRequest {

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be 6 digits")
        private String otp;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
                message = "Password must contain uppercase, lowercase, digit, and special character")
        private String newPassword;

    }

    @Data @Builder
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private long expiresIn;
        private UserResponse user;
    }

    @Data @Builder
    public static class UserResponse {
        private UUID id;
        private String username;
        private String email;
        private String fullName;
        private String profilePicture;
        private String role;
        private boolean emailVerified;
        private LocalDateTime createdAt;
    }

    @Data @Builder
    public static class MessageResponse {
        private boolean success;
        private String message;
    }

}
