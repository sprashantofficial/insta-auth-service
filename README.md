# Instagram Auth Service

A production-grade authentication backend for an Instagram-style application, built with Spring Boot 4 and Spring Security 7. Implements registration with email verification, JWT-based login with refresh-token rotation, account lockout, Redis-backed token blacklisting, OTP flows, password reset, and per-IP rate limiting with Bucket4j.

This repository accompanies the **"Building Instagram's Authentication Backend"** YouTube series on [@PrashantSharmaOfficial](https://www.youtube.com/@PrashantSharmaOfficial).

---

## Features

- **Registration with email verification** — OTP sent via Spring Mail, login blocked until verified
- **JWT access + refresh tokens** — 15-minute access tokens, 7-day refresh tokens stored in Postgres
- **Refresh token rotation** — every refresh invalidates the old token and issues a new pair; replaying a revoked token revokes all of that user's tokens
- **Token blacklist** — logout pushes the access token into a Redis TTL-bound blacklist
- **Account lockout** — 5 failed login attempts locks the account for 30 minutes (persisted in Postgres)
- **Rate limiting** — Bucket4j, 10 requests/minute per IP on `/login` and `/forgot-password`
- **Password reset** — OTP-based reset flow with refresh-token revocation on success
- **OpenAPI/Swagger UI** — interactive API documentation out of the box
- **Dockerized stack** — Postgres 16, Redis 7, and MailHog (SMTP catcher) via `docker-compose up`

---

## Tech Stack

| Layer             | Choice                               |
| ----------------- | ------------------------------------ |
| Language          | Java 21                              |
| Framework         | Spring Boot 4.0.5, Spring Security 7 |
| Database          | PostgreSQL 16                        |
| Cache / blacklist | Redis 7                              |
| JWT               | jjwt 0.12.5                          |
| Rate limiting     | Bucket4j 8.10.1                      |
| Schema migrations | Flyway                               |
| Email (dev)       | MailHog                              |
| API docs          | springdoc-openapi 2.5.0              |

---

## Quick Start

### Prerequisites

- Java 21
- Maven (or use the bundled `./mvnw`)
- Docker Desktop

### Run with Docker Compose (recommended)

```
docker-compose up --build
```

This starts four services:

| Service             | Port                           | Notes                                          |
| ------------------- | ------------------------------ | ---------------------------------------------- |
| `app` (Spring Boot) | `8080`                         | API                                            |
| `postgres`          | `5432`                         |                                                |
| `redis`             | `6379`                         |                                                |
| `mailhog`           | `1025` (SMTP), `8025` (web UI) | open `http://localhost:8025` to see OTP emails |

Once everything is up:

- **API base URL** → `http://localhost:8080`
- **Swagger UI** → `http://localhost:8080/swagger-ui.html`
- **OpenAPI spec** → `http://localhost:8080/api-docs`
- **MailHog inbox** → `http://localhost:8025` (read the OTP from here in dev)
- **Health check** → `http://localhost:8080/actuator/health`

### Run locally (Postgres + Redis in Docker, app from IDE)

Start only the infra:

```
docker-compose up postgres redis mailhog
```

Then run `InstaAuthServiceApplication` from your IDE, or:

```
./mvnw spring-boot:run
```

The Docker stack publishes Postgres on `5432`, which matches `application.yml`'s default, so no port override is needed.

---

## Configuration

All settings live in `src/main/resources/application.yml` and can be overridden via environment variables.

| Env var          | Default                 | Purpose                          |
| ---------------- | ----------------------- | -------------------------------- |
| `DB_HOST`        | `localhost`             | Postgres host                    |
| `DB_PORT`        | `5432`                  | Postgres port                    |
| `DB_USERNAME`    | `postgres`              |                                  |
| `DB_PASSWORD`    | `postgres`              |                                  |
| `REDIS_HOST`     | `localhost`             |                                  |
| `REDIS_PORT`     | `6379`                  |                                  |
| `REDIS_PASSWORD` | *(empty)*               |                                  |
| `MAIL_HOST`      | `localhost`             | SMTP host (MailHog in dev)       |
| `MAIL_PORT`      | `1025`                  | SMTP port                        |
| `CORS_ORIGINS`   | `http://localhost:3000` | comma-separated allowed origins  |

> **Note:** for `DB_HOST` / `DB_PORT` to take effect, `application.yml`'s datasource URL must reference them — see "Making DB host/port overridable" below. `DB_USERNAME`, `DB_PASSWORD`, `REDIS_*`, and `MAIL_*` are already wired.

Application-level config (`app.*` in `application.yml`):

| Key                                     | Default     | Meaning                        |
| --------------------------------------- | ----------- | ------------------------------ |
| `app.jwt.access-token-expiry-ms`        | `900000`    | 15 minutes                     |
| `app.jwt.refresh-token-expiry-ms`       | `604800000` | 7 days                         |
| `app.otp.expiry-minutes`                | `10`        | OTP lifetime                   |
| `app.otp.length`                        | `6`         | OTP digits                     |
| `app.security.max-login-attempts`       | `5`         | Failed attempts before lockout |
| `app.security.lockout-duration-minutes` | `30`        | Lockout duration               |
| `app.rate-limit.login-capacity`         | `10`        | Bucket size per IP             |
| `app.rate-limit.login-refill-tokens`    | `10`        | Tokens refilled per window     |
| `app.rate-limit.login-refill-minutes`   | `1`         | Refill window                  |

> ⚠️ The JWT secret in `application.yml` is for development only. **Rotate it for any non-local deployment** via `JWT_SECRET`.

---

## API Reference

All endpoints are under `/api/auth`. Every response is wrapped in:

```
{
  "success": true,
  "message": "...",
  "data": {  },
  "errorCode": null,
  "timestamp": "2026-05-23T16:51:00.000"
}
```

### Public endpoints

| Method | Path                        | Purpose                                                      |
| ------ | --------------------------- | ------------------------------------------------------------ |
| `POST` | `/api/auth/register`        | Create a user; sends verification OTP                        |
| `POST` | `/api/auth/verify-email`    | Confirm email with OTP                                       |
| `POST` | `/api/auth/resend-otp`      | Resend the verification OTP                                  |
| `POST` | `/api/auth/login`           | Authenticate; returns access + refresh tokens (rate-limited) |
| `POST` | `/api/auth/refresh`         | Exchange a refresh token for a new pair                      |
| `POST` | `/api/auth/forgot-password` | Request a password-reset OTP (rate-limited)                  |
| `POST` | `/api/auth/reset-password`  | Reset password using OTP                                     |

### Authenticated endpoints (require `Authorization: Bearer <accessToken>`)

| Method | Path               | Purpose                                            |
| ------ | ------------------ | -------------------------------------------------- |
| `POST` | `/api/auth/logout` | Blacklist current token, revoke all refresh tokens |
| `GET`  | `/api/auth/me`     | Get the current user                               |

### Sample request / response: register

```
POST /api/auth/register
Content-Type: application/json

{
  "username": "prashant",
  "email": "prashant@example.com",
  "password": "StrongP@ss1",
  "fullName": "Prashant Sharma"
}
```

```
{
  "success": true,
  "data": {
    "success": true,
    "message": "Registration successful. Please check your email for a verification code."
  },
  "timestamp": "2026-05-23T16:51:00.000"
}
```

The OTP is sent via SMTP to MailHog — open `http://localhost:8025`, click the latest email, and copy the 6-digit code.

### Sample request / response: login (after verification)

```
POST /api/auth/login
Content-Type: application/json

{
  "email": "prashant@example.com",
  "password": "StrongP@ss1"
}
```

```
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "8f6a2c10-...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "9e8a...",
      "username": "prashant",
      "email": "prashant@example.com",
      "fullName": "Prashant Sharma",
      "role": "USER",
      "emailVerified": true,
      "createdAt": "2026-05-23T16:51:00"
    }
  },
  "timestamp": "2026-05-23T16:52:14.123"
}
```

### Validation rules

- **Username** — 3–30 chars, `[a-zA-Z0-9._]` only
- **Password** — min 8 chars, must contain uppercase, lowercase, digit, and one of `@$!%*?&`
- **Email** — must be a valid RFC-compliant address
- **OTP** — exactly 6 digits

### Error responses

Errors come back with `success: false` and an `errorCode`:

```
{
  "success": false,
  "message": "Invalid credentials",
  "errorCode": "AUTHENTICATION_FAILED",
  "timestamp": "2026-05-23T16:52:14.123"
}
```

| HTTP | `errorCode`             | Cause                                     |
| ---- | ----------------------- | ----------------------------------------- |
| 400  | `VALIDATION_ERROR`      | DTO constraint violation                  |
| 400  | `INVALID_OTP`           | OTP wrong or expired                      |
| 401  | `AUTHENTICATION_FAILED` | Wrong credentials                         |
| 401  | `INVALID_TOKEN`         | Refresh token revoked / expired / unknown |
| 403  | `EMAIL_NOT_VERIFIED`    | Login attempted before verification       |
| 403  | `ACCESS_DENIED`         | Insufficient role                         |
| 404  | `NOT_FOUND`             | Resource not found                        |
| 409  | `CONFLICT`              | Email / username already taken            |
| 423  | `ACCOUNT_LOCKED`        | 5+ failed login attempts                  |
| 429  | `RATE_LIMIT_EXCEEDED`   | Bucket4j rejected the request             |

---

## Architecture

```
Client (Browser / Mobile / Postman)
        │  HTTPS + Bearer JWT
        ▼
Rate limiter (Bucket4j, per-IP)        ← login + forgot-password only; rejects with 429
        ▼
JwtAuthFilter                          ← validates token, checks Redis blacklist
        ▼
Spring Security 7                      ← CORS, STATELESS sessions, method security
        ▼
AuthController (/api/auth/*)
        ▼
AuthService ─── JwtService ─── OtpService
        ▼              ▼              ▼
   UserRepo     RefreshTokenRepo   RedisTemplate
        ▼              ▼              ▼
        PostgreSQL 16   ◄────►   Redis 7 (blacklist + OTP TTL)
                  ▲
            Flyway (V1__create_auth_tables.sql)
```

**Filter order:** `RateLimitFilter` runs *before* `UsernamePasswordAuthenticationFilter`; `JwtAuthFilter` runs *after* it (see `SecurityConfig`).

### Why these choices

- **Spring Security 7** — ships with Spring Boot 4; modern `SecurityFilterChain` + lambda DSL, no deprecated `WebSecurityConfigurerAdapter`
- **Refresh token rotation** — old token revoked on every refresh, and replay of a revoked token revokes all the user's tokens, so theft is detectable
- **Redis for blacklisting** — TTL matches the access-token lifetime, so blacklisted tokens auto-expire
- **Flyway** — schema lives in git; no manual SQL in prod
- **Bucket4j** — token-bucket algorithm done right; in-memory to start, back it with Redis when you run more than one instance
- **MailHog** — local SMTP catcher so dev OTP emails are visible at `http://localhost:8025`

---

## Project Layout

```
insta-auth-service/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── src/main/
│   ├── java/com/example/insta_auth_service/
│   │   ├── InstaAuthServiceApplication.java
│   │   ├── config/        # OpenApiConfig, RedisConfig, TokenCleanupScheduler
│   │   ├── controller/    # AuthController
│   │   ├── dto/           # AuthDtos, ApiResponse
│   │   ├── entity/        # User, RefreshToken
│   │   ├── exception/     # GlobalExceptionHandler + typed exceptions
│   │   ├── repository/    # UserRepository, RefreshTokenRepository
│   │   ├── security/      # SecurityConfig, JwtAuthFilter, RateLimitFilter, JwtAuthEntryPoint
│   │   └── service/       # AuthService, JwtService, OtpService, EmailService, UserDetailsServiceImpl
│   └── resources/
│       ├── application.yml
│       └── db/migration/V1__create_auth_tables.sql
└── src/test/
```

---

## Testing the flows by hand

A typical happy path:

1. `POST /api/auth/register` with a fresh email
2. Open `http://localhost:8025`, grab the 6-digit OTP from the email
3. `POST /api/auth/verify-email` with that OTP
4. `POST /api/auth/login` → save `accessToken` and `refreshToken` from the response
5. `GET /api/auth/me` with `Authorization: Bearer <accessToken>`
6. `POST /api/auth/refresh` with the refresh token — note the access token changes; the old refresh token now fails
7. `POST /api/auth/logout` — the same access token now returns `401`

To trigger lockout: `POST /api/auth/login` with the wrong password 5 times. The 6th attempt returns `423 ACCOUNT_LOCKED` and stays locked across an app restart.

To trigger the rate limit: hammer `POST /api/auth/login` more than 10 times in a minute from the same IP. The 11th gets `429 RATE_LIMIT_EXCEEDED`.

The included Postman collection covers all of this — import `instagram-auth.postman_collection.json` from the repo root.

---

## Making DB host/port overridable (optional)

By default the datasource in `application.yml` points at `localhost:5432`. To let `DB_HOST` / `DB_PORT` override it (useful for running the app inside Compose later), change the datasource URL to:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/instagram_auth
```

---

## Building a production image

```
./mvnw clean package -DskipTests
docker build -t insta-auth-service:latest .
```

Before deploying:

- Set a **strong, secret `JWT_SECRET`** (don't reuse the one in `application.yml`)
- Configure real SMTP credentials via `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD`
- Tighten `CORS_ORIGINS` to your actual frontend domain
- Front the app with HTTPS at the load balancer or ingress
- Externalize the Bucket4j buckets to Redis if you run more than one app instance

---

## License & Credits

Educational project. Code may be reused freely for learning. Series source: [@PrashantSharmaOfficial](https://www.youtube.com/@PrashantSharmaOfficial). Companion Udemy course covers OAuth2 social login, MFA, and AWS deployment beyond what's in this repo.
