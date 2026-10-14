CREATE UNIQUE INDEX IF NOT EXISTS uk_attendances_event_participant
    ON attendances (event_id, participant_id);
