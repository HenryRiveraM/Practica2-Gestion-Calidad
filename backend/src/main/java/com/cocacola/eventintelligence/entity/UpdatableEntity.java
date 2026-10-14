package com.cocacola.eventintelligence.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Getter
@Setter
@MappedSuperclass
public abstract class UpdatableEntity extends BaseEntity {
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}