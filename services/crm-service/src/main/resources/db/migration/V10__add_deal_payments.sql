-- Existing Supabase installations already have this table; fresh databases need it too.
CREATE TABLE IF NOT EXISTS deal_payments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    deal_id uuid NOT NULL REFERENCES deals(id) ON DELETE CASCADE,
    amount numeric(15, 2) NOT NULL CHECK (amount > 0),
    paid_at timestamp NOT NULL,
    method varchar(30) CHECK (method IS NULL OR method IN ('CASH', 'BANK_TRANSFER', 'LOAN')),
    receipt_url varchar(500),
    note text,
    created_by uuid,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_deal_payments_deal_id ON deal_payments (deal_id);
