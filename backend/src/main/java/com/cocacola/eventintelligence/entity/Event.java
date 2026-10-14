package com.cocacola.eventintelligence.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event extends UpdatableEntity {
    @Column(name = "campaign_id")
    private Long campaignId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 50)
    private String type;
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;
    @Column(nullable = false, length = 200)
    private String location;
    private String description;
    private String objective;
    @Column(precision = 14, scale = 2)
    private BigDecimal budget;
    @Column(name = "expected_participants")
    private Integer expectedParticipants;
    @Column(length = 150)
    private String responsible;
    @Column(nullable = false, length = 20)
    private String status = "PLANNED";
}