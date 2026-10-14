package com.cocacola.eventintelligence.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "interactions")
@Getter
@Setter
@NoArgsConstructor
public class Interaction extends BaseEntity {
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Column(name = "participant_id", nullable = false)
    private Long participantId;
    @Column(name = "activity_id", nullable = false)
    private Long activityId;
    @Column(name = "interacted_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime interactedAt;
    private String notes;
}