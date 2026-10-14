package com.cocacola.eventintelligence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "registrations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_registrations_event_participant", columnNames = {"event_id", "participant_id"}),
        @UniqueConstraint(name = "uk_registrations_token", columnNames = "token")
})
@Getter
@Setter
@NoArgsConstructor
public class Registration extends UpdatableEntity {
    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Column(nullable = false, length = 36)
    private String token;
}
