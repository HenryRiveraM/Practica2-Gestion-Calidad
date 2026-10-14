CREATE TABLE registrations (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    participant_id BIGINT NOT NULL REFERENCES participants(id),
    token VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_registrations_event_participant UNIQUE (event_id, participant_id),
    CONSTRAINT uk_registrations_token UNIQUE (token)
);
