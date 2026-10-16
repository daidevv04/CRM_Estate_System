-- Bang phan cong nhan su (Manager / Sales) vao du an cu the.
-- Phuc vu viec data scoping: Manager chi quan ly cac du an duoc gan.
CREATE TABLE IF NOT EXISTS project_assignments (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    project_id uuid NOT NULL,
    user_id uuid NOT NULL,
    assigned_role varchar(20) NOT NULL,
    assigned_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by uuid,
    CONSTRAINT project_assignments_pkey PRIMARY KEY (id),
    CONSTRAINT fk_project_assignments_project FOREIGN KEY (project_id) 
        REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT project_assignments_role_check CHECK (assigned_role IN ('MANAGER', 'SALES'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_project_assignments_project_user 
    ON project_assignments (project_id, user_id);

CREATE INDEX IF NOT EXISTS idx_project_assignments_user_id 
    ON project_assignments (user_id);

CREATE INDEX IF NOT EXISTS idx_project_assignments_project_id 
    ON project_assignments (project_id);

ALTER TABLE project_assignments ENABLE ROW LEVEL SECURITY;
