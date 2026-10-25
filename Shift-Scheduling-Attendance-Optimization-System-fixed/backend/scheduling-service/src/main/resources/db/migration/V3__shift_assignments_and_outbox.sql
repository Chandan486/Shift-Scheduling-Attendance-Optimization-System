-- Direct shift -> employee assignments, created together with the shift (or edited later).
-- employee_id / user_id are snapshots taken from employee-service at assignment time, so the
-- schedule still displays correctly even if the employee record is later renamed or deactivated.
CREATE TABLE shift_assignments (
    id BIGSERIAL PRIMARY KEY,
    shift_id BIGINT NOT NULL REFERENCES shifts(id) ON DELETE CASCADE,
    employee_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    employee_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    assigned_by BIGINT,
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_shift_assignment_employee UNIQUE (shift_id, employee_id)
);
CREATE INDEX idx_shift_assignments_employee ON shift_assignments(employee_id);
CREATE INDEX idx_shift_assignments_user ON shift_assignments(user_id);

-- Transactional outbox for SHIFT_ASSIGNED notifications (see NotificationOutboxService).
CREATE TABLE notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    target_path VARCHAR(200) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_error VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMP,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);
CREATE INDEX idx_outbox_pending ON notification_outbox(status, next_attempt_at);
