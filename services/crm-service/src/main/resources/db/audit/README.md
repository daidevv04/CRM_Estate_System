# Sales integrity rollout

`spring.flyway.enabled=false` in the current application configuration. Merely deploying the JAR does not apply V9. Run the SQL migration explicitly using the existing database administration process.

1. Back up the database; stop writes to products, leads, contracts, payments and the reservation scheduler.
2. Run `sales_integrity_audit.sql` against a staging copy, then the target database. Keep findings for reconciliation; do not automatically delete reservations or fabricate receipts.
3. Resolve duplicate ACTIVE reservations with the business owner before applying `../migration/V9__unique_active_product_reservation.sql`. The unique index deliberately fails on duplicates.
4. Deploy the CRM backend and matching frontend together. Request `paymentStatus` is retained for older clients but is ignored; the response uses receipts. Cancelled contracts cannot be reopened and contracts with receipts cannot be deleted.
5. On a dedicated PostgreSQL test database verify two concurrent lock requests for one available product produce one success and one 409, with exactly one ACTIVE reservation. Also verify two concurrent approvals for the same property yield only one approved sale; expiry scanning cannot reopen a sold property. Unit mocks alone do not prove database locking.
6. Smoke-check all three roles and the three payment schedule templates before reopening writes.

Rollback: stop writes first. Keep the unique index when rolling back application binaries unless it is proven to cause the incident. Do not restore an old whole-database backup after new receipts have been recorded. Reconcile affected records individually.

The audit is read-only; it does not repair historical data. If `deal_payments` is absent in an environment, resolve the existing schema mismatch before running payment reconciliation.
