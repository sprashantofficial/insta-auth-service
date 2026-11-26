package com.example.insta_auth_service.service;

import com.example.insta_auth_service.exception.InvalidOtpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class OtpService {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.otp.expiry-minutes:10}")
    private int otpExpiryMinutes;

    @Value("${app.otp.length:6}")
    private int otpLength;

    private static final String OTP_PREFIX = "otp:";

    private static final String OTP_ATTEMPTS_PREFIX = "otp:attempts:";

    private static final int MAX_OTP_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateAndStoreOtp(String email, OtpType type) {
        String otp = generateOtp();
        String key = buildKey(email, type);

        stringRedisTemplate.opsForValue().set(key, otp, otpExpiryMinutes, TimeUnit.MINUTES);
        log.debug("OTP generated for email: {} type: {}", email, type);

        return otp;
    }

    public boolean validateOtp(String email, String otp, OtpType type) {
        String attemptsKey = OTP_ATTEMPTS_PREFIX + email + ":" + type;
        String currentAttempts = stringRedisTemplate.opsForValue().get(attemptsKey);

        if (currentAttempts != null && Integer.parseInt(currentAttempts) >= MAX_OTP_ATTEMPTS) {
            throw new InvalidOtpException("Too many OTP attempts. Please request a new OTP.");
        }

        String key = buildKey(email, type);
        String storedOtp = stringRedisTemplate.opsForValue().get(key);

        if (storedOtp == null) {
            return false;
        }

        if (!storedOtp.equals(otp)) {
            stringRedisTemplate.opsForValue().increment(attemptsKey);
            stringRedisTemplate.expire(attemptsKey, otpExpiryMinutes, TimeUnit.MINUTES);
            return false;
        }

        stringRedisTemplate.delete(key);
        stringRedisTemplate.delete(attemptsKey);

        return true;
    }

    private String buildKey(String email, OtpType type) {
        return OTP_PREFIX + type.name().toLowerCase() + ":" + email;
    }

    private String generateOtp() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < otpLength; i++) {
            sb.append(secureRandom.nextInt(10));
        }

        return sb.toString();
    }

    public enum OtpType {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }

}
