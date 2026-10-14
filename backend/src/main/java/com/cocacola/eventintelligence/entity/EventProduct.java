package com.cocacola.eventintelligence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "event_products")
@Getter
@Setter
@NoArgsConstructor
public class EventProduct extends BaseEntity {
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
}