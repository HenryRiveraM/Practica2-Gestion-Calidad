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
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
public class Campaign extends UpdatableEntity {
    @Column(nullable = false, length = 150)
    private String name;
    private String description;
    @Column(name = "start_date")
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Column(precision = 14, scale = 2)
    private BigDecimal budget;
    @Column(nullable = false, length = 20)
    private String status = "PLANNED";
}