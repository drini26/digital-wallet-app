-- auth-service schema: users and single-use sign-in links.

-- Users sign in with an email or a phone number (no passwords).
CREATE TABLE users (
    id           UUID PRIMARY KEY,
    email        VARCHAR(254),
    phone        VARCHAR(16),
    display_name VARCHAR(100) NOT NULL,
    role         VARCHAR(16)  NOT NULL CHECK (role IN ('CUSTOMER', 'MANAGER')),
    account_type VARCHAR(16)  NOT NULL CHECK (account_type IN ('PERSONAL', 'MERCHANT')),
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BLOCKED')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- An email or phone belongs to one user (this also stops two parallel sign-ups).
    CONSTRAINT ux_users_email UNIQUE (email),
    CONSTRAINT ux_users_phone UNIQUE (phone),
    CONSTRAINT chk_users_identifier CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

-- Sign-in links. Only the SHA-256 hash of the token is stored.
CREATE TABLE magic_link_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    -- Set with UPDATE ... WHERE used_at IS NULL, so only one of two parallel clicks wins.
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_magic_link_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_magic_link_tokens_user ON magic_link_tokens (user_id);
