package com.cocacola.eventintelligence.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * El JdbcTemplate se mockea. Para ejercitar los RowMapper internos de find(...),
 * los stubs invocan el RowMapper recibido con un ResultSet simulado.
 *
 * Notas de adaptación:
 * - Se asume que Role es un enum (se usa Role.values()[0]).
 * - Se asume que Account es un record (o define equals) con el constructor
 *   (id, name, email, passwordHash, enabled, authVersion, roles, eventIds).
 *   Si no define equals, cambia assertEquals(expected, actual) por aserciones sobre sus accesores.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"unchecked", "rawtypes"})
class AccountStoreTest {

    private static final String ACCOUNT_SQL = "SELECT * FROM app_users WHERE id = ?";
    private static final String ROLES_SQL = "SELECT role FROM app_user_roles WHERE user_id = ? ORDER BY role";
    private static final String EVENTS_SQL = "SELECT event_id FROM app_user_events WHERE user_id = ?";
    private static final String ID_BY_EMAIL_SQL = "SELECT id FROM app_users WHERE email = ?";
    private static final String COUNT_SQL = "SELECT COUNT(*) FROM app_users";
    private static final String AUDIT_SQL =
            "INSERT INTO app_access_audit(actor_id, action, target_id) VALUES (?,?,?)";

    private static final Long USER_ID = 7L;

    @Mock
    private JdbcTemplate jdbc;

    private AccountStore store;

    @BeforeEach
    void setUp() {
        store = new AccountStore(jdbc);
    }

    // ---- constructor ----

    @Test
    void accountStore_isAUserDetailsService() {
        assertTrue(store instanceof UserDetailsService);
    }

    // ---- find ----

    @Test
    void find_returnsAccountMappedFromRowRolesAndEvents() throws SQLException {
        Role role = Role.values()[0];
        stubAccountRow(USER_ID, List.of(role.name()), List.of(10L, 11L));

        Optional<Account> result = store.find(USER_ID);

        assertTrue(result.isPresent());
        Account expected = new Account(
                USER_ID, "Ana", "ana@example.com", "hash", true, 3,
                new LinkedHashSet<>(List.of(role)),
                new LinkedHashSet<>(List.of(10L, 11L)));
        assertEquals(USER_ID, result.get().id());
        assertEquals(expected, result.get());
        verify(jdbc).query(eq(ACCOUNT_SQL), any(RowMapper.class), eq(USER_ID));
        verify(jdbc).query(eq(ROLES_SQL), any(RowMapper.class), eq(USER_ID));
        verify(jdbc).queryForList(EVENTS_SQL, Long.class, USER_ID);
    }

    @Test
    void find_mapsAccountWithoutRolesNorEvents() throws SQLException {
        stubAccountRow(USER_ID, List.of(), List.of());

        Optional<Account> result = store.find(USER_ID);

        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get().id());
        assertEquals(
                new Account(USER_ID, "Ana", "ana@example.com", "hash", true, 3,
                        new LinkedHashSet<Role>(), new LinkedHashSet<Long>()),
                result.get());
    }

    @Test
    void find_returnsEmptyWhenUserDoesNotExist() {
        when(jdbc.query(eq(ACCOUNT_SQL), any(RowMapper.class), eq(99L))).thenReturn(List.of());

        Optional<Account> result = store.find(99L);

        assertFalse(result.isPresent());
        verify(jdbc).query(eq(ACCOUNT_SQL), any(RowMapper.class), eq(99L));
        verifyNoMoreInteractions(jdbc);
    }

    // ---- loadUserByUsername ----

    @Test
    void loadUserByUsername_normalizesEmailAndReturnsAccount() throws SQLException {
        Role role = Role.values()[0];
        when(jdbc.queryForList(ID_BY_EMAIL_SQL, Long.class, "ana@example.com")).thenReturn(List.of(USER_ID));
        stubAccountRow(USER_ID, List.of(role.name()), List.of(10L));

        Account account = store.loadUserByUsername("  Ana@Example.COM  ");

        assertEquals(USER_ID, account.id());
        verify(jdbc).queryForList(ID_BY_EMAIL_SQL, Long.class, "ana@example.com");
    }

    @Test
    void loadUserByUsername_throwsWhenEmailIsNotRegistered() {
        when(jdbc.queryForList(ID_BY_EMAIL_SQL, Long.class, "nadie@example.com")).thenReturn(List.of());

        UsernameNotFoundException thrown = assertThrows(
                UsernameNotFoundException.class,
                () -> store.loadUserByUsername("nadie@example.com"));

        assertEquals("Credenciales inválidas", thrown.getMessage());
        verify(jdbc).queryForList(ID_BY_EMAIL_SQL, Long.class, "nadie@example.com");
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void loadUserByUsername_throwsWhenIdExistsButAccountCannotBeLoaded() {
        when(jdbc.queryForList(ID_BY_EMAIL_SQL, Long.class, "ana@example.com")).thenReturn(List.of(USER_ID));
        when(jdbc.query(eq(ACCOUNT_SQL), any(RowMapper.class), eq(USER_ID))).thenReturn(List.of());

        UsernameNotFoundException thrown = assertThrows(
                UsernameNotFoundException.class,
                () -> store.loadUserByUsername("ana@example.com"));

        assertEquals("Credenciales inválidas", thrown.getMessage());
    }

    // ---- isEmpty ----

    @Test
    void isEmpty_returnsTrueWhenThereAreNoUsers() {
        when(jdbc.queryForObject(COUNT_SQL, Long.class)).thenReturn(0L);

        assertTrue(store.isEmpty());
    }

    @Test
    void isEmpty_returnsFalseWhenUsersExist() {
        when(jdbc.queryForObject(COUNT_SQL, Long.class)).thenReturn(3L);

        assertFalse(store.isEmpty());
    }

    // ---- audit ----

    @Test
    void audit_insertsActorActionAndTarget() {
        store.audit(1L, "LOGIN", 2L);

        verify(jdbc).update(AUDIT_SQL, 1L, "LOGIN", 2L);
    }

    @Test
    void audit_acceptsNullTarget() {
        store.audit(1L, "LOGOUT", null);

        verify(jdbc).update(AUDIT_SQL, 1L, "LOGOUT", (Object) null);
    }

    // ---- helpers ----

    /**
     * Simula las tres consultas de find(id): la fila de app_users (ejecutando el RowMapper
     * con un ResultSet simulado), los roles y los eventos asignados.
     */
    private void stubAccountRow(Long id, List<String> roleNames, List<Long> eventIds) throws SQLException {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(id);
        when(row.getString("name")).thenReturn("Ana");
        when(row.getString("email")).thenReturn("ana@example.com");
        when(row.getString("password_hash")).thenReturn("hash");
        when(row.getBoolean("enabled")).thenReturn(true);
        when(row.getInt("auth_version")).thenReturn(3);

        List<ResultSet> roleRows = new ArrayList<>();
        for (String roleName : roleNames) {
            ResultSet roleRow = mock(ResultSet.class);
            when(roleRow.getString(1)).thenReturn(roleName);
            roleRows.add(roleRow);
        }

        when(jdbc.query(eq(ACCOUNT_SQL), any(RowMapper.class), eq(id))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(row, 0));
        });
        when(jdbc.query(eq(ROLES_SQL), any(RowMapper.class), eq(id))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            List<Object> roles = new ArrayList<>();
            for (ResultSet roleRow : roleRows) {
                roles.add(mapper.mapRow(roleRow, 0));
            }
            return roles;
        });
        when(jdbc.queryForList(EVENTS_SQL, Long.class, id)).thenReturn(eventIds);
    }
}