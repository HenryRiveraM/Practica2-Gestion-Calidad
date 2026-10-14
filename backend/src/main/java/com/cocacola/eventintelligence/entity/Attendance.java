package com.cocacola.eventintelligence.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "attendances", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attendances_event_participant", columnNames = {"event_id", "participant_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class Attendance extends UpdatableEntity {
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Column(name = "participant_id", nullable = false)
    private Long participantId;
    @Column(nullable = false, length = 20)
    private String status = "REGISTERED";
    @Column(name = "registered_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime registeredAt;
    @Column(name = "check_in_at")
    private OffsetDateTime checkInAt;
    @Column(name = "check_in_method", length = 20)
    private String checkInMethod;
}