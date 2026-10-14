package com.cocacola.eventintelligence.repository;

import com.cocacola.eventintelligence.entity.Participant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {
    Optional<Participant> findByEmailIgnoreCase(String email);
}