-- The demo seed stores registrations and check-ins in attendances. Mirror those
-- rows into registrations so the API and analytics count seeded participants.
INSERT INTO registrations (event_id, participant_id, token)
SELECT event_id, participant_id, gen_random_uuid()::text
FROM attendances
WHERE status <> 'CANCELLED'
ON CONFLICT (event_id, participant_id) DO NOTHING;
