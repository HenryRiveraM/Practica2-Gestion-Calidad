package com.cocacola.eventintelligence.repository;

import com.cocacola.eventintelligence.entity.Attendance;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    boolean existsByEventIdAndParticipantIdAndStatus(Long eventId, Long participantId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Attendance> findByEventIdAndParticipantId(Long eventId, Long participantId);
}