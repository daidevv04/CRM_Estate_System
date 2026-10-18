-- V7: Add deal payment schedules table
CREATE TABLE IF NOT EXISTS deal_payment_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deal_id UUID NOT NULL REFERENCES deals(id) ON DELETE CASCADE,
    installment_no INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    percentage NUMERIC(5, 2),
    amount_due NUMERIC(15, 2) NOT NULL,
    due_date DATE NOT NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_deal_payment_schedules_deal ON deal_payment_schedules(deal_id);
