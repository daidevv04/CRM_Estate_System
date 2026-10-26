-- Existing duplicates must be reconciled before this migration; never discard a reservation automatically.
CREATE UNIQUE INDEX uq_product_reservations_active
ON product_reservations (product_id)
WHERE status = 'ACTIVE';
