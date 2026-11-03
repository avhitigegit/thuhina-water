-- Seed users for LOCAL and UAT only (never production) – the prototype's users (prototype/assets/js/data.js).
-- Every seed user's password is: demo1234
-- Flyway re-runs this file when it changes; existing users are left as they are.

INSERT INTO app_user (username, full_name, phone, role, password_hash, must_change_password, active, last_login_at, created_by)
VALUES
    ('nimal.admin',  'Nimal Perera',          '077 100 2201', 'ADMIN',          '$2a$10$sErP7sQLEW99mgYqGPsQJOk7HqOPtjGbKSQNtg7zGeOBhGBBIwJgW', FALSE, TRUE,  NULL, 'seed'),
    ('dilini.admin', 'Dilini Wickramasinghe', '071 220 4410', 'ADMIN',          '$2a$10$sErP7sQLEW99mgYqGPsQJOk7HqOPtjGbKSQNtg7zGeOBhGBBIwJgW', FALSE, TRUE,  NULL, 'seed'),
    ('shanika.acc',  'Shanika Fernando',      '076 550 1192', 'ACCOUNTANT',     '$2a$10$sErP7sQLEW99mgYqGPsQJOk7HqOPtjGbKSQNtg7zGeOBhGBBIwJgW', FALSE, TRUE,  NULL, 'seed'),
    ('kasun.d',      'Kasun Jayasinghe',      '077 881 3320', 'DELIVERY_STAFF', '$2a$10$sErP7sQLEW99mgYqGPsQJOk7HqOPtjGbKSQNtg7zGeOBhGBBIwJgW', FALSE, TRUE,  NULL, 'seed'),
    ('ruwan.d',      'Ruwan Bandara',         '070 446 7702', 'DELIVERY_STAFF', '$2a$10$sErP7sQLEW99mgYqGPsQJOk7HqOPtjGbKSQNtg7zGeOBhGBBIwJgW', FALSE, TRUE,  NULL, 'seed'),
    ('chamara.d',    'Chamara Silva',         '075 993 0018', 'DELIVERY_STAFF', '$2a$10$sErP7sQLEW99mgYqGPsQJOk7HqOPtjGbKSQNtg7zGeOBhGBBIwJgW', FALSE, FALSE, NULL, 'seed')
ON CONFLICT (username) DO NOTHING;
