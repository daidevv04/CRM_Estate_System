-- ============================================
-- CRM DB - schema hoan chinh (V2)
-- Chay 1 lan tren Supabase SQL Editor cua project CRM.
--
-- Quyet dinh da chot: 1A (3 DB rieng), 4 (them lead_stage_history, deal_payments),
-- 6A (TIMESTAMP + UTC), 7A (CHECK cho products.direction, deals.payment_method).
-- Script tao DB MOI tu dau: DROP roi CREATE lai toan bo bang cua crm_db.
--
-- CANH BAO: script xoa du lieu. Chi chay tren project CRM.
--
-- Quan he xuyen DB (khong the dat FK vat ly, khac project Supabase):
--   customers.id  <- leads.customer_id                  (customer DB)
--   users.id      <- leads.assigned_to, deals.sales_id, deals.approved_by,
--                    moi bang *_by                      (user DB)
-- Cac FK con lai deu trong cung DB nay nen rang buoc that, DB tu chan.
-- ============================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DROP TABLE IF EXISTS contact_detail CASCADE;
DROP TABLE IF EXISTS deal_payments CASCADE;
DROP TABLE IF EXISTS deals CASCADE;
DROP TABLE IF EXISTS lead_stage_history CASCADE;
DROP TABLE IF EXISTS leads CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS projects CASCADE;
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
-- projects: du an bat dong san
-- ============================================
CREATE TABLE projects (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    name varchar(200) NOT NULL,
    location varchar(255),
    investor varchar(150),
    description text,
    status varchar(20) NOT NULL DEFAULT 'PLANNING',
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT projects_pkey PRIMARY KEY (id),
    CONSTRAINT projects_status_check CHECK (status IN ('PLANNING', 'SELLING', 'SOLD_OUT', 'CLOSED'))
);
-- Khong dat UNIQUE(name): hai chu dau tu khac nhau co the trung ten du an.
CREATE INDEX idx_projects_status ON projects (status);
CREATE INDEX idx_projects_investor ON projects (investor);

CREATE TRIGGER trg_projects_updated_at
    BEFORE UPDATE ON projects FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================
