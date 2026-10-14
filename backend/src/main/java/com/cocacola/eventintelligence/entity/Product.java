package com.cocacola.eventintelligence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product extends UpdatableEntity {
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 80)
    private String category;
    @Column(length = 80)
    private String flavor;
    @Column(length = 80)
    private String presentation;
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}