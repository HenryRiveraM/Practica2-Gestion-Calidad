package com.cocacola.eventintelligence.repository;

import com.cocacola.eventintelligence.entity.Registration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    boolean existsByEventIdAndParticipantId(Long eventId, Long participantId);

    Optional<Registration> findByToken(String token);
}
