package com.cocacola.eventintelligence.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Notas de adaptación:
 * - Se asume que Role es un enum con al menos 2 valores (se usan Role.values()[0] y [1]);
 *   el orden natural del enum define cuál es el rol "primero" en sorted().
 * - Se construye Account con el constructor visto en AccountStore:
 *   (id, name, email, passwordHash, enabled, authVersion, roles, eventIds).
 */
@ExtendWith(MockitoExtension.class)
class SessionAccountFilterTest {

    private static final Long USER_ID = 5L;
    private static final int AUTH_VERSION = 3;

    private static final Role FIRST_ROLE = Role.values()[0];
    private static final Role SECOND_ROLE = Role.values()[1];

    @Mock
    private AccountStore accounts;

    private SessionAccountFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new SessionAccountFilter(accounts);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeRoleConstant_isStable() {
        assertEquals("active-role", SessionAccountFilter.ACTIVE_ROLE);
    }

    // ---- sin cuenta en el contexto: el filtro no hace nada ----

    @Test
    void doFilter_withoutAuthentication_justContinuesTheChain() throws Exception {
        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getSession(false));
        verifyNoInteractions(accounts);
    }

    @Test
    void doFilter_withNonAccountPrincipal_leavesAuthenticationUntouched() throws Exception {
        Authentication other = UsernamePasswordAuthenticationToken.authenticated(
                "usuario-externo", null, List.of(new SimpleGrantedAuthority("ROLE_OTRO")));
        SecurityContextHolder.getContext().setAuthentication(other);

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertSame(other, SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(accounts);
    }

    // ---- cuenta vigente: se refresca la autenticación y el rol activo ----

    @Test
    void doFilter_withValidAccountAndNoSavedRole_usesFirstSortedRole() throws Exception {
        authenticateAs(account(true, AUTH_VERSION, SECOND_ROLE, FIRST_ROLE));
        when(accounts.find(USER_ID)).thenReturn(Optional.of(account(true, AUTH_VERSION, SECOND_ROLE, FIRST_ROLE)));

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(FIRST_ROLE, request.getSession(false).getAttribute(SessionAccountFilter.ACTIVE_ROLE));
        assertAuthenticatedWithRole(FIRST_ROLE);
    }

    @Test
    void doFilter_keepsSavedRoleWhenAccountStillHasIt() throws Exception {
        authenticateAs(account(true, AUTH_VERSION, FIRST_ROLE, SECOND_ROLE));
        when(accounts.find(USER_ID)).thenReturn(Optional.of(account(true, AUTH_VERSION, FIRST_ROLE, SECOND_ROLE)));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAccountFilter.ACTIVE_ROLE, SECOND_ROLE);
        request.setSession(session);

        filter.doFilter(request, response, chain);

        assertEquals(SECOND_ROLE, session.getAttribute(SessionAccountFilter.ACTIVE_ROLE));
        assertAuthenticatedWithRole(SECOND_ROLE);
    }

    @Test
    void doFilter_fallsBackToFirstRoleWhenSavedRoleIsNoLongerAssigned() throws Exception {
        authenticateAs(account(true, AUTH_VERSION, FIRST_ROLE, SECOND_ROLE));
        when(accounts.find(USER_ID)).thenReturn(Optional.of(account(true, AUTH_VERSION, FIRST_ROLE)));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAccountFilter.ACTIVE_ROLE, SECOND_ROLE);
        request.setSession(session);

        filter.doFilter(request, response, chain);

        assertEquals(FIRST_ROLE, session.getAttribute(SessionAccountFilter.ACTIVE_ROLE));
        assertAuthenticatedWithRole(FIRST_ROLE);
    }

    @Test
    void doFilter_ignoresSavedValueThatIsNotARole() throws Exception {
        authenticateAs(account(true, AUTH_VERSION, FIRST_ROLE, SECOND_ROLE));
        when(accounts.find(USER_ID)).thenReturn(Optional.of(account(true, AUTH_VERSION, FIRST_ROLE, SECOND_ROLE)));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAccountFilter.ACTIVE_ROLE, "no-es-un-rol");
        request.setSession(session);

        filter.doFilter(request, response, chain);

        assertEquals(FIRST_ROLE, session.getAttribute(SessionAccountFilter.ACTIVE_ROLE));
        assertAuthenticatedWithRole(FIRST_ROLE);
    }

    @Test
    void doFilter_replacesPrincipalWithFreshAccountFromStore() throws Exception {
        authenticateAs(account(true, AUTH_VERSION, FIRST_ROLE));
        when(accounts.find(USER_ID)).thenReturn(Optional.of(account(true, AUTH_VERSION, FIRST_ROLE)));

        filter.doFilter(request, response, chain);

        verify(accounts).find(USER_ID);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertInstanceOf(Account.class, authentication.getPrincipal());
        assertEquals(USER_ID, ((Account) authentication.getPrincipal()).id());
        assertNull(authentication.getCredentials());
    }

    // ---- cuenta inválida: se limpia el contexto y se invalida la sesión ----

    @Test
    void doFilter_clearsContextAndInvalidatesSessionWhenAccountNoLongerExists() throws Exception {
        authenticateAs(account(true, AUTH_VERSION, FIRST_ROLE));
        when(accounts.find(USER_ID)).thenReturn(Optional.empty());
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(session.isInvalid());
    }

    @Test
    void doFilter_clearsContextWhenAccountIsDisabled() throws Exception {
        authenticateAs(account(true, AUTH_VERSION, FIRST_ROLE));
        when(accounts.find(USER_ID)).thenReturn(Optional.of(account(false, AUTH_VERSION, FIRST_ROLE)));
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(session.isInvalid());
    }

    @Test
    void doFilter_clearsContextWhenAuthVersionChanged() throws Exception {
        authenticateAs(account(true, AUTH_VERSION, FIRST_ROLE));
        when(accounts.find(USER_ID)).thenReturn(Optional.of(account(true, AUTH_VERSION + 1, FIRST_ROLE)));
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(session.isInvalid());
        assertNotNull(chain.getRequest());
    }

    @Test
    void doFilter_clearsContextWithoutFailingWhenThereIsNoSession() throws Exception {
        authenticateAs(account(true, AUTH_VERSION, FIRST_ROLE));
        when(accounts.find(USER_ID)).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getSession(false));
        assertNotNull(chain.getRequest());
    }

    // ---- helpers ----

    private static Account account(boolean enabled, int authVersion, Role... roles) {
        return new Account(
                USER_ID, "Ana", "ana@example.com", "hash", enabled, authVersion,
                new LinkedHashSet<>(List.of(roles)), new LinkedHashSet<Long>());
    }

    private static void authenticateAs(Account account) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        account, null, List.of(new SimpleGrantedAuthority("ROLE_" + FIRST_ROLE.name()))));
    }

    private static void assertAuthenticatedWithRole(Role expectedRole) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals(
                List.of(new SimpleGrantedAuthority("ROLE_" + expectedRole.name())),
                List.copyOf(authentication.getAuthorities()));
    }
}