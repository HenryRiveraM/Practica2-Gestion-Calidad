-- The backend records QR-token check-ins as TOKEN.
ALTER TABLE attendances DROP CONSTRAINT IF EXISTS ck_attendances_method;
ALTER TABLE attendances
    ADD CONSTRAINT ck_attendances_method
    CHECK (check_in_method IS NULL OR check_in_method IN ('QR', 'MANUAL', 'TOKEN'));
