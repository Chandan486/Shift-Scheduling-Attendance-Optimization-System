CREATE TABLE leave_requests (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    leave_type VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    reason VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT,
    review_notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    CONSTRAINT chk_leave_type CHECK (leave_type IN ('ANNUAL', 'SICK', 'PERSONAL', 'UNPAID')),
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_dates CHECK (end_date >= start_date)
);

CREATE TABLE leave_balances (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    annual_leave INTEGER NOT NULL DEFAULT 20,
    sick_leave INTEGER NOT NULL DEFAULT 10,
    personal_leave INTEGER NOT NULL DEFAULT 5,
    annual_used INTEGER NOT NULL DEFAULT 0,
    sick_used INTEGER NOT NULL DEFAULT 0,
    personal_used INTEGER NOT NULL DEFAULT 0,
    year INTEGER NOT NULL,
    CONSTRAINT uq_employee_year UNIQUE (employee_id, year)
);

CREATE INDEX idx_leave_requests_employee ON leave_requests(employee_id);
CREATE INDEX idx_leave_requests_status ON leave_requests(status);
CREATE INDEX idx_leave_requests_dates ON leave_requests(start_date, end_date);
CREATE INDEX idx_leave_balances_employee_year ON leave_balances(employee_id, year);
