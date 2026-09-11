CREATE TABLE users (
    id uuid NOT NULL,
    address varchar(255),
    created_at timestamptz NOT NULL,
    email varchar(100) NOT NULL,
    full_name varchar(100) NOT NULL,
    password_hash varchar(255) NOT NULL,
    phone varchar(16),
    role varchar(15) NOT NULL,
    status varchar(20) NOT NULL,
    is_2fa_enabled boolean NOT NULL,
    two_factor_secret varchar(255),
    updated_at timestamptz NOT NULL,
    username varchar(50) NOT NULL,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users (id)
);

CREATE TABLE refresh_tokens (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    token_hash varchar(64) NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_refresh_token_hash ON refresh_tokens (token_hash);

CREATE TABLE otp_verifications (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    otp_code_hash varchar(255) NOT NULL,
    purpose varchar(20) NOT NULL,
    updated_at timestamptz NOT NULL,
    is_used boolean NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT otp_verifications_pkey PRIMARY KEY (id),
    CONSTRAINT fk_otp_verifications_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_otp_user_purpose_used_expires ON otp_verifications (user_id, purpose, is_used, expires_at);
