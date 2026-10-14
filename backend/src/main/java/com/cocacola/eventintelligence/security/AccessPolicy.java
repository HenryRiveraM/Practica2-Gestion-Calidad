package com.cocacola.eventintelligence.security;

import com.cocacola.eventintelligence.entity.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AccessPolicy {
    private final JdbcTemplate jdbc;
    public AccessPolicy(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Account account() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Account account)) throw denied();
        return account;
    }
    public Role role() {
        account();
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(a -> a.getAuthority()).filter(a -> a.startsWith("ROLE_")).map(a -> Role.valueOf(a.substring(5)))
                .findFirst().orElseThrow(AccessPolicy::denied);
    }
    public boolean isOrganizer() { return role() == Role.ORGANIZADOR; }
    public void requireAdmin() { if (role() != Role.ADMINISTRADOR) throw denied(); }
    public void requireReports() { if (!role().permissions().contains("reports")) throw denied(); }
    public void requireEvent(Long eventId) {
        if (eventId == null || (isOrganizer() && !account().eventIds().contains(eventId))) throw denied();
    }
    public void requireOperate(Long eventId) {
        if (!role().permissions().contains("operations")) throw denied();
        requireEvent(eventId);
    }
    public void requireParticipantAtEvent(Long eventId, Long participantId) {
        if (participantId == null) return;
        boolean present = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM registrations WHERE event_id=? AND participant_id=? UNION ALL SELECT 1 FROM attendances WHERE event_id=? AND participant_id=?)", Boolean.class, eventId,participantId,eventId,participantId));
        if (!present) throw denied();
    }
    public Set<Long> eventScope() { return isOrganizer() ? account().eventIds() : null; }
    public boolean canRead(BaseEntity entity) {
        Role role = role();
        if (role == Role.ADMINISTRADOR) return true;
        if (entity instanceof Participant p) {
            if (role == Role.MARKETING) return Boolean.TRUE.equals(p.getCommunicationConsent());
            return jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM app_user_events ue WHERE ue.user_id = ? AND (
                    EXISTS(SELECT 1 FROM registrations r WHERE r.event_id=ue.event_id AND r.participant_id=?) OR
                    EXISTS(SELECT 1 FROM attendances a WHERE a.event_id=ue.event_id AND a.participant_id=?)))
                """, Boolean.class, account().id(), p.getId(), p.getId());
        }
        if (entity instanceof Product) return true;
        if (entity instanceof Campaign c) return role == Role.MARKETING || jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM app_user_events ue JOIN events e ON e.id=ue.event_id WHERE ue.user_id=? AND e.campaign_id=?)
            """, Boolean.class, account().id(), c.getId());
        Long eventId = eventOf(entity);
        if (eventId == null) return false;
        if (role == Role.MARKETING) return entity instanceof Event || entity instanceof EventProduct;
        return account().eventIds().contains(eventId);
    }
    public void requireRead(BaseEntity entity) { if (!canRead(entity)) throw denied(); }
    public void requireWrite(BaseEntity entity) {
        Long activityId = null;
        if (entity instanceof Interaction e) activityId=e.getActivityId();
        if (entity instanceof ProductInteraction e) activityId=e.getActivityId();
        if (activityId != null && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM activities WHERE id=? AND event_id=?)", Boolean.class, activityId, eventOf(entity)))) throw denied();
        Long participant = null;
        if (entity instanceof Activity e) participant=e.getParticipantId();
        if (entity instanceof Survey e) participant=e.getParticipantId();
        if (entity instanceof Interaction e) participant=e.getParticipantId();
        if (entity instanceof ProductInteraction e) participant=e.getParticipantId();
        if (entity instanceof Conversion e) participant=e.getParticipantId();
        if (participant != null) requireParticipantAtEvent(eventOf(entity), participant);
        if (role() == Role.ADMINISTRADOR) return;
        if (entity instanceof Activity || entity instanceof Survey || entity instanceof Interaction || entity instanceof ProductInteraction || entity instanceof Conversion) {
            requireOperate(eventOf(entity));
            return;
        }
        throw denied();
    }
    public static Long eventOf(BaseEntity entity) {
        if (entity instanceof Event e) return e.getId();
        if (entity instanceof Activity e) return e.getEventId();
        if (entity instanceof Survey e) return e.getEventId();
        if (entity instanceof Attendance e) return e.getEventId();
        if (entity instanceof Interaction e) return e.getEventId();
        if (entity instanceof ProductInteraction e) return e.getEventId();
        if (entity instanceof Conversion e) return e.getEventId();
        if (entity instanceof EventProduct e) return e.getEventId();
        return null;
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("No tienes permiso para esta operación o evento."); }
}
