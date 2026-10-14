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
@Table(name = "surveys", uniqueConstraints = {
        @UniqueConstraint(name = "uk_surveys_event_participant", columnNames = {"event_id", "participant_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class Survey extends BaseEntity {
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Column(name = "participant_id", nullable = false)
    private Long participantId;
    @Column(name = "organization_score", nullable = false)
    private Short organizationScore;
    @Column(name = "service_score", nullable = false)
    private Short serviceScore;
    @Column(name = "experience_score", nullable = false)
    private Short experienceScore;
    @Column(name = "product_score", nullable = false)
    private Short productScore;
    @Column(name = "overall_satisfaction", nullable = false)
    private Short overallSatisfaction;
    @Column(name = "nps_score", nullable = false)
    private Short npsScore;
    private String comment;
    @Column(name = "submitted_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime submittedAt;
}