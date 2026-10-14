package com.cocacola.eventintelligence.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "participants")
@Getter
@Setter
@NoArgsConstructor
public class Participant extends UpdatableEntity {
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;
    @Column(length = 30)
    private String phone;
    @Column(length = 255)
    private String email;
    @Column(length = 100)
    private String city;
    @Column(name = "age_range", length = 10)
    private String ageRange;
    @Column(name = "product_preference", length = 100)
    private String productPreference;
    @Column(name = "communication_consent", nullable = false)
    private Boolean communicationConsent = false;
    @Column(name = "consent_at")
    private OffsetDateTime consentAt;
    @Column(name = "registration_source", nullable = false, length = 20)
    private String registrationSource = "WEB";
}