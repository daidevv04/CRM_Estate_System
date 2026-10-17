-- V5: Alter leads table to add project_id, demand_notes and make product_id nullable
ALTER TABLE leads ADD COLUMN project_id uuid;
ALTER TABLE leads ADD COLUMN demand_notes text;

-- Backfill project_id from products
UPDATE leads l
SET project_id = p.project_id
FROM products p
WHERE l.product_id = p.id;

-- Fallback for any orphan lead if project_id is still null
UPDATE leads
SET project_id = (SELECT id FROM projects ORDER BY created_at LIMIT 1)
WHERE project_id IS NULL;

-- Enforce constraints: project_id is required, product_id is optional
ALTER TABLE leads ALTER COLUMN project_id SET NOT NULL;
ALTER TABLE leads ALTER COLUMN product_id DROP NOT NULL;

-- Foreign key and index for project_id
ALTER TABLE leads ADD CONSTRAINT fk_leads_project FOREIGN KEY (project_id) REFERENCES projects (id);
CREATE INDEX idx_leads_project_id ON leads (project_id);
