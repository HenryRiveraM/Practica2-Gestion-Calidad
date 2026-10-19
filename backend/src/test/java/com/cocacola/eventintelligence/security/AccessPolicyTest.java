
    
  
package com.cocacola.eventintelligence.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.entity.Interaction;
import com.cocacola.eventintelligence.entity.Survey;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AccessPolicyTest {

    private static final long EVENT_ID = 10L;
    private static final long PARTICIPANT_ID = 20L;

    @Mock
    private JdbcTemplate jdbc;

    private AccessPolicy accessPolicy;

    @BeforeEach
    void setUp() {
        accessPolicy = new AccessPolicy(jdbc);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("P2-17-C01: permite operar al organizador asignado al evento")
    void allowsOperateForOrganizerAssignedToEvent() {
        authenticate(Role.ORGANIZADOR, Set.of(EVENT_ID));

        assertDoesNotThrow(() -> accessPolicy.requireOperate(EVENT_ID));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-17-C02: deniega operar al organizador no asignado al evento")
    void deniesOperateForOrganizerWithoutAssignedEvent() {
        authenticate(Role.ORGANIZADOR, Set.of(99L));

        assertThrows(AccessDeniedException.class, () -> accessPolicy.requireOperate(EVENT_ID));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-17-C03: deniega operaciones al rol de marketing")
    void deniesOperateForMarketing() {
        authenticate(Role.MARKETING, Set.of(EVENT_ID));

        assertThrows(AccessDeniedException.class, () -> accessPolicy.requireOperate(EVENT_ID));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-17-C04: deniega una interacción cuya actividad pertenece a otro evento")
    void deniesWriteWhenInteractionActivityDoesNotBelongToEvent() {
        authenticate(Role.ORGANIZADOR, Set.of(EVENT_ID));
        Interaction interaction = new Interaction();
        interaction.setEventId(EVENT_ID);
        interaction.setActivityId(30L);

        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(30L), eq(EVENT_ID)))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> accessPolicy.requireWrite(interaction));

        verify(jdbc).queryForObject(anyString(), eq(Boolean.class), eq(30L), eq(EVENT_ID));
    }

    @Test
    @DisplayName("P2-17-C05: deniega escritura cuando el participante no pertenece al evento")
    void deniesWriteWhenParticipantDoesNotBelongToEvent() {
        authenticate(Role.ORGANIZADOR, Set.of(EVENT_ID));
        Survey survey = surveyFor(EVENT_ID, PARTICIPANT_ID);

        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(EVENT_ID), eq(PARTICIPANT_ID),
                eq(EVENT_ID), eq(PARTICIPANT_ID))).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> accessPolicy.requireWrite(survey));

        verify(jdbc).queryForObject(anyString(), eq(Boolean.class), eq(EVENT_ID), eq(PARTICIPANT_ID),
                eq(EVENT_ID), eq(PARTICIPANT_ID));
    }

    @Test
    @DisplayName("P2-17-C06: permite escribir una encuesta de participante autorizado")
    void allowsWriteForSurveyWithAuthorizedParticipant() {
        authenticate(Role.ORGANIZADOR, Set.of(EVENT_ID));
        Survey survey = surveyFor(EVENT_ID, PARTICIPANT_ID);

        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(EVENT_ID), eq(PARTICIPANT_ID),
                eq(EVENT_ID), eq(PARTICIPANT_ID))).thenReturn(true);

        assertDoesNotThrow(() -> accessPolicy.requireWrite(survey));

        verify(jdbc).queryForObject(anyString(), eq(Boolean.class), eq(EVENT_ID), eq(PARTICIPANT_ID),
                eq(EVENT_ID), eq(PARTICIPANT_ID));
    }

    private void authenticate(Role role, Set<Long> eventIds) {
        Account account = new Account(1L, "Test User", "test@example.com", "secret", true, 0,
                Set.of(role), eventIds);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(account, null, account.getAuthorities()));
    }

    private Survey surveyFor(long eventId, long participantId) {
        Survey survey = new Survey();
        survey.setEventId(eventId);
        survey.setParticipantId(participantId);
        return survey;
    }
}

