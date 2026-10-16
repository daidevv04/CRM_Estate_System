-- ============================================
-- V2: Bang notifications va check constraint day du 11 loai thong bao
-- ============================================

CREATE TABLE IF NOT EXISTS notifications (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    type varchar(30) NOT NULL,
    title varchar(200) NOT NULL,
    body text,
    ref_type varchar(30),
    ref_id uuid,
    read_at timestamp,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT notifications_type_check CHECK (
        type IN (
            'APPOINTMENT_REMINDER',
            'LEAD_DUE',
            'DEAL_APPROVAL',
            'EMAIL_FAILED',
            'LEAD_CREATED',
            'LEAD_STAGE_CHANGED',
            'CUSTOMER_CREATED',
            'DEAL_CREATED',
            'DEAL_WON',
            'DEAL_LOST',
            'SYSTEM_ANNOUNCEMENT'
        )
    ),
    CONSTRAINT notifications_ref_type_check CHECK (
        ref_type IS NULL OR ref_type IN ('APPOINTMENT', 'LEAD', 'DEAL', 'CUSTOMER')
    )
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON notifications (user_id, read_at) WHERE read_at IS NULL;
