package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.dto.RegistrationRequest;
import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import com.cocacola.eventintelligence.security.RequestThrottle;
import com.cocacola.eventintelligence.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/events")
public class PublicEventController {
    private final JdbcTemplate jdbc;
    private final RegistrationService registrations;
    private final RequestThrottle throttle;
    public PublicEventController(JdbcTemplate jdbc, RegistrationService registrations, RequestThrottle throttle) {
        this.jdbc=jdbc; this.registrations=registrations; this.throttle=throttle;
    }
    public record PublicRegistration(@NotBlank @Size(max=100) String firstName, @NotBlank @Size(max=100) String lastName,
        @NotBlank @Email @Size(max=255) String email, @Pattern(regexp="^$|^\\+?[0-9][0-9 ()-]{5,29}$") String phone,
        @Size(max=100) String city, @Pattern(regexp="<18|18-24|25-34|35-44|45-54|55\\+") String ageRange,
        @Size(max=100) String productPreference, Boolean communicationConsent) {}
    @GetMapping public List<Map<String,Object>> availableEvents() {
        return jdbc.queryForList("""
            SELECT e.id,e.name,e.type,e.event_date AS "eventDate",e.location,e.description,e.status,p.token
            FROM event_public_access p JOIN events e ON e.id=p.event_id
            WHERE p.enabled=TRUE AND e.status IN ('PLANNED','ACTIVE')
            ORDER BY CASE WHEN e.status='ACTIVE' THEN 0 ELSE 1 END,e.event_date,e.id
            """);
    }
    @GetMapping("/{token}") public Map<String,Object> event(@PathVariable String token) {
        var rows=jdbc.queryForList("""
            SELECT e.id,e.name,e.type,e.event_date AS "eventDate",e.location,e.description
            FROM event_public_access p JOIN events e ON e.id=p.event_id
            WHERE p.token=? AND p.enabled=TRUE AND e.status IN ('PLANNED','ACTIVE')
            """,token);
        if(rows.isEmpty()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Inscripción no disponible");
        return rows.getFirst();
    }
    @PostMapping("/{token}/registrations") public Map<String,Object> register(@PathVariable String token, @Valid @RequestBody PublicRegistration body, HttpServletRequest request) {
        throttle.check("registration:"+request.getRemoteAddr(),40);
        var event=event(token);
        com.cocacola.eventintelligence.dto.RegistrationResponse result;
        try { result=registrations.register(new RegistrationRequest(((Number)event.get("id")).longValue(),body.firstName(),body.lastName(),body.email(),body.phone(),
                body.city(),body.ageRange(),body.productPreference(),body.communicationConsent()));
        } catch (com.cocacola.eventintelligence.exception.DuplicateRegistrationException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Este correo ya tiene una inscripción en el evento. Solicita ayuda al equipo de registro.");
        }
        return Map.of("eventName",result.eventName(),"participantName",body.firstName().trim()+" "+body.lastName().trim(),"qrToken",result.qrToken());
    }
}
