INSERT INTO departments (name, description) VALUES
('Engineering', 'Software development team'),
('Operations', 'Operations and logistics'),
('Customer Support', 'Customer service team'),
('Security', 'Security operations')
ON CONFLICT (name) DO NOTHING;

INSERT INTO skills (name, description) VALUES
('Java', 'Java programming'),
('JavaScript', 'JavaScript/TypeScript'),
('Customer Service', 'Customer communication skills'),
('Security Monitoring', 'Security monitoring and surveillance'),
('Team Lead', 'Team leadership'),
('First Aid', 'Basic first aid certification'),
('Night Shift', 'Available for night shifts'),
('Forklift', 'Forklift operation')
ON CONFLICT (name) DO NOTHING;
