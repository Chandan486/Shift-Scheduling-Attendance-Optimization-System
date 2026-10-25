-- Role-based approval: who applied (id, role, display name) and when it was decided.
ALTER TABLE leave_requests ADD COLUMN applicant_user_id BIGINT;
ALTER TABLE leave_requests ADD COLUMN applicant_role VARCHAR(20);
ALTER TABLE leave_requests ADD COLUMN applicant_name VARCHAR(100);
ALTER TABLE leave_requests ADD COLUMN reviewed_at TIMESTAMP;
-- Optimistic locking as a second guard against double decisions (pessimistic lock is the primary one).
ALTER TABLE leave_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE leave_requests ADD CONSTRAINT chk_applicant_role CHECK (applicant_role IS NULL OR applicant_role IN ('EMPLOYEE', 'HR', 'ADMIN'));

-- Existing rows: the old frontend used the user id as employeeId, so that is the applicant id.
-- applicant_role stays NULL for legacy rows; the service then requires an ADMIN approver (safe default).
UPDATE leave_requests SET applicant_user_id = employee_id WHERE applicant_user_id IS NULL;

CREATE INDEX idx_leave_requests_status_role ON leave_requests(status, applicant_role);
CREATE INDEX idx_leave_requests_employee_status ON leave_requests(employee_id, status);

-- Transactional outbox: notifications are written in the same transaction as the decision
-- and delivered afterwards, with retries, so a notification outage never corrupts a decision.
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
