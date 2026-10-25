CREATE TABLE shifts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    department_id BIGINT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    date DATE NOT NULL,
    overnight BOOLEAN NOT NULL DEFAULT false,
    max_staff INTEGER NOT NULL DEFAULT 10,
    min_staff INTEGER NOT NULL DEFAULT 1,
    night_shift BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE shift_requirements (
    id BIGSERIAL PRIMARY KEY,
    shift_id BIGINT NOT NULL REFERENCES shifts(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL,
    required_count INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE schedules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    published_at TIMESTAMP
);

CREATE TABLE schedule_assignments (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES schedules(id) ON DELETE CASCADE,
    shift_id BIGINT NOT NULL REFERENCES shifts(id),
    employee_id BIGINT NOT NULL,
    date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
    notes VARCHAR(500),
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(schedule_id, shift_id, employee_id, date)
);

CREATE INDEX idx_shifts_date ON shifts(date);
CREATE INDEX idx_shifts_department ON shifts(department_id);
CREATE INDEX idx_shifts_date_range ON shifts(date, start_time);
CREATE INDEX idx_schedules_status ON schedules(status);
CREATE INDEX idx_schedules_dates ON schedules(start_date, end_date);
CREATE INDEX idx_assignments_schedule ON schedule_assignments(schedule_id);
CREATE INDEX idx_assignments_employee ON schedule_assignments(employee_id);
CREATE INDEX idx_assignments_date ON schedule_assignments(date);
CREATE INDEX idx_assignments_employee_date ON schedule_assignments(employee_id, date);
CREATE INDEX idx_shift_requirements_shift ON shift_requirements(shift_id);
