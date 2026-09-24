-- ============================================
-- USER DB - schema hoan chinh (V2)
-- Chay 1 lan tren Supabase SQL Editor cua project user.
--
-- Quyet dinh da chot: 1A (3 DB rieng), 3B (OTP luu hash), 5 (them cot),
-- 6A (TIMESTAMP + UTC), 7A (CHECK cho cot chuan hoa).
-- Script tao DB MOI tu dau: DROP roi CREATE lai toan bo bang cua user_db.
--
-- CANH BAO: script xoa du lieu. Chi chay tren project user.
-- ============================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DROP TABLE IF EXISTS notifications CASCADE;
DROP TABLE IF EXISTS otp_verifications CASCADE;
DROP TABLE IF EXISTS refresh_tokens CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP FUNCTION IF EXISTS set_updated_at();

-- Moi thoi diem luu UTC, khop voi JVM (Dockerfile dat TZ=UTC).
SET TIME ZONE 'UTC';

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- users
-- ============================================
CREATE TABLE users (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    username varchar(50) NOT NULL,
    password varchar(255) NOT NULL,
    email varchar(100),
    full_name varchar(100),
    phone varchar(15),
    address varchar(255),
    role varchar(15) NOT NULL DEFAULT 'SALES',
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    is_2fa_enabled boolean NOT NULL DEFAULT false,
    two_factor_secret varchar(255),
    -- Moi: moc xac thuc email, la cho ghi ket qua OTP purpose VERIFY_EMAIL.
    email_verified_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
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
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_users_phone ON users (phone) WHERE phone IS NOT NULL;
CREATE INDEX idx_users_role_status ON users (role, status);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================
-- refresh_tokens
-- ============================================
CREATE TABLE refresh_tokens (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    -- SHA-256 hex = 64 ky tu (V1 ghi 70, khong khop code).
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
-- Token con hieu luc = chua thu hoi.
CREATE INDEX idx_refresh_tokens_active ON refresh_tokens (user_id, expires_at) WHERE revoked_at IS NULL;

-- ============================================
-- otp_verifications
-- ============================================
CREATE TABLE otp_verifications (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    -- Hash SHA-256 cua ma 6 so, khong luu plaintext (quyet dinh 3B).
    otp_code varchar(64) NOT NULL,
    purpose varchar(20) NOT NULL,
    -- So lan nhap sai; service huy OTP khi >= 5.
    attempts integer NOT NULL DEFAULT 0,
    expires_at timestamp NOT NULL,
    is_used boolean NOT NULL DEFAULT false,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT otp_verifications_pkey PRIMARY KEY (id),
    CONSTRAINT otp_verifications_purpose_check CHECK (
        purpose IN ('LOGIN', 'RESET_PASSWORD', 'VERIFY_EMAIL')),
    CONSTRAINT chk_otp_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT chk_otp_expiry CHECK (expires_at > created_at),
    CONSTRAINT fk_otp_verifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_otp_verifications_lookup ON otp_verifications (user_id, purpose, is_used, expires_at);
CREATE INDEX idx_otp_verifications_expires_at ON otp_verifications (expires_at);

CREATE TRIGGER trg_otp_verifications_updated_at
    BEFORE UPDATE ON otp_verifications FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================
-- notifications (moi): hop thu thong bao cua nhan vien
-- user_id FK vat ly duoc vi cung DB voi users.
-- ref_type/ref_id tro sang lead/deal/appointment o DB khac nen khong FK.
-- ============================================
CREATE TABLE notifications (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    type varchar(30) NOT NULL,
    title varchar(200) NOT NULL,
    body text,
    ref_type varchar(30),
    ref_id uuid,
    read_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT notifications_type_check CHECK (type IN (
        'APPOINTMENT_REMINDER', 'LEAD_DUE', 'DEAL_APPROVAL', 'EMAIL_FAILED')),
    CONSTRAINT notifications_ref_type_check CHECK (
        ref_type IS NULL OR ref_type IN ('APPOINTMENT', 'LEAD', 'DEAL', 'CUSTOMER')),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id, read_at);
CREATE INDEX idx_notifications_created_at ON notifications (created_at);

-- ============================================
-- Row Level Security: bat nhung khong dat policy.
-- Backend ket noi bang role so huu bang nen van doc/ghi binh thuong; anon key
-- cua Supabase thi khong vao duoc.
-- ============================================
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE otp_verifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;

-- Hash bcrypt cost 12 duoi day duoc sinh bang spring-security-crypto, cung
-- thu vien BCrypt ma user-service dang dung. Email/phone de NULL de khong tu
-- dat thong tin ca nhan; cap nhat qua API profile sau khi dang nhap.
-- ============================================
INSERT INTO users (username, password, full_name, role, status)
VALUES ('admin',
        '$2a$12$xdd0HRro20XZPIZB9NmoOutCWTFKdcJL8PmVh3jzOX3idNkDEDenq',
        'ADMIN DXMT',
        'ADMIN',
        'ACTIVE');
