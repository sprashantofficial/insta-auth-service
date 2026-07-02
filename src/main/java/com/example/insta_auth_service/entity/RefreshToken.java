package com.example.insta_auth_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, name = "expires_at")
    private LocalDateTime expires_at;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(updatable = false, name = "created_at")
    @Builder.Default
    private LocalDateTime created_at = LocalDateTime.now();

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expires_at);
    }

}
