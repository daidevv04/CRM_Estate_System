-- Read-only reconciliation. Run before V9; each result set lists records requiring review.
-- More than one active reservation per product.
SELECT product_id, count(*) AS active_count, array_agg(id) AS reservation_ids
FROM product_reservations WHERE status = 'ACTIVE'
GROUP BY product_id HAVING count(*) > 1;

-- Product/reservation state or customer mismatch.
SELECT p.id AS product_id, p.status AS product_status, r.id AS reservation_id,
       p.locked_customer_id, r.customer_id
FROM products p FULL JOIN product_reservations r ON r.product_id = p.id AND r.status = 'ACTIVE'
WHERE (p.status = 'RESERVED' AND (r.id IS NULL OR p.locked_customer_id IS DISTINCT FROM r.customer_id
       OR p.lock_expires_at IS DISTINCT FROM r.expiry_date OR p.locked_by IS DISTINCT FROM r.sales_id))
   OR (r.id IS NOT NULL AND p.status IS DISTINCT FROM 'RESERVED');

-- Multiple approved, non-cancelled contracts for the same property.
SELECT l.product_id, count(*) AS approved_count, array_agg(d.id) AS deal_ids
FROM deals d JOIN leads l ON l.id = d.lead_id
WHERE d.approval_status = 'APPROVED' AND d.status <> 'CANCELLED'
GROUP BY l.product_id HAVING count(*) > 1;

-- Approved contracts whose property is no longer SOLD.
SELECT d.id AS deal_id, l.product_id, p.status
FROM deals d JOIN leads l ON l.id = d.lead_id LEFT JOIN products p ON p.id = l.product_id
WHERE d.approval_status = 'APPROVED' AND d.status <> 'CANCELLED'
  AND p.status IS DISTINCT FROM 'SOLD';

-- Payment status mismatch or receipts exceeding contract value. Never create synthetic receipts.
WITH totals AS (
    SELECT d.id, d.payment_status, d.contract_value, coalesce(sum(p.amount), 0) AS paid
    FROM deals d LEFT JOIN deal_payments p ON p.deal_id = d.id
    GROUP BY d.id
), expected AS (
    SELECT *, CASE WHEN paid <= 0 THEN 'UNPAID'
      WHEN contract_value > 0 AND paid = contract_value THEN 'PAID' ELSE 'PARTIAL' END AS expected_status
    FROM totals
)
SELECT * FROM expected
WHERE payment_status IS DISTINCT FROM expected_status OR paid > contract_value;

-- First installment differs from the contract deposit; review custom schedules manually.
SELECT d.id AS deal_id, d.deposit_amount, s.amount_due AS first_installment
FROM deals d JOIN deal_payment_schedules s ON s.deal_id = d.id AND s.installment_no = 1
WHERE coalesce(d.deposit_amount, 0) <> s.amount_due;
