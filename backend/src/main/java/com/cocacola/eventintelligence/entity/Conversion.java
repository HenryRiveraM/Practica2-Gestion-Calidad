package com.cocacola.eventintelligence.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "conversions")
@Getter
@Setter
@NoArgsConstructor
public class Conversion extends BaseEntity {
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    @Column(name = "participant_id", nullable = false)
    private Long participantId;
    @Column(name = "conversion_type", nullable = false, length = 30)
    private String conversionType;
    @Column(name = "converted_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime convertedAt;
    @Column(length = 500)
    private String description;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode metadata;
}