-- products: san pham bat dong san (can ho, dat, nha pho)
-- ============================================
CREATE TABLE products (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    project_id uuid NOT NULL,
    code varchar(50) NOT NULL,
    type varchar(20) NOT NULL,
    area numeric(10, 2),
    block varchar(20),
    price numeric(15, 2),
    bedroom integer,
    direction varchar(20),
    status varchar(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT products_pkey PRIMARY KEY (id),
    CONSTRAINT products_type_check CHECK (type IN ('APARTMENT', 'LAND', 'TOWNHOUSE')),
    CONSTRAINT products_status_check CHECK (status IN ('AVAILABLE', 'RESERVED', 'SOLD')),
    -- 7A: chuan hoa huong, khong con free-text.
    CONSTRAINT products_direction_check CHECK (
        direction IS NULL OR direction IN ('N', 'S', 'E', 'W', 'NE', 'NW', 'SE', 'SW')),
    CONSTRAINT chk_products_area_positive CHECK (area IS NULL OR area > 0),
    CONSTRAINT chk_products_price_non_negative CHECK (price IS NULL OR price >= 0),
    CONSTRAINT chk_products_bedroom_non_negative CHECK (bedroom IS NULL OR bedroom >= 0),
    CONSTRAINT fk_products_project FOREIGN KEY (project_id) REFERENCES projects (id)
);
-- Ma can/lo chi can duy nhat trong pham vi mot du an.
CREATE UNIQUE INDEX uq_products_project_code ON products (project_id, code);
CREATE INDEX idx_products_project_id ON products (project_id);
CREATE INDEX idx_products_status ON products (status);
CREATE INDEX idx_products_type ON products (type);
CREATE INDEX idx_products_block ON products (block) WHERE block IS NOT NULL;

CREATE TRIGGER trg_products_updated_at
    BEFORE UPDATE ON products FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================
-- leads: khach hang tiem nang quan tam mot san pham
-- customer_id tro toi customers.id o customer DB: khong FK vat ly duoc.
-- ============================================
CREATE TABLE leads (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    customer_id uuid NOT NULL,
    product_id uuid NOT NULL,
    stage varchar(20) NOT NULL DEFAULT 'NEW',
    expected_value numeric(15, 2),
    close_date date,
    assigned_to uuid NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT leads_pkey PRIMARY KEY (id),
    CONSTRAINT leads_stage_check CHECK (stage IN (
        'NEW', 'CONTACTED', 'INTERESTED', 'PROPOSAL_SENT', 'NEGOTIATION',
        'INTERNAL_REVIEW', 'WON', 'LOST')),
    CONSTRAINT chk_leads_expected_value_non_negative CHECK (
        expected_value IS NULL OR expected_value >= 0),
    CONSTRAINT fk_leads_product FOREIGN KEY (product_id) REFERENCES products (id)
);
CREATE INDEX idx_leads_customer_id ON leads (customer_id);
CREATE INDEX idx_leads_product_id ON leads (product_id);
CREATE INDEX idx_leads_assigned_to ON leads (assigned_to);
CREATE INDEX idx_leads_stage ON leads (stage);
CREATE INDEX idx_leads_close_date ON leads (close_date) WHERE close_date IS NOT NULL;
-- Danh sach lead cua mot sales theo stage: truy van thuong gap cua pipeline.
CREATE INDEX idx_leads_assign_stage ON leads (assigned_to, stage);

CREATE TRIGGER trg_leads_updated_at
    BEFORE UPDATE ON leads FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================
-- lead_stage_history (moi): lich su doi stage cua lead
-- leads chi giu stage hien tai; bang nay giu vet de bao cao funnel.
-- Append-only: khong co updated_at/updated_by.
-- ============================================
CREATE TABLE lead_stage_history (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    lead_id uuid NOT NULL,
    from_stage varchar(20),
    to_stage varchar(20) NOT NULL,
    changed_by uuid,
    changed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT lead_stage_history_pkey PRIMARY KEY (id),
    CONSTRAINT lead_stage_history_from_stage_check CHECK (from_stage IS NULL OR from_stage IN (
        'NEW', 'CONTACTED', 'INTERESTED', 'PROPOSAL_SENT', 'NEGOTIATION',
        'INTERNAL_REVIEW', 'WON', 'LOST')),
    CONSTRAINT lead_stage_history_to_stage_check CHECK (to_stage IN (
        'NEW', 'CONTACTED', 'INTERESTED', 'PROPOSAL_SENT', 'NEGOTIATION',
        'INTERNAL_REVIEW', 'WON', 'LOST')),
    CONSTRAINT chk_lead_stage_history_changed CHECK (from_stage IS NULL OR from_stage <> to_stage),
    CONSTRAINT fk_lead_stage_history_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE CASCADE
);
CREATE INDEX idx_lead_stage_history_lead ON lead_stage_history (lead_id, changed_at);
CREATE INDEX idx_lead_stage_history_to_stage ON lead_stage_history (to_stage);

-- ============================================
-- deals: hop dong chinh thuc khi mot lead chot thanh cong
-- 1 lead chi ra 1 deal nen lead_id la UNIQUE. Nhieu san pham trong cung mot hop
-- dong nam o contact_detail.
-- ============================================
CREATE TABLE deals (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    lead_id uuid NOT NULL,
    sales_id uuid NOT NULL,
    contract_code varchar(50) NOT NULL,
    contract_value numeric(15, 2),
    deposit_amount numeric(15, 2),
    deposit_date date,
    signed_date date,
    payment_status varchar(20) NOT NULL DEFAULT 'UNPAID',
    payment_method varchar(30),
    approval_status varchar(20) NOT NULL DEFAULT 'PENDING',
    approved_by uuid,
    approved_at timestamp,
    file_url varchar(500),
    status varchar(20) NOT NULL DEFAULT 'IN_PROGRESS',
    note text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT deals_pkey PRIMARY KEY (id),
    CONSTRAINT uq_deals_contract_code UNIQUE (contract_code),
    CONSTRAINT uq_deals_lead_id UNIQUE (lead_id),
    CONSTRAINT deals_payment_status_check CHECK (payment_status IN ('UNPAID', 'PARTIAL', 'PAID')),
    -- 7A: chuan hoa hinh thuc thanh toan, khong con free-text.
    CONSTRAINT deals_payment_method_check CHECK (
        payment_method IS NULL OR payment_method IN ('CASH', 'BANK_TRANSFER', 'LOAN')),
    CONSTRAINT deals_approval_status_check CHECK (
        approval_status IN ('PENDING', 'APPROVED', 'REJECTED', 'NOT_REQUIRED')),
    CONSTRAINT deals_status_check CHECK (
        status IN ('IN_PROGRESS', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_deals_contract_value_non_negative CHECK (
        contract_value IS NULL OR contract_value >= 0),
    CONSTRAINT chk_deals_deposit_non_negative CHECK (
        deposit_amount IS NULL OR deposit_amount >= 0),
    -- Dat coc khong the lon hon tong gia tri hop dong.
    CONSTRAINT chk_deals_deposit_within_value CHECK (
        deposit_amount IS NULL OR contract_value IS NULL OR deposit_amount <= contract_value),
    -- APPROVED thi phai co nguoi duyet va thoi diem duyet; cac trang thai khac de trong.
    CONSTRAINT chk_deals_approved_fields CHECK (
        (approval_status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR (approval_status <> 'APPROVED')),
    CONSTRAINT fk_deals_lead FOREIGN KEY (lead_id) REFERENCES leads (id)
);
CREATE INDEX idx_deals_sales_id ON deals (sales_id);
CREATE INDEX idx_deals_status ON deals (status);
CREATE INDEX idx_deals_payment_status ON deals (payment_status);
CREATE INDEX idx_deals_approval_status ON deals (approval_status);
-- Hang cho duyet: loc theo trang thai roi sap theo ngay tao.
CREATE INDEX idx_deals_created_at ON deals (created_at);

CREATE TRIGGER trg_deals_updated_at
    BEFORE UPDATE ON deals FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================
-- deal_payments (moi): tung dot thanh toan cua hop dong
-- deals.payment_status chi la trang thai tong; bang nay cho bao cao doanh thu.
-- Append-only: khong co updated_at/updated_by.
-- ============================================
CREATE TABLE deal_payments (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    deal_id uuid NOT NULL,
    amount numeric(15, 2) NOT NULL,
    paid_at timestamp NOT NULL,
    method varchar(30),
    receipt_url varchar(500),
    note text,
    created_by uuid,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT deal_payments_pkey PRIMARY KEY (id),
    CONSTRAINT chk_deal_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT deal_payments_method_check CHECK (
        method IS NULL OR method IN ('CASH', 'BANK_TRANSFER', 'LOAN')),
    CONSTRAINT fk_deal_payments_deal FOREIGN KEY (deal_id) REFERENCES deals (id) ON DELETE CASCADE
);
CREATE INDEX idx_deal_payments_deal ON deal_payments (deal_id, paid_at);
CREATE INDEX idx_deal_payments_paid_at ON deal_payments (paid_at);

-- ============================================
-- contact_detail: chi tiet san pham trong mot hop dong
-- Ten bang giu nguyen theo thiet ke; y nghia la "dong san pham cua deal".
-- FK CASCADE: xoa hop dong thi cac dong chi tiet di theo, khong con y nghia.
-- ============================================
CREATE TABLE contact_detail (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    deal_id uuid NOT NULL,
    product_id uuid NOT NULL,
    unit_price numeric(15, 2),
    quantity integer NOT NULL DEFAULT 1,
    note text,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_by uuid,
    CONSTRAINT contact_detail_pkey PRIMARY KEY (id),
    CONSTRAINT chk_contact_detail_unit_price_non_negative CHECK (
        unit_price IS NULL OR unit_price >= 0),
    CONSTRAINT chk_contact_detail_quantity_positive CHECK (quantity > 0),
    CONSTRAINT fk_contact_detail_deal FOREIGN KEY (deal_id)
        REFERENCES deals (id) ON DELETE CASCADE,
    CONSTRAINT fk_contact_detail_product FOREIGN KEY (product_id) REFERENCES products (id)
);
-- Cung mot san pham khong duoc xuat hien 2 lan trong cung mot hop dong.
CREATE UNIQUE INDEX uq_contact_detail_deal_product ON contact_detail (deal_id, product_id);
CREATE INDEX idx_contact_detail_deal_id ON contact_detail (deal_id);
CREATE INDEX idx_contact_detail_product_id ON contact_detail (product_id);

CREATE TRIGGER trg_contact_detail_updated_at
    BEFORE UPDATE ON contact_detail FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Luu y: "mot san pham chi ban cho mot hop dong dang hieu luc" KHONG the dat o DB
-- vi phu thuoc trang thai cua bang deals (CANCELLED thi san pham lai ban duoc).
-- Chan o tang service khi tao contact_detail: san pham phai AVAILABLE/RESERVED.

-- ============================================
-- Row Level Security: bat nhung khong dat policy.
-- ============================================
ALTER TABLE projects ENABLE ROW LEVEL SECURITY;
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE leads ENABLE ROW LEVEL SECURITY;
ALTER TABLE lead_stage_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE deals ENABLE ROW LEVEL SECURITY;
ALTER TABLE deal_payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE contact_detail ENABLE ROW LEVEL SECURITY;
