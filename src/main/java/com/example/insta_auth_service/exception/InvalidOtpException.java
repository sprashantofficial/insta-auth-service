package com.example.insta_auth_service.exception;

public class InvalidOtpException extends RuntimeException {
    public InvalidOtpException(String message) {
        super(message);
    }
}
