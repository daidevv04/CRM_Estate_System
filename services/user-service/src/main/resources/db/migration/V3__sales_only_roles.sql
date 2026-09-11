-- UserRole back to 3 internal roles; no more CUSTOMER
UPDATE users SET role = 'SALES' WHERE role = 'CUSTOMER';
