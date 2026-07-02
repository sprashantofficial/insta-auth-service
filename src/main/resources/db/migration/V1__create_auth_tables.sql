CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username         VARCHAR(30)  NOT NULL UNIQUE,
    email            VARCHAR(255) NOT NULL UNIQUE,
    password         VARCHAR(255),
    full_name        VARCHAR(100),
    profile_picture  TEXT,
    role             VARCHAR(20)  NOT NULL DEFAULT 'USER',
    auth_provider    VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',
    provider_id      VARCHAR(255),
    email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    account_locked   BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts INT     NOT NULL DEFAULT 0,
    lock_expires_at  TIMESTAMP,
    last_login_at    TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token      VARCHAR(512) NOT NULL UNIQUE,
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION update_updated_at();