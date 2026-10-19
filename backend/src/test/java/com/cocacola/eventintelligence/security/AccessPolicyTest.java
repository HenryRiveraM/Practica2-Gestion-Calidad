
    
  
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
import java.util.Collection;
import java.util.List;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
        SecurityContextHolder.clearContext();
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

    @Test
    @DisplayName("P2-18-C01: deniega cuando no existe autenticaciÃ³n")
    void deniesAccountWhenAuthenticationIsMissing() {
        // Arrange
        SecurityContextHolder.clearContext();

        // Act / Assert
        assertThrows(AccessDeniedException.class, () -> accessPolicy.account());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C02: deniega un principal autenticado que no es Account")
    void deniesAccountWhenPrincipalIsNotAnAccount() {
        // Arrange
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("not-an-account", null, List.of()));

        // Act / Assert
        assertThrows(AccessDeniedException.class, () -> accessPolicy.account());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C03: deniega Account sin autoridad ROLE_ vÃ¡lida")
    void deniesRoleWhenAccountHasNoRoleAuthority() {
        // Arrange
        Account account = account(Role.ORGANIZADOR, Set.of(EVENT_ID));
        authenticate(account, List.of(new SimpleGrantedAuthority("SCOPE_events.read")));

        // Act / Assert
        assertThrows(AccessDeniedException.class, () -> accessPolicy.role());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C04: permite administraciÃ³n al administrador")
    void allowsAdminForAdministrator() {
        // Arrange
        authenticate(Role.ADMINISTRADOR, Set.of());

        // Act / Assert
        assertDoesNotThrow(() -> accessPolicy.requireAdmin());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C05: deniega administraciÃ³n al organizador")
    void deniesAdminForNonAdministrator() {
        // Arrange
        authenticate(Role.ORGANIZADOR, Set.of(EVENT_ID));

        // Act / Assert
        assertThrows(AccessDeniedException.class, () -> accessPolicy.requireAdmin());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C06: permite reportes al administrador")
    void allowsReportsForAdministrator() {
        // Arrange
        authenticate(Role.ADMINISTRADOR, Set.of());

        // Act / Assert
        assertDoesNotThrow(() -> accessPolicy.requireReports());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C07: permite reportes a marketing")
    void allowsReportsForMarketing() {
        // Arrange
        authenticate(Role.MARKETING, Set.of());

        // Act / Assert
        assertDoesNotThrow(() -> accessPolicy.requireReports());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C08: deniega reportes al organizador")
    void deniesReportsForOrganizer() {
        // Arrange
        authenticate(Role.ORGANIZADOR, Set.of(EVENT_ID));

        // Act / Assert
        assertThrows(AccessDeniedException.class, () -> accessPolicy.requireReports());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C09: deniega un evento nulo")
    void deniesNullEvent() {
        // Arrange
        authenticate(Role.ADMINISTRADOR, Set.of());

        // Act / Assert
        assertThrows(AccessDeniedException.class, () -> accessPolicy.requireEvent(null));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C10: permite al administrador acceder a un evento no nulo")
    void allowsEventForAdministrator() {
        // Arrange
        authenticate(Role.ADMINISTRADOR, Set.of());

        // Act / Assert
        assertDoesNotThrow(() -> accessPolicy.requireEvent(EVENT_ID));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C11: permite a marketing acceder a un evento no nulo")
    void allowsEventForMarketing() {
        // Arrange
        authenticate(Role.MARKETING, Set.of());

        // Act / Assert
        assertDoesNotThrow(() -> accessPolicy.requireEvent(EVENT_ID));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C12: devuelve exactamente el alcance asignado al organizador")
    void returnsAssignedEventScopeForOrganizer() {
        // Arrange
        Set<Long> assignedEvents = Set.of(EVENT_ID, 99L);
        authenticate(Role.ORGANIZADOR, assignedEvents);

        // Act / Assert
        org.junit.jupiter.api.Assertions.assertEquals(assignedEvents, accessPolicy.eventScope());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C13: devuelve alcance nulo al administrador")
    void returnsNullEventScopeForAdministrator() {
        // Arrange
        authenticate(Role.ADMINISTRADOR, Set.of(EVENT_ID));

        // Act / Assert
        org.junit.jupiter.api.Assertions.assertNull(accessPolicy.eventScope());
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("P2-18-C14: devuelve alcance nulo a marketing")
    void returnsNullEventScopeForMarketing() {
        // Arrange
        authenticate(Role.MARKETING, Set.of(EVENT_ID));

        // Act / Assert
        org.junit.jupiter.api.Assertions.assertNull(accessPolicy.eventScope());
        verifyNoInteractions(jdbc);
    }

    private void authenticate(Role role, Set<Long> eventIds) {
        Account account = account(role, eventIds);
        authenticate(account, account.getAuthorities());
    }

    private Account account(Role role, Set<Long> eventIds) {
        return new Account(1L, "Test User", "test@example.com", "secret", true, 0,
                Set.of(role), eventIds);
    }

    private void authenticate(Account account, Collection<? extends GrantedAuthority> authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(account, null, authorities));
    }

    private Survey surveyFor(long eventId, long participantId) {
        Survey survey = new Survey();
        survey.setEventId(eventId);
        survey.setParticipantId(participantId);
        return survey;
    }
}

