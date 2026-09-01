INSERT INTO users (id, email, password_hash, role, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'admin@clients-service.test',
    '$2b$10$Ms40Z/hCbN1uHkvjOAlub.TBRo3PkZjG7GaV4tjNwFBOEKPBi/cne',
    'ADMIN',
    now(),
    now()
);
