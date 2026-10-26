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

## Automated PostgreSQL checks

With the database credentials and JWT secret loaded into the environment, set `IT_INFRA=up`, `CONSUL_ENABLED=false`, `SPRING_CLOUD_CONFIG_ENABLED=false` and run `mvn test` in `services/crm-service`.

The CRM integration tests create a uniquely named `crm_test_*` schema, apply V1 through V10 there, and configure every application connection to use only that schema. The database role needs permission to create and drop schemas. Normal context shutdown drops the test schema; an interrupted JVM may leave a `crm_test_*` schema requiring manual cleanup. Never run the old version of `CrmRepositorySchemaTests`, which used the default schema for cleanup.

`SalesIntegrityIntegrationTests` exercises concurrent reservations, concurrent approvals, concurrent receipts, expiry versus approval, sold-inventory protection, customer ownership and the partial unique index. Other service context tests validate their configured infrastructure; these checks do not replace browser acceptance testing.

V10 records the previously provisioned `deal_payments` table so fresh databases can be initialized from the repository. Existing payment rows are preserved.
