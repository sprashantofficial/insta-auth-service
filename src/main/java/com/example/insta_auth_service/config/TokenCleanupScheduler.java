package com.example.insta_auth_service.config;

import com.example.insta_auth_service.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanExpiredTokens() {
        log.info("Running scheduled cleanup of expired/revoked refresh tokens...");
        refreshTokenRepository.deleteExpiredAndRevokedTokens(LocalDateTime.now());
        log.info("Token cleanup complete.");
    }

}
