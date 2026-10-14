package com.cocacola.eventintelligence.repository;

import com.cocacola.eventintelligence.entity.Survey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyRepository extends JpaRepository<Survey, Long> {
    boolean existsByEventIdAndParticipantId(Long eventId, Long participantId);
}