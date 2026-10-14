package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.dto.CheckInRequest;
import com.cocacola.eventintelligence.dto.CheckInResponse;
import com.cocacola.eventintelligence.entity.Attendance;
import com.cocacola.eventintelligence.entity.Event;
import com.cocacola.eventintelligence.entity.Participant;
import com.cocacola.eventintelligence.entity.Registration;
import com.cocacola.eventintelligence.exception.BusinessRuleException;
import com.cocacola.eventintelligence.exception.DuplicateCheckInException;
import com.cocacola.eventintelligence.exception.InvalidRegistrationTokenException;
import com.cocacola.eventintelligence.repository.AttendanceRepository;
import com.cocacola.eventintelligence.repository.EventRepository;
import com.cocacola.eventintelligence.repository.ParticipantRepository;
import com.cocacola.eventintelligence.repository.RegistrationRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckInService {
    @org.springframework.beans.factory.annotation.Autowired
    protected com.cocacola.eventintelligence.security.AccessPolicy access;

    private static final String CHECKED_IN = "CHECKED_IN";
    private static final String CHECK_IN_METHOD = "QR";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final java.util.Set<String> ATTENDANCE_UNIQUE_CONSTRAINTS = java.util.Set.of(
            "uk_attendances_event_participant", "uq_attendances_event_participant");

    private final RegistrationRepository registrationRepository;
    private final AttendanceRepository attendanceRepository;
    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;

    public CheckInService(
            RegistrationRepository registrationRepository,
            AttendanceRepository attendanceRepository,
            EventRepository eventRepository,
            ParticipantRepository participantRepository) {
        this.registrationRepository = registrationRepository;
        this.attendanceRepository = attendanceRepository;
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public CheckInResponse checkIn(CheckInRequest request) {
        String normalizedToken = extractToken(request.qrToken());
        Registration registration = registrationRepository.findByToken(normalizedToken)
                .orElseThrow(() -> new InvalidRegistrationTokenException("No existe una inscripción para este QR"));
        access.requireOperate(registration.getEventId());
        Event event = eventRepository.findById(registration.getEventId())
                .orElseThrow(() -> new InvalidRegistrationTokenException("El evento de esta inscripción no existe"));
        Participant participant = participantRepository.findById(registration.getParticipantId())
                .orElseThrow(() -> new InvalidRegistrationTokenException("El participante de esta inscripción no existe"));

        Attendance attendance = attendanceRepository
                .findByEventIdAndParticipantId(registration.getEventId(), registration.getParticipantId())
                .orElseGet(() -> newAttendance(registration));

        if (CHECKED_IN.equals(attendance.getStatus())) {
            throw new DuplicateCheckInException();
        }

        attendance.setStatus(CHECKED_IN);
        attendance.setCheckInAt(OffsetDateTime.now(ZoneOffset.UTC));
        attendance.setCheckInMethod(CHECK_IN_METHOD);

        try {
            Attendance saved = attendanceRepository.saveAndFlush(attendance);
            return new CheckInResponse(
                    saved.getId(),
                    saved.getEventId(),
                    saved.getParticipantId(),
                    event.getName(),
                    participant.getFirstName() + " " + participant.getLastName(),
                    saved.getStatus(),
                    saved.getCheckInAt());
        } catch (DataIntegrityViolationException exception) {
            if (isAttendanceUniqueConstraint(exception)) {
                throw new DuplicateCheckInException();
            }
            throw exception;
        }
    }

    private Attendance newAttendance(Registration registration) {
        Attendance attendance = new Attendance();
        attendance.setEventId(registration.getEventId());
        attendance.setParticipantId(registration.getParticipantId());
        return attendance;
    }

    /**
     * Extrae el UUID del texto recibido. Tolera espacios, prefijos como "Token QR: "
     * o URLs que contengan el token, para no rechazar lecturas válidas del QR.
     */
    private String extractToken(String rawToken) {
        if (rawToken == null) {
            throw new BusinessRuleException("El token QR no es válido");
        }
        Matcher matcher = UUID_PATTERN.matcher(rawToken);
        if (!matcher.find()) {
            throw new BusinessRuleException("El token QR no es válido");
        }
        return UUID.fromString(matcher.group()).toString();
    }

    private boolean isAttendanceUniqueConstraint(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && ATTENDANCE_UNIQUE_CONSTRAINTS.contains(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}