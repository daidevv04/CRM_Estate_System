-- legacy ddl-auto=update schemas have enum check constraints that block new enum values
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
