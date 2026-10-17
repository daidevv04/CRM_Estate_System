-- V6: Add product lock columns and product_reservations table
ALTER TABLE products ADD COLUMN IF NOT EXISTS locked_by uuid;
ALTER TABLE products ADD COLUMN IF NOT EXISTS locked_customer_id uuid;
ALTER TABLE products ADD COLUMN IF NOT EXISTS lock_expires_at date;
ALTER TABLE products ADD COLUMN IF NOT EXISTS lock_deposit_amount numeric(15, 2);

CREATE TABLE IF NOT EXISTS product_reservations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    customer_id uuid NOT NULL,
    sales_id uuid NOT NULL,
    priority int NOT NULL DEFAULT 1,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    deposit_amount numeric(15, 2) DEFAULT 0,
    expiry_date date NOT NULL,
    notes text,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reservations_product_id ON product_reservations (product_id);
CREATE INDEX IF NOT EXISTS idx_reservations_status ON product_reservations (status);
