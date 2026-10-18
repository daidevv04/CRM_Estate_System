-- V8: Add project commission policies, deal commissions, and commission milestones
CREATE TABLE IF NOT EXISTS project_commission_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    commission_rate NUMERIC(5, 2) NOT NULL DEFAULT 2.50,
    default_bonus_amount NUMERIC(15, 2) DEFAULT 0,
    milestone_rules TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_commission_policy UNIQUE (project_id)
);

CREATE TABLE IF NOT EXISTS deal_commissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deal_id UUID NOT NULL UNIQUE REFERENCES deals(id) ON DELETE CASCADE,
    sales_id UUID NOT NULL,
    contract_value NUMERIC(15, 2) NOT NULL,
    commission_rate NUMERIC(5, 2) NOT NULL,
    commission_amount NUMERIC(15, 2) NOT NULL,
    bonus_amount NUMERIC(15, 2) DEFAULT 0,
    total_expected NUMERIC(15, 2) NOT NULL,
    total_paid NUMERIC(15, 2) DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_deal_commissions_sales ON deal_commissions(sales_id);
CREATE INDEX IF NOT EXISTS idx_deal_commissions_status ON deal_commissions(status);

CREATE TABLE IF NOT EXISTS deal_commission_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deal_commission_id UUID NOT NULL REFERENCES deal_commissions(id) ON DELETE CASCADE,
    milestone_no INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    customer_paid_threshold_pct NUMERIC(5, 2) NOT NULL,
    payout_percentage NUMERIC(5, 2) NOT NULL,
    amount_expected NUMERIC(15, 2) NOT NULL,
    amount_paid NUMERIC(15, 2) DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_MILESTONE',
    approved_by UUID,
    approved_at TIMESTAMP,
    paid_at TIMESTAMP,
    note TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_commission_milestones_deal_comm ON deal_commission_milestones(deal_commission_id);
CREATE INDEX IF NOT EXISTS idx_commission_milestones_status ON deal_commission_milestones(status);
