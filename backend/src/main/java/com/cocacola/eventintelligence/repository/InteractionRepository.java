package com.cocacola.eventintelligence.repository;

import com.cocacola.eventintelligence.entity.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionRepository extends JpaRepository<Interaction, Long> {
}