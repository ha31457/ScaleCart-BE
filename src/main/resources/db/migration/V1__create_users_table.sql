CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TYPE user_role AS ENUM ('CUSTOMER', 'SELLER', 'ADMIN');

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            user_role    NOT NULL DEFAULT 'CUSTOMER',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Email lookups happen on every login — must be fast
CREATE UNIQUE INDEX idx_users_email ON users(email);

-- Soft-delete pattern: we never DELETE users, we disable them
CREATE INDEX idx_users_enabled ON users(enabled) WHERE enabled = TRUE;