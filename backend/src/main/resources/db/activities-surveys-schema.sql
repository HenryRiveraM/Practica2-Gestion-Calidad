ALTER TABLE activities
    ADD COLUMN IF NOT EXISTS participant_id BIGINT REFERENCES participants(id),
    ADD COLUMN IF NOT EXISTS quantity INTEGER,
    ADD COLUMN IF NOT EXISTS result TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_surveys_event_participant
    ON surveys (event_id, participant_id);
