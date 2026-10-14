package com.cocacola.eventintelligence.repository;

import com.cocacola.eventintelligence.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}