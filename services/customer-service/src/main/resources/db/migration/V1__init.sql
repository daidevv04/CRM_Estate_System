-- ============================================
-- CUSTOMER DB - khoi tao 5 bang
-- Chay 1 lan tren Supabase SQL Editor cua project customer.
--
-- Luu y: cot thoi gian dung 'timestamp' (khong phai timestamptz) vi entity
-- Java khai bao LocalDateTime; dung timestamptz se lam Hibernate validate fail.
--
-- File nay phan anh DUNG schema dang chay tren Supabase customer DB.
-- ============================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
-- btree_gist can cho EXCLUDE constraint tren appointments (so sanh uuid bang =)
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ============================================
-- FUNCTION: set_updated_at
-- Tu dong cap nhat updated_at o tang DB cho ca 5 bang.
-- ============================================
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- customers: ho so khach hang
-- owner_id la UUID tho tro toi user_db.users.id (DB khac, khong FK vat ly)
-- ============================================
CREATE TABLE customers (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    full_name varchar(100) NOT NULL,
    phone varchar(15),
    email varchar(100),
    demand_type varchar(20) NOT NULL DEFAULT 'BUY',
    source varchar(50),
    owner_id uuid NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'NEW',
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT customers_pkey PRIMARY KEY (id),
    CONSTRAINT customers_demand_type_check CHECK (demand_type IN ('BUY', 'SELL', 'RENT', 'INVEST')),
    CONSTRAINT customers_status_check CHECK (status IN ('NEW', 'POTENTIAL', 'CUSTOMER', 'INACTIVE'))
);
-- Email/phone unique khong phan biet hoa thuong va bo qua NULL, de nhieu khach
-- chua co email/phone khong bi coi la trung nhau.
CREATE UNIQUE INDEX uk_customers_email_lower ON customers (lower(email));
CREATE UNIQUE INDEX uq_customers_phone ON customers (phone) WHERE phone IS NOT NULL;
CREATE INDEX idx_customers_owner_id ON customers (owner_id);
CREATE INDEX idx_customers_status ON customers (status);
CREATE INDEX idx_customers_demand_type ON customers (demand_type);

CREATE TRIGGER trg_customers_updated_at
    BEFORE UPDATE ON customers FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================
-- customer_cares: nhat ky cham soc
-- FK khong CASCADE: service chan truoc khi xoa khach con nhat ky.
-- ============================================
CREATE TABLE customer_cares (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    customer_id uuid NOT NULL,
    type varchar(20) NOT NULL,
    content text NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT customer_cares_pkey PRIMARY KEY (id),
    CONSTRAINT customer_cares_type_check CHECK (type IN ('CALL', 'MEETING', 'EMAIL', 'NOTE')),
    CONSTRAINT fk_customer_cares_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);
CREATE INDEX idx_customer_cares_customer_id ON customer_cares (customer_id);

CREATE TRIGGER trg_customer_cares_updated_at
    BEFORE UPDATE ON customer_cares FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================
-- appointments: lich hen gap mat
-- sales_id la UUID tho tro toi user_db.users.id (DB khac, khong FK vat ly)
-- ============================================
CREATE TABLE appointments (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    customer_id uuid NOT NULL,
    sales_id uuid NOT NULL,
    title varchar(200) NOT NULL,
    start_time timestamp NOT NULL,
    end_time timestamp NOT NULL,
    color varchar(10),
    reminder_minutes integer,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT appointments_pkey PRIMARY KEY (id),
    CONSTRAINT appointments_status_check CHECK (status IN ('PENDING', 'DONE', 'CANCELLED')),
    CONSTRAINT chk_appointments_time CHECK (end_time > start_time),
    CONSTRAINT fk_appointments_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    -- Chan 2 lich giao nhau cua cung 1 sales o tan DB. Day moi la hang rao that:
    -- countOverlapping o service chi la check truoc, hai request cung luc van lot.
    CONSTRAINT ex_appointments_no_overlap EXCLUDE USING gist (
        sales_id WITH =,
        tsrange(start_time, end_time) WITH &&
    ) WHERE (status <> 'CANCELLED')
);
CREATE INDEX idx_appointments_customer_id ON appointments (customer_id);
CREATE INDEX idx_appointments_sales_id ON appointments (sales_id);
CREATE INDEX idx_appointments_start_time ON appointments (start_time);

CREATE TRIGGER trg_appointments_updated_at
    BEFORE UPDATE ON appointments FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Luu y: rang buoc 'start_time phai o tuong lai' KHONG the dat o DB vi now() la
-- ham volatile, khong dung duoc trong CHECK. No duoc chan o AppointmentService.

-- ============================================
-- email_templates: mau email tai su dung
-- ============================================
CREATE TABLE email_templates (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    name varchar(100) NOT NULL,
    subject varchar(255) NOT NULL,
    body text NOT NULL,
    category varchar(50) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT email_templates_pkey PRIMARY KEY (id),
    CONSTRAINT email_templates_category_check CHECK (category IN ('WELCOME', 'FOLLOW_UP', 'PROMOTION', 'CONTRACT')),
    CONSTRAINT email_templates_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE UNIQUE INDEX uq_email_templates_name ON email_templates (name);
CREATE INDEX idx_email_templates_category ON email_templates (category);

CREATE TRIGGER trg_email_templates_updated_at
    BEFORE UPDATE ON email_templates FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================
-- email_cares: chi tiet 1 email da gui
-- subject/body duoc luu lai tai thoi diem gui (ban sao), nen sua mau sau nay
-- khong lam sai lich su. Xoa mau chi set template_id = NULL, khong xoa log.
-- ============================================
CREATE TABLE email_cares (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    customer_care_id uuid NOT NULL,
    template_id uuid,
    to_email varchar(100) NOT NULL,
    subject varchar(255) NOT NULL,
    body text NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'SENT',
    sent_at timestamp,
    opened_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT email_cares_pkey PRIMARY KEY (id),
    CONSTRAINT email_cares_status_check CHECK (status IN ('SENT', 'FAILED', 'OPENED', 'CLICKED')),
    CONSTRAINT fk_email_cares_customer_care FOREIGN KEY (customer_care_id)
        REFERENCES customer_cares (id) ON DELETE CASCADE,
    CONSTRAINT fk_email_cares_template FOREIGN KEY (template_id)
        REFERENCES email_templates (id) ON DELETE SET NULL
);
CREATE INDEX idx_email_cares_customer_care_id ON email_cares (customer_care_id);
CREATE INDEX idx_email_cares_template_id ON email_cares (template_id);
CREATE INDEX idx_email_cares_status ON email_cares (status);

CREATE TRIGGER trg_email_cares_updated_at
    BEFORE UPDATE ON email_cares FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================
-- Row Level Security: bat nhung khong dat policy.
-- Backend ket noi bang role so huu bang nen van doc/ghi binh thuong; anon key
-- cua Supabase thi khong vao duoc. Giong cau hinh dang chay.
-- ============================================
ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_cares ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_cares ENABLE ROW LEVEL SECURITY;
