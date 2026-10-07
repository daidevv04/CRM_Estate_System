-- Event append-only cho funnel va audit pipeline. Da co the ton tai o DB cu,
-- nen dung IF NOT EXISTS de Flyway an toan tren moi moi truong.
CREATE TABLE IF NOT EXISTS lead_stage_history (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    lead_id uuid NOT NULL,
    from_stage varchar(20),
    to_stage varchar(20) NOT NULL,
    changed_by uuid,
    changed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT lead_stage_history_pkey PRIMARY KEY (id),
    CONSTRAINT fk_lead_stage_history_lead FOREIGN KEY (lead_id) REFERENCES leads (id) ON DELETE CASCADE,
    CONSTRAINT chk_lead_stage_history_from_stage CHECK (from_stage IS NULL OR from_stage IN (
        'NEW', 'CONTACTED', 'INTERESTED', 'PROPOSAL_SENT', 'NEGOTIATION', 'INTERNAL_REVIEW', 'WON', 'LOST')),
    CONSTRAINT chk_lead_stage_history_to_stage CHECK (to_stage IN (
        'NEW', 'CONTACTED', 'INTERESTED', 'PROPOSAL_SENT', 'NEGOTIATION', 'INTERNAL_REVIEW', 'WON', 'LOST'))
);

CREATE INDEX IF NOT EXISTS idx_lead_stage_history_lead_changed_at
    ON lead_stage_history (lead_id, changed_at);