package com.cocacola.eventintelligence.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_interactions")
@Getter
@Setter
@NoArgsConstructor
public class ProductInteraction extends BaseEntity {
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Column(name = "participant_id", nullable = false)
    private Long participantId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "activity_id")
    private Long activityId;
    @Column(nullable = false)
    private Boolean tried = true;
    private Short rating;
    @Column(name = "purchase_intent", length = 10)
    private String purchaseIntent;
    private String comment;
    @Column(name = "interacted_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime interactedAt;
}