-- ============================================
-- USER DB - khoi tao 3 bang
-- Chay 1 lan tren Supabase SQL Editor cua project user.
--
-- Luu y quan trong:
--  - Cot mat khau ten 'password' (khong phai 'password_hash') cho khop entity
--    User.java va schema dang chay tren Supabase.
--  - Cot ma OTP ten 'otp_code' (khong phai 'otp_code_hash').
--  - Cot thoi gian dung 'timestamp' (khong phai timestamptz) vi entity Java
--    khai bao LocalDateTime; dung timestamptz se lam Hibernate validate fail.
--
-- File nay phan anh DUNG schema dang chay tren Supabase user DB.
-- ============================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================
-- FUNCTION: set_updated_at
-- Tu dong cap nhat updated_at o tang DB, dung chung cho moi bang co cot nay.
-- ============================================
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- TABLE: users - tai khoan nhan vien
-- ============================================
CREATE TABLE users (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    username varchar(50) NOT NULL,
    password varchar(255) NOT NULL,
    email varchar(100),
    full_name varchar(100),
    phone varchar(15),
    address varchar(255),
    role varchar(15) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    is_2fa_enabled boolean NOT NULL DEFAULT false,
    two_factor_secret varchar(255),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_phone UNIQUE (phone),
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'MANAGER', 'SALES')),
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED')),
    -- Bat 2FA thi phai co secret; tat thi phai de trong.
    CONSTRAINT chk_users_2fa_secret CHECK (
        (is_2fa_enabled = false AND two_factor_secret IS NULL)
        OR (is_2fa_enabled = true AND two_factor_secret IS NOT NULL)),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);
-- Email unique khong phan biet hoa thuong, cho phep nhieu NULL.
CREATE UNIQUE INDEX uq_users_email ON users (email) WHERE email IS NOT NULL;
CREATE INDEX idx_users_role_status ON users (role, status);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================
-- TABLE: refresh_tokens - refresh token da hash
-- ============================================
CREATE TABLE refresh_tokens (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    token_hash varchar(64) NOT NULL,
    expires_at timestamp NOT NULL,
    revoked_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_refresh_tokens_expiry CHECK (expires_at > created_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
-- Token con hieu luc = chua thu hoi; index rieng cho truy van thuong dung.
CREATE INDEX idx_refresh_tokens_active ON refresh_tokens (user_id, expires_at) WHERE revoked_at IS NULL;

-- ============================================
-- TABLE: otp_verifications - ma OTP dung mot lan
-- ============================================
CREATE TABLE otp_verifications (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    otp_code varchar(255) NOT NULL,
    purpose varchar(20) NOT NULL,
    expires_at timestamp NOT NULL,
    is_used boolean NOT NULL DEFAULT false,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT otp_verifications_pkey PRIMARY KEY (id),
    CONSTRAINT otp_verifications_purpose_check CHECK (purpose IN ('LOGIN', 'RESET_PASSWORD', 'VERIFY_EMAIL')),
    CONSTRAINT chk_otp_expiry CHECK (expires_at > created_at),
    CONSTRAINT fk_otp_verifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_otp_verifications_lookup ON otp_verifications (user_id, purpose, is_used, expires_at);
CREATE INDEX idx_otp_verifications_expires_at ON otp_verifications (expires_at);

CREATE TRIGGER trg_otp_verifications_updated_at
    BEFORE UPDATE ON otp_verifications FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================
-- Row Level Security: bat nhung khong dat policy.
-- Backend ket noi bang role so huu bang nen van doc/ghi binh thuong; anon key
-- cua Supabase thi khong vao duoc. Giong cau hinh dang chay.
-- ============================================
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE otp_verifications ENABLE ROW LEVEL SECURITY;
