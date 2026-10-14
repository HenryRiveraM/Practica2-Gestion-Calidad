-- Cuentas para la base de demostración incluida en database/init/.
-- Solo se crean si existe el evento semilla; nunca se reemplazan cuentas existentes.
-- Las contraseñas se almacenan como hashes BCrypt (coste 12), sin texto plano.
DO $$
DECLARE
    created_user_id BIGINT;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM events
        WHERE id = 5 AND name = 'Sabores de Primavera Cochabamba'
    ) THEN
        RETURN;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'admin@demo.local') THEN
        INSERT INTO app_users (name, email, password_hash)
        VALUES ('Administrador Demo', 'admin@demo.local',
                '$2y$12$7TnvF1HWoZXzfuD5c3xu1uOEP68AQRIwBsV8B3nsdXlqBH9CYTr8C')
        RETURNING id INTO created_user_id;
        INSERT INTO app_user_roles (user_id, role)
        VALUES (created_user_id, 'ADMINISTRADOR');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'organizador@demo.local') THEN
        INSERT INTO app_users (name, email, password_hash)
        VALUES ('Organizador Demo', 'organizador@demo.local',
                '$2y$12$AmAeXfGnGCdB8mcAMPCmluZCk50jvRiFuooZoJk5AWG21zqIOBjS2')
        RETURNING id INTO created_user_id;
        INSERT INTO app_user_roles (user_id, role)
        VALUES (created_user_id, 'ORGANIZADOR');
        INSERT INTO app_user_events (user_id, event_id)
        SELECT created_user_id, id FROM events WHERE status = 'ACTIVE';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'marketing@demo.local') THEN
        INSERT INTO app_users (name, email, password_hash)
        VALUES ('Marketing Demo', 'marketing@demo.local',
                '$2y$12$Aqhnvk4a3ufARn1lpNSEDeB42sAeU8Wel3/C5Rg9wJj3nF6XEM5o.')
        RETURNING id INTO created_user_id;
        INSERT INTO app_user_roles (user_id, role)
        VALUES (created_user_id, 'MARKETING');
    END IF;
END $$;
