-- Seed sample shifts for the next 7 days
-- Using department_id = 1 (assumes a General/Operations department exists in employee-service)

-- Morning shifts
INSERT INTO shifts (name, department_id, start_time, end_time, date, overnight, max_staff, min_staff, night_shift)
VALUES
    ('Morning Shift A', 1, '06:00:00', '14:00:00', CURRENT_DATE, false, 5, 2, false),
    ('Morning Shift A', 1, '06:00:00', '14:00:00', CURRENT_DATE + INTERVAL '1 day', false, 5, 2, false),
    ('Morning Shift A', 1, '06:00:00', '14:00:00', CURRENT_DATE + INTERVAL '2 days', false, 5, 2, false),
    ('Morning Shift A', 1, '06:00:00', '14:00:00', CURRENT_DATE + INTERVAL '3 days', false, 5, 2, false),
    ('Morning Shift A', 1, '06:00:00', '14:00:00', CURRENT_DATE + INTERVAL '4 days', false, 5, 2, false),
    ('Morning Shift A', 1, '06:00:00', '14:00:00', CURRENT_DATE + INTERVAL '5 days', false, 5, 2, false),
    ('Morning Shift A', 1, '06:00:00', '14:00:00', CURRENT_DATE + INTERVAL '6 days', false, 5, 2, false);

-- Afternoon shifts
INSERT INTO shifts (name, department_id, start_time, end_time, date, overnight, max_staff, min_staff, night_shift)
VALUES
    ('Afternoon Shift B', 1, '14:00:00', '22:00:00', CURRENT_DATE, false, 5, 2, false),
    ('Afternoon Shift B', 1, '14:00:00', '22:00:00', CURRENT_DATE + INTERVAL '1 day', false, 5, 2, false),
    ('Afternoon Shift B', 1, '14:00:00', '22:00:00', CURRENT_DATE + INTERVAL '2 days', false, 5, 2, false),
    ('Afternoon Shift B', 1, '14:00:00', '22:00:00', CURRENT_DATE + INTERVAL '3 days', false, 5, 2, false),
    ('Afternoon Shift B', 1, '14:00:00', '22:00:00', CURRENT_DATE + INTERVAL '4 days', false, 5, 2, false),
    ('Afternoon Shift B', 1, '14:00:00', '22:00:00', CURRENT_DATE + INTERVAL '5 days', false, 5, 2, false),
    ('Afternoon Shift B', 1, '14:00:00', '22:00:00', CURRENT_DATE + INTERVAL '6 days', false, 5, 2, false);

-- Night shifts
INSERT INTO shifts (name, department_id, start_time, end_time, date, overnight, max_staff, min_staff, night_shift)
VALUES
    ('Night Shift C', 1, '22:00:00', '06:00:00', CURRENT_DATE, true, 3, 1, true),
    ('Night Shift C', 1, '22:00:00', '06:00:00', CURRENT_DATE + INTERVAL '1 day', true, 3, 1, true),
    ('Night Shift C', 1, '22:00:00', '06:00:00', CURRENT_DATE + INTERVAL '2 days', true, 3, 1, true),
    ('Night Shift C', 1, '22:00:00', '06:00:00', CURRENT_DATE + INTERVAL '3 days', true, 3, 1, true),
    ('Night Shift C', 1, '22:00:00', '06:00:00', CURRENT_DATE + INTERVAL '4 days', true, 3, 1, true),
    ('Night Shift C', 1, '22:00:00', '06:00:00', CURRENT_DATE + INTERVAL '5 days', true, 3, 1, true),
    ('Night Shift C', 1, '22:00:00', '06:00:00', CURRENT_DATE + INTERVAL '6 days', true, 3, 1, true);

-- Add skill requirements for some shifts (skill_id = 1 assumed to exist)
INSERT INTO shift_requirements (shift_id, skill_id, required_count)
SELECT id, 1, 1 FROM shifts WHERE name = 'Night Shift C' AND date = CURRENT_DATE;
