package com.cocacola.eventintelligence.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cocacola.eventintelligence.exception.BusinessRuleException;
import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountManagementTest {

    private static final long ACTOR_ID = 1L;
    private static final long USER_ID = 20L;
    private static final long OTHER_ADMIN_ID = 21L;
    private static final long EVENT_A = 100L;
    private static final long EVENT_B = 101L;
    private static final String NAME = " Ana Admin ";
    private static final String NORMALIZED_NAME = "Ana Admin";
    private static final String EMAIL = " ANA@EXAMPLE.COM ";
    private static final String NORMALIZED_EMAIL = "ana@example.com";
    private static final String VALID_PASSWORD = "secure-password";

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private AccountStore accounts;
    @Mock
    private PasswordEncoder passwords;

    private AccountManagement management;

    @BeforeEach
    void setUp() {
        management = new AccountManagement(jdbc, accounts, passwords);
    }

    @Test
    @DisplayName("view expone roles, permisos y eventos de una cuenta multirol")
    void viewExposesAccountRolesPermissionsAndEvents() {
        // Arrange
        Account account = account(USER_ID, "Ana", "ana@example.com", "hash", true,
                Set.of(Role.ADMINISTRADOR, Role.MARKETING), Set.of(EVENT_A, EVENT_B));

        // Act
        Map<String, Object> view = management.view(account, Role.MARKETING);

        // Assert
        assertEquals(USER_ID, view.get("id"));
        assertEquals("Ana", view.get("name"));
        assertEquals("ana@example.com", view.get("email"));
        assertEquals(account.roles(), view.get("roles"));
        assertEquals(Role.MARKETING, view.get("activeRole"));
        assertEquals(Role.MARKETING.permissions(), view.get("permissions"));
        assertEquals(account.eventIds(), view.get("eventIds"));
        verifyNoInteractions(jdbc, accounts, passwords);
    }

    @Test
    @DisplayName("adminView informa si una cuenta esta activada")
    void adminViewReportsActivationFromPasswordHash() {
        // Arrange
        Account activated = account(USER_ID, "Ana", "ana@example.com", "hash", true,
                Set.of(Role.ORGANIZADOR), Set.of(EVENT_A));
        Account pending = account(USER_ID, "Ana", "ana@example.com", null, true,
                Set.of(Role.ORGANIZADOR), Set.of(EVENT_A));

        // Act
        Map<String, Object> activatedView = management.adminView(activated);
        Map<String, Object> pendingView = management.adminView(pending);

        // Assert
        assertEquals(true, activatedView.get("activated"));
        assertEquals(false, pendingView.get("activated"));
        assertEquals(true, activatedView.get("enabled"));
        assertEquals(Set.of(EVENT_A), pendingView.get("eventIds"));
        verifyNoInteractions(jdbc, accounts, passwords);
    }

    @Test
    @DisplayName("run consulta el estado de configuracion para store vacio y no vacio")
    void runChecksWhetherSetupIsRequired() {
        // Arrange
        ApplicationArguments arguments = org.mockito.Mockito.mock(ApplicationArguments.class);
        when(accounts.isEmpty()).thenReturn(true, false);

        // Act
        management.run(arguments);
        management.run(arguments);

        // Assert
        verify(accounts, org.mockito.Mockito.times(2)).isEmpty();
        verifyNoInteractions(jdbc, passwords);
    }

    @Test
    @DisplayName("list devuelve vacio cuando no existen cuentas")
    void listReturnsEmptyWhenNoAccountsExist() {
        // Arrange
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(List.of());

        // Act
        List<Map<String, Object>> result = management.list();

        // Assert
        assertTrue(result.isEmpty());
        verify(jdbc).queryForList(anyString(), eq(Long.class));
        verifyNoInteractions(accounts, passwords);
    }

    @Test
    @DisplayName("list resuelve cuentas en el orden de sus ids")
    void listResolvesAccountsInIdOrder() {
        // Arrange
        Account first = account(USER_ID, "Ana", "ana@example.com", "hash", true,
                Set.of(Role.ADMINISTRADOR), Set.of());
        Account second = account(OTHER_ADMIN_ID, "Beto", "beto@example.com", null, false,
                Set.of(Role.MARKETING), Set.of());
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(USER_ID, OTHER_ADMIN_ID));
        when(accounts.find(USER_ID)).thenReturn(Optional.of(first));
        when(accounts.find(OTHER_ADMIN_ID)).thenReturn(Optional.of(second));

        // Act
        List<Map<String, Object>> result = management.list();

        // Assert
        assertEquals(List.of(management.adminView(first), management.adminView(second)), result);
        InOrder orderedAccounts = inOrder(accounts);
        orderedAccounts.verify(accounts).find(USER_ID);
        orderedAccounts.verify(accounts).find(OTHER_ADMIN_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("list falla cuando un id listado no puede resolverse")
    void listFailsWhenListedAccountCannotBeResolved() {
        // Arrange
        when(jdbc.queryForList(anyString(), eq(Long.class))).thenReturn(List.of(USER_ID));
        when(accounts.find(USER_ID)).thenReturn(Optional.empty());

        // Act / Assert
        assertThrows(NoSuchElementException.class, () -> management.list());
        verify(accounts).find(USER_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("setup deniega cuando ya existe una cuenta")
    void setupRejectsWhenAccountsAlreadyExist() {
        // Arrange
        when(accounts.isEmpty()).thenReturn(false);

        // Act / Assert
        assertThrows(BusinessRuleException.class,
                () -> management.setup("any-code", NAME, EMAIL, VALID_PASSWORD));
        verify(jdbc).execute(anyString());
        verify(accounts).isEmpty();
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("setup deniega un codigo incorrecto cuando el store esta vacio")
    void setupRejectsIncorrectCodeWhenStoreIsEmpty() {
        // Arrange
        when(accounts.isEmpty()).thenReturn(true);

        // Act / Assert
        assertThrows(BusinessRuleException.class,
                () -> management.setup("incorrect-code", NAME, EMAIL, VALID_PASSWORD));
        verify(jdbc).execute(anyString());
        verify(accounts).isEmpty();
        verifyNoInteractions(passwords);
    }

    @ParameterizedTest(name = "setup rechaza contrasena invalida: {0}")
    @MethodSource("invalidPasswords")
    void setupRejectsInvalidPasswords(String password) {
        // Arrange
        when(accounts.isEmpty()).thenReturn(true);

        // Act / Assert
        assertThrows(BusinessRuleException.class,
                () -> management.setup(setupToken(), NAME, EMAIL, password));
        verify(jdbc).execute(anyString());
        verify(accounts).isEmpty();
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("setup crea y audita al administrador inicial con datos normalizados")
    void setupCreatesInitialAdministrator() {
        // Arrange
        when(accounts.isEmpty()).thenReturn(true);
        when(passwords.encode(VALID_PASSWORD)).thenReturn("encoded-password");
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(NORMALIZED_NAME), eq(NORMALIZED_EMAIL),
                eq("encoded-password"))).thenReturn(USER_ID);

        // Act
        management.setup(setupToken(), NAME, EMAIL, VALID_PASSWORD);

        // Assert
        InOrder ordered = inOrder(jdbc, accounts, passwords);
        ordered.verify(jdbc).execute(anyString());
        ordered.verify(accounts).isEmpty();
        ordered.verify(passwords).encode(VALID_PASSWORD);
        ordered.verify(jdbc).queryForObject(anyString(), eq(Long.class), eq(NORMALIZED_NAME), eq(NORMALIZED_EMAIL),
                eq("encoded-password"));
        ordered.verify(jdbc).update(anyString(), eq(USER_ID));
        ordered.verify(accounts).audit(USER_ID, "INITIAL_ADMIN_CREATED", USER_ID);
    }

    @ParameterizedTest(name = "create rechaza roles invalidos: {0}")
    @MethodSource("invalidRoleSets")
    void createRejectsNullOrEmptyRoles(Set<Role> roles) {
        // Act / Assert
        assertThrows(BusinessRuleException.class,
                () -> management.create(ACTOR_ID, NAME, EMAIL, roles, Set.of(EVENT_A)));
        verify(jdbc).execute(anyString());
        verifyNoInteractions(accounts, passwords);
    }

    @Test
    @DisplayName("create asigna roles y eventos al organizador y emite token")
    void createAssignsOrganizerRolesEventsAndActivationToken() {
        // Arrange
        Set<Role> roles = Set.of(Role.ORGANIZADOR, Role.MARKETING);
        Set<Long> events = Set.of(EVENT_A, EVENT_B);
        Account created = account(USER_ID, NORMALIZED_NAME, NORMALIZED_EMAIL, null, true, roles, events);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(NORMALIZED_NAME), eq(NORMALIZED_EMAIL)))
                .thenReturn(USER_ID);
        when(accounts.find(USER_ID)).thenReturn(Optional.of(created));

        // Act
        Map<String, Object> result = management.create(ACTOR_ID, NAME, EMAIL, roles, events);

        // Assert
        assertEquals(management.adminView(created), result.get("user"));
        String rawToken = (String) result.get("activationToken");
        assertTrue(rawToken.matches("[0-9a-f]{64}"));
        verify(jdbc).update(startsWith("DELETE FROM app_user_roles"), eq(USER_ID));
        verify(jdbc).update(startsWith("INSERT INTO app_user_roles"), eq(USER_ID), eq(Role.ORGANIZADOR.name()));
        verify(jdbc).update(startsWith("INSERT INTO app_user_roles"), eq(USER_ID), eq(Role.MARKETING.name()));
        verify(jdbc).update(startsWith("DELETE FROM app_user_events"), eq(USER_ID));
        verify(jdbc).update(startsWith("INSERT INTO app_user_events"), eq(USER_ID), eq(EVENT_A));
        verify(jdbc).update(startsWith("INSERT INTO app_user_events"), eq(USER_ID), eq(EVENT_B));
        verify(accounts).audit(ACTOR_ID, "USER_CREATED", USER_ID);
        assertInsertedTokenHashMatches(rawToken, USER_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("create ignora eventos cuando los roles no incluyen organizador")
    void createDoesNotAssignEventsWithoutOrganizerRole() {
        // Arrange
        Set<Role> roles = Set.of(Role.MARKETING);
        Account created = account(USER_ID, NORMALIZED_NAME, NORMALIZED_EMAIL, null, true, roles, Set.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(NORMALIZED_NAME), eq(NORMALIZED_EMAIL)))
                .thenReturn(USER_ID);
        when(accounts.find(USER_ID)).thenReturn(Optional.of(created));

        // Act
        management.create(ACTOR_ID, NAME, EMAIL, roles, Set.of(EVENT_A));

        // Assert
        verify(jdbc).update(startsWith("DELETE FROM app_user_events"), eq(USER_ID));
        verify(jdbc, never()).update(startsWith("INSERT INTO app_user_events"), eq(USER_ID), eq(EVENT_A));
        verify(accounts).audit(ACTOR_ID, "USER_CREATED", USER_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("create falla si la cuenta no se encuentra despues de insertarla")
    void createFailsWhenInsertedAccountCannotBeFound() {
        // Arrange
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(NORMALIZED_NAME), eq(NORMALIZED_EMAIL)))
                .thenReturn(USER_ID);
        when(accounts.find(USER_ID)).thenReturn(Optional.empty());

        // Act / Assert
        assertThrows(NoSuchElementException.class,
                () -> management.create(ACTOR_ID, NAME, EMAIL, Set.of(Role.MARKETING), Set.of()));
        verify(accounts).audit(ACTOR_ID, "USER_CREATED", USER_ID);
        verify(jdbc, never()).update(startsWith("INSERT INTO app_activation_tokens"), anyString(), eq(USER_ID));
        verifyNoInteractions(passwords);
    }

    @ParameterizedTest(name = "update rechaza roles invalidos: {0}")
    @MethodSource("invalidRoleSets")
    void updateRejectsNullOrEmptyRoles(Set<Role> roles) {
        // Act / Assert
        assertThrows(BusinessRuleException.class,
                () -> management.update(ACTOR_ID, USER_ID, NAME, true, roles, Set.of(EVENT_A)));
        verify(jdbc).execute(anyString());
        verifyNoInteractions(accounts, passwords);
    }

    @Test
    @DisplayName("update informa usuario inexistente")
    void updateRejectsUnknownUser() {
        // Arrange
        when(accounts.find(USER_ID)).thenReturn(Optional.empty());

        // Act / Assert
        assertThrows(ResourceNotFoundException.class,
                () -> management.update(ACTOR_ID, USER_ID, NAME, true, Set.of(Role.MARKETING), Set.of()));
        verify(jdbc).execute(anyString());
        verify(accounts).find(USER_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("update de cuenta no administradora no consulta conteo de administradores")
    void updateNonAdministratorSkipsAdministratorCount() {
        // Arrange
        Account existing = account(USER_ID, "Ana", "ana@example.com", "hash", true,
                Set.of(Role.MARKETING), Set.of());
        Account updated = account(USER_ID, NORMALIZED_NAME, "ana@example.com", "hash", true,
                Set.of(Role.MARKETING), Set.of());
        when(accounts.find(USER_ID)).thenReturn(Optional.of(existing), Optional.of(updated));

        // Act
        Map<String, Object> result = management.update(ACTOR_ID, USER_ID, NAME, true,
                Set.of(Role.MARKETING), Set.of(EVENT_A));

        // Assert
        assertEquals(management.adminView(updated), result);
        verify(jdbc, never()).queryForObject(startsWith("SELECT COUNT(*)"), eq(Long.class));
        verify(jdbc).update(startsWith("UPDATE app_users"), eq(NORMALIZED_NAME), eq(true), eq(USER_ID));
        verify(accounts).audit(ACTOR_ID, "USER_ACCESS_UPDATED", USER_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("update de admin que conserva rol y estado no consulta conteo")
    void updateAdministratorKeepingRoleAndStateSkipsAdministratorCount() {
        // Arrange
        Account existing = account(USER_ID, "Ana", "ana@example.com", "hash", true,
                Set.of(Role.ADMINISTRADOR), Set.of());
        Account updated = account(USER_ID, NORMALIZED_NAME, "ana@example.com", "hash", true,
                Set.of(Role.ADMINISTRADOR), Set.of());
        when(accounts.find(USER_ID)).thenReturn(Optional.of(existing), Optional.of(updated));

        // Act
        management.update(OTHER_ADMIN_ID, USER_ID, NAME, true, Set.of(Role.ADMINISTRADOR), Set.of());

        // Assert
        verify(jdbc, never()).queryForObject(startsWith("SELECT COUNT(*)"), eq(Long.class));
        verify(accounts).audit(OTHER_ADMIN_ID, "USER_ACCESS_UPDATED", USER_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("update no deshabilita al ultimo administrador activo")
    void updateRejectsDisablingLastAdministrator() {
        // Arrange
        when(accounts.find(USER_ID)).thenReturn(Optional.of(activeAdministrator()));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*)"), eq(Long.class))).thenReturn(1L);

        // Act / Assert
        assertThrows(BusinessRuleException.class,
                () -> management.update(OTHER_ADMIN_ID, USER_ID, NAME, false, Set.of(Role.ADMINISTRADOR), Set.of()));
        verify(jdbc, never()).update(startsWith("UPDATE app_users"), anyString(), eq(false), eq(USER_ID));
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("update no retira rol al ultimo administrador activo")
    void updateRejectsRemovingLastAdministratorRole() {
        // Arrange
        when(accounts.find(USER_ID)).thenReturn(Optional.of(activeAdministrator()));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*)"), eq(Long.class))).thenReturn(1L);

        // Act / Assert
        assertThrows(BusinessRuleException.class,
                () -> management.update(OTHER_ADMIN_ID, USER_ID, NAME, true, Set.of(Role.MARKETING), Set.of()));
        verify(jdbc, never()).update(startsWith("UPDATE app_users"), anyString(), eq(true), eq(USER_ID));
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("update impide que un administrador se retire su propio acceso")
    void updateRejectsActorRemovingOwnAdministratorAccess() {
        // Arrange
        when(accounts.find(USER_ID)).thenReturn(Optional.of(activeAdministrator()));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*)"), eq(Long.class))).thenReturn(2L);

        // Act / Assert
        assertThrows(BusinessRuleException.class,
                () -> management.update(USER_ID, USER_ID, NAME, false, Set.of(Role.ADMINISTRADOR), Set.of()));
        verify(jdbc, never()).update(startsWith("UPDATE app_users"), anyString(), eq(false), eq(USER_ID));
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("update permite a otro admin retirar acceso cuando quedan dos administradores")
    void updateAllowsOtherAdministratorToRemoveAccessWhenAnotherRemains() {
        // Arrange
        Account existing = activeAdministrator();
        Account updated = account(USER_ID, NORMALIZED_NAME, "ana@example.com", "hash", false,
                Set.of(Role.MARKETING), Set.of());
        when(accounts.find(USER_ID)).thenReturn(Optional.of(existing), Optional.of(updated));
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*)"), eq(Long.class))).thenReturn(2L);

        // Act
        Map<String, Object> result = management.update(OTHER_ADMIN_ID, USER_ID, NAME, false,
                Set.of(Role.MARKETING), Set.of());

        // Assert
        assertEquals(management.adminView(updated), result);
        verify(jdbc).update(startsWith("UPDATE app_users"), eq(NORMALIZED_NAME), eq(false), eq(USER_ID));
        verify(accounts).audit(OTHER_ADMIN_ID, "USER_ACCESS_UPDATED", USER_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("update de organizador reemplaza roles y eventos e incrementa version de autenticacion")
    void updateOrganizerReplacesRolesEventsAndAuthenticationVersion() {
        // Arrange
        Account existing = account(USER_ID, "Ana", "ana@example.com", "hash", true,
                Set.of(Role.ORGANIZADOR), Set.of(EVENT_A));
        Account updated = account(USER_ID, NORMALIZED_NAME, "ana@example.com", "hash", true,
                Set.of(Role.ORGANIZADOR, Role.MARKETING), Set.of(EVENT_A, EVENT_B));
        when(accounts.find(USER_ID)).thenReturn(Optional.of(existing), Optional.of(updated));

        // Act
        management.update(ACTOR_ID, USER_ID, NAME, true, updated.roles(), updated.eventIds());

        // Assert
        verify(jdbc).update(startsWith("UPDATE app_users SET name=?, enabled=?, auth_version=auth_version+1"),
                eq(NORMALIZED_NAME), eq(true), eq(USER_ID));
        verify(jdbc).update(startsWith("DELETE FROM app_user_roles"), eq(USER_ID));
        verify(jdbc).update(startsWith("INSERT INTO app_user_roles"), eq(USER_ID), eq(Role.ORGANIZADOR.name()));
        verify(jdbc).update(startsWith("INSERT INTO app_user_roles"), eq(USER_ID), eq(Role.MARKETING.name()));
        verify(jdbc).update(startsWith("DELETE FROM app_user_events"), eq(USER_ID));
        verify(jdbc).update(startsWith("INSERT INTO app_user_events"), eq(USER_ID), eq(EVENT_A));
        verify(jdbc).update(startsWith("INSERT INTO app_user_events"), eq(USER_ID), eq(EVENT_B));
        verify(accounts).audit(ACTOR_ID, "USER_ACCESS_UPDATED", USER_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("invite informa usuario inexistente")
    void inviteRejectsUnknownUser() {
        // Arrange
        when(accounts.find(USER_ID)).thenReturn(Optional.empty());

        // Act / Assert
        assertThrows(ResourceNotFoundException.class, () -> management.invite(ACTOR_ID, USER_ID));
        verify(jdbc).execute(anyString());
        verify(accounts).find(USER_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("invite deniega una cuenta ya activada")
    void inviteRejectsActivatedAccount() {
        // Arrange
        when(accounts.find(USER_ID)).thenReturn(Optional.of(activeAdministrator()));

        // Act / Assert
        assertThrows(BusinessRuleException.class, () -> management.invite(ACTOR_ID, USER_ID));
        verify(accounts, never()).audit(ACTOR_ID, "ACTIVATION_LINK_CREATED", USER_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("invite deniega una cuenta pendiente deshabilitada")
    void inviteRejectsDisabledPendingAccount() {
        // Arrange
        when(accounts.find(USER_ID)).thenReturn(Optional.of(account(USER_ID, "Ana", "ana@example.com", null, false,
                Set.of(Role.MARKETING), Set.of())));

        // Act / Assert
        assertThrows(BusinessRuleException.class, () -> management.invite(ACTOR_ID, USER_ID));
        verify(accounts, never()).audit(ACTOR_ID, "ACTIVATION_LINK_CREATED", USER_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("invite audita y renueva token de una cuenta pendiente habilitada")
    void inviteAuditsAndIssuesTokenForEnabledPendingAccount() {
        // Arrange
        Account pending = account(USER_ID, "Ana", "ana@example.com", null, true,
                Set.of(Role.MARKETING), Set.of());
        when(accounts.find(USER_ID)).thenReturn(Optional.of(pending));

        // Act
        String rawToken = management.invite(ACTOR_ID, USER_ID);

        // Assert
        assertTrue(rawToken.matches("[0-9a-f]{64}"));
        InOrder ordered = inOrder(jdbc, accounts);
        ordered.verify(jdbc).execute(anyString());
        ordered.verify(accounts).find(USER_ID);
        ordered.verify(accounts).audit(ACTOR_ID, "ACTIVATION_LINK_CREATED", USER_ID);
        ordered.verify(jdbc).update(startsWith("UPDATE app_activation_tokens"), eq(USER_ID));
        assertInsertedTokenHashMatches(rawToken, USER_ID);
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("activate rechaza contrasena invalida antes de consultar token")
    void activateRejectsInvalidPasswordBeforeTokenLookup() {
        // Act / Assert
        assertThrows(BusinessRuleException.class, () -> management.activate("activation-code", "short"));
        verify(jdbc).execute(anyString());
        verifyNoInteractions(accounts, passwords);
    }

    @ParameterizedTest(name = "activate deniega token {0}")
    @ValueSource(strings = {"missing", "expired", "used"})
    void activateRejectsUnavailableToken(String code) {
        // Arrange
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(sha256(code)))).thenReturn(List.of());

        // Act / Assert
        assertThrows(BusinessRuleException.class, () -> management.activate(code, VALID_PASSWORD));
        verify(jdbc).execute(anyString());
        verify(jdbc).queryForList(anyString(), eq(Long.class), eq(sha256(code)));
        verifyNoInteractions(accounts, passwords);
    }

    @Test
    @DisplayName("activate informa usuario inexistente para un token valido")
    void activateRejectsMissingAccountForValidToken() {
        // Arrange
        String code = "valid-token";
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(sha256(code)))).thenReturn(List.of(USER_ID));
        when(accounts.find(USER_ID)).thenReturn(Optional.empty());

        // Act / Assert
        assertThrows(ResourceNotFoundException.class, () -> management.activate(code, VALID_PASSWORD));
        InOrder ordered = inOrder(jdbc, accounts);
        ordered.verify(jdbc).execute(anyString());
        ordered.verify(jdbc).queryForList(anyString(), eq(Long.class), eq(sha256(code)));
        ordered.verify(jdbc).execute(anyString());
        ordered.verify(accounts).find(USER_ID);
        verifyNoInteractions(passwords);
    }

    @ParameterizedTest(name = "activate deniega cuenta no disponible: {0}")
    @MethodSource("unavailableAccounts")
    void activateRejectsDisabledOrAlreadyActivatedAccount(Account account) {
        // Arrange
        String code = "valid-token";
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(sha256(code)))).thenReturn(List.of(USER_ID));
        when(accounts.find(USER_ID)).thenReturn(Optional.of(account));

        // Act / Assert
        assertThrows(BusinessRuleException.class, () -> management.activate(code, VALID_PASSWORD));
        verify(jdbc, never()).update(startsWith("UPDATE app_users SET password_hash"), anyString(), eq(USER_ID));
        verifyNoInteractions(passwords);
    }

    @Test
    @DisplayName("activate actualiza cuenta, invalida tokens y audita en orden")
    void activateUpdatesAccountInvalidatesTokensAndAuditsInOrder() {
        // Arrange
        String code = "valid-token";
        Account pending = account(USER_ID, "Ana", "ana@example.com", null, true,
                Set.of(Role.MARKETING), Set.of());
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(sha256(code)))).thenReturn(List.of(USER_ID));
        when(accounts.find(USER_ID)).thenReturn(Optional.of(pending));
        when(passwords.encode(VALID_PASSWORD)).thenReturn("encoded-password");

        // Act
        management.activate(code, VALID_PASSWORD);

        // Assert
        InOrder ordered = inOrder(jdbc, accounts, passwords);
        ordered.verify(jdbc).execute(anyString());
        ordered.verify(jdbc).queryForList(anyString(), eq(Long.class), eq(sha256(code)));
        ordered.verify(jdbc).execute(anyString());
        ordered.verify(accounts).find(USER_ID);
        ordered.verify(passwords).encode(VALID_PASSWORD);
        ordered.verify(jdbc).update(startsWith("UPDATE app_users SET password_hash"), eq("encoded-password"), eq(USER_ID));
        ordered.verify(jdbc).update(startsWith("UPDATE app_activation_tokens"), eq(USER_ID));
        ordered.verify(accounts).audit(USER_ID, "ACCOUNT_ACTIVATED", USER_ID);
    }

    @Test
    @DisplayName("token genera 64 caracteres hexadecimales")
    void tokenHasExpectedHexFormat() {
        // Act
        String token = AccountManagement.token();

        // Assert
        assertTrue(token.matches("[0-9a-f]{64}"));
    }

    private static Stream<Arguments> invalidPasswords() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of("short"),
                Arguments.of("a".repeat(73)));
    }

    private static Stream<Arguments> invalidRoleSets() {
        return Stream.of(Arguments.of((Set<Role>) null), Arguments.of(Set.<Role>of()));
    }

    private static Stream<Arguments> unavailableAccounts() {
        return Stream.of(
                Arguments.of(account(USER_ID, "Ana", "ana@example.com", null, false, Set.of(Role.MARKETING), Set.of())),
                Arguments.of(account(USER_ID, "Ana", "ana@example.com", "hash", true, Set.of(Role.MARKETING), Set.of())));
    }

    private static Account account(Long id, String name, String email, String passwordHash, boolean enabled,
                                   Set<Role> roles, Set<Long> events) {
        return new Account(id, name, email, passwordHash, enabled, 0, roles, events);
    }

    private static Account activeAdministrator() {
        return account(USER_ID, "Ana", "ana@example.com", "hash", true, Set.of(Role.ADMINISTRADOR), Set.of());
    }

    private String setupToken() {
        try {
            Field field = AccountManagement.class.getDeclaredField("setupToken");
            field.setAccessible(true);
            return (String) field.get(management);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("No se pudo obtener setupToken para la prueba", exception);
        }
    }

    private void assertInsertedTokenHashMatches(String rawToken, Long userId) {
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(startsWith("INSERT INTO app_activation_tokens"), hashCaptor.capture(), eq(userId));
        assertEquals(sha256(rawToken), hashCaptor.getValue());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError("SHA-256 debe estar disponible", exception);
        }
    }
}
