-- Da chay tren Supabase SQL Editor: giu file nay de schema trong repo khop DB.
ALTER TABLE projects ADD COLUMN IF NOT EXISTS image_url varchar(500);
ALTER TABLE products ADD COLUMN IF NOT EXISTS image_url varchar(500);