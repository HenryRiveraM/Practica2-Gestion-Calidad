package com.cocacola.eventintelligence.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "activities")
@Getter
@Setter
@NoArgsConstructor
public class Activity extends UpdatableEntity {
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Column(name = "participant_id")
    private Long participantId;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 20)
    private String type;
    private String description;
    @Column(name = "start_at")
    private OffsetDateTime startAt;
    @Column(name = "end_at")
    private OffsetDateTime endAt;
    @Column
    private Integer quantity;
    @Column(columnDefinition = "text")
    private String result;
    @Column(nullable = false, length = 20)
    private String status = "PLANNED";
}