package com.cocacola.eventintelligence.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.controller.UserAdminController.CreateUser;
import com.cocacola.eventintelligence.controller.UserAdminController.UpdateUser;
import com.cocacola.eventintelligence.security.AccessPolicy;
import com.cocacola.eventintelligence.security.AccountManagement;
import com.cocacola.eventintelligence.security.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Notas de adaptación:
 * - Se asume que Role es un enum (se usa Role.values()[0]).
 * - AccessPolicy se mockea con RETURNS_DEEP_STUBS para poder stubear access.account().id().
 *   Si account() devuelve un record/clase final, necesitas Mockito 5+ (mock inline por defecto).
 * - SecurityException es un ejemplo de lo que lanza requireAdmin() cuando el usuario no es admin;
 *   cámbiala por tu excepción real (por ejemplo AccessDeniedException).
 */
@ExtendWith(MockitoExtension.class)
class UserAdminControllerTest {

    private static final Long ACTOR_ID = 1L;
    private static final Long USER_ID = 42L;

    private static Validator validator;

    @Mock
    private AccountManagement management;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AccessPolicy access;

    private UserAdminController controller;

    @BeforeAll
    static void initValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @BeforeEach
    void setUp() {
        controller = new UserAdminController(management, access);
    }

    // ---- constructor ----

    @Test
    void constructor_createsController() {
        assertNotNull(new UserAdminController(management, access));
    }

    // ---- GET list ----

    @Test
    void list_requiresAdminAndReturnsAccounts() {
        List<Map<String, Object>> accounts = List.of(Map.of("id", 1L, "name", "Ana"));
        when(management.list()).thenReturn(accounts);

        List<Map<String, Object>> result = controller.list();

        assertSame(accounts, result);
        InOrder order = inOrder(access, management);
        order.verify(access).requireAdmin();
        order.verify(management).list();
    }

    @Test
    void list_doesNotCallManagementWhenNotAdmin() {
        doThrow(new SecurityException("forbidden")).when(access).requireAdmin();

        assertThrows(SecurityException.class, () -> controller.list());

        verifyNoInteractions(management);
    }

    // ---- POST create ----

    @Test
    void create_delegatesToManagementWithActorAndBodyValues() {
        Set<Role> roles = Set.of(Role.values()[0]);
        Set<Long> eventIds = Set.of(7L, 8L);
        CreateUser body = new CreateUser("Ana", "ana@example.com", roles, eventIds);
        Map<String, Object> created = Map.of("id", USER_ID);
        when(access.account().id()).thenReturn(ACTOR_ID);
        when(management.create(ACTOR_ID, "Ana", "ana@example.com", roles, eventIds)).thenReturn(created);

        Map<String, Object> result = controller.create(body);

        assertSame(created, result);
        verify(access).requireAdmin();
        verify(management).create(ACTOR_ID, "Ana", "ana@example.com", roles, eventIds);
    }

    @Test
    void create_doesNotCallManagementWhenNotAdmin() {
        CreateUser body = new CreateUser("Ana", "ana@example.com", Set.of(Role.values()[0]), Set.of());
        doThrow(new SecurityException("forbidden")).when(access).requireAdmin();

        assertThrows(SecurityException.class, () -> controller.create(body));

        verifyNoInteractions(management);
    }

    // ---- PUT update ----

    @Test
    void update_delegatesToManagementWithActorIdAndBodyValues() {
        Set<Role> roles = Set.of(Role.values()[0]);
        Set<Long> eventIds = Set.of(3L);
        UpdateUser body = new UpdateUser("Ana Gómez", true, roles, eventIds);
        Map<String, Object> updated = Map.of("id", USER_ID, "enabled", true);
        when(access.account().id()).thenReturn(ACTOR_ID);
        when(management.update(ACTOR_ID, USER_ID, "Ana Gómez", true, roles, eventIds)).thenReturn(updated);

        Map<String, Object> result = controller.update(USER_ID, body);

        assertSame(updated, result);
        verify(access).requireAdmin();
        verify(management).update(ACTOR_ID, USER_ID, "Ana Gómez", true, roles, eventIds);
    }

    @Test
    void update_passesDisabledFlagThrough() {
        Set<Role> roles = Set.of(Role.values()[0]);
        UpdateUser body = new UpdateUser("Ana", false, roles, Set.of());
        when(access.account().id()).thenReturn(ACTOR_ID);
        when(management.update(ACTOR_ID, USER_ID, "Ana", false, roles, Set.of())).thenReturn(Map.of());

        controller.update(USER_ID, body);

        verify(management).update(ACTOR_ID, USER_ID, "Ana", false, roles, Set.of());
    }

    @Test
    void update_doesNotCallManagementWhenNotAdmin() {
        UpdateUser body = new UpdateUser("Ana", true, Set.of(Role.values()[0]), Set.of());
        doThrow(new SecurityException("forbidden")).when(access).requireAdmin();

        assertThrows(SecurityException.class, () -> controller.update(USER_ID, body));

        verifyNoInteractions(management);
    }

    // ---- POST activation ----

    @Test
    void invite_returnsActivationTokenFromManagement() {
        when(access.account().id()).thenReturn(ACTOR_ID);
        when(management.invite(ACTOR_ID, USER_ID)).thenReturn("token-abc");

        Map<String, String> result = controller.invite(USER_ID);

        assertEquals(Map.of("activationToken", "token-abc"), result);
        verify(access).requireAdmin();
        verify(management).invite(ACTOR_ID, USER_ID);
    }

    @Test
    void invite_doesNotCallManagementWhenNotAdmin() {
        doThrow(new SecurityException("forbidden")).when(access).requireAdmin();

        assertThrows(SecurityException.class, () -> controller.invite(USER_ID));

        verifyNoInteractions(management);
    }

    // ---- Validaciones de los records ----

    @Test
    void createUser_validBodyHasNoViolations() {
        CreateUser body = new CreateUser("Ana", "ana@example.com", Set.of(Role.values()[0]), Set.of(1L));

        assertTrue(validator.validate(body).isEmpty());
    }

    @Test
    void createUser_rejectsBlankNameInvalidEmailAndEmptyRoles() {
        CreateUser body = new CreateUser(" ", "no-es-email", Set.of(), Set.of());

        assertEquals(Set.of("name", "email", "roles"), invalidFields(validator.validate(body)));
    }

    @Test
    void createUser_rejectsTooLongNameAndEmail() {
        CreateUser body = new CreateUser(
                "a".repeat(121),
                "a".repeat(250) + "@x.com",
                Set.of(Role.values()[0]),
                Set.of());

        assertEquals(Set.of("name", "email"), invalidFields(validator.validate(body)));
    }

    @Test
    void createUser_rejectsNullAndNonPositiveEventIds() {
        assertEquals(Set.of("eventIds"), invalidFields(validator.validate(
                new CreateUser("Ana", "ana@example.com", Set.of(Role.values()[0]), null))));
        assertTrue(invalidFields(validator.validate(
                new CreateUser("Ana", "ana@example.com", Set.of(Role.values()[0]), Set.of(0L))))
                .stream().anyMatch(f -> f.startsWith("eventIds")));
    }

    @Test
    void updateUser_validBodyHasNoViolations() {
        UpdateUser body = new UpdateUser("Ana", true, Set.of(Role.values()[0]), Set.of(1L));

        assertTrue(validator.validate(body).isEmpty());
    }

    @Test
    void updateUser_rejectsBlankNameEmptyRolesAndNullEventIds() {
        UpdateUser body = new UpdateUser("", false, Set.of(), null);

        assertEquals(Set.of("name", "roles", "eventIds"), invalidFields(validator.validate(body)));
    }

    @Test
    void records_exposeTheirComponents() {
        Set<Role> roles = Set.of(Role.values()[0]);
        CreateUser create = new CreateUser("Ana", "ana@example.com", roles, Set.of(1L));
        UpdateUser update = new UpdateUser("Ana", true, roles, Set.of(1L));

        assertEquals("Ana", create.name());
        assertEquals("ana@example.com", create.email());
        assertEquals(roles, create.roles());
        assertEquals(Set.of(1L), create.eventIds());
        assertEquals("Ana", update.name());
        assertTrue(update.enabled());
        assertEquals(roles, update.roles());
        assertEquals(Set.of(1L), update.eventIds());
    }

    private static <T> Set<String> invalidFields(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}