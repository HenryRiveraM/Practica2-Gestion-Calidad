package com.cocacola.eventintelligence.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Pruebas de SecurityConfig levantando solo la cadena de filtros de Spring Security
 * (sin controladores reales). Un endpoint "permitido" responde 404 porque no existe
 * handler; lo que se verifica es que NO responda 401 ni 403.
 *
 * Requiere la dependencia spring-security-test (scope test).
 *
 * Nota: MockMvc deja servletPath vacío por defecto, pero el filtro de throttling de
 * SecurityConfig compara request.getServletPath(). Por eso las peticiones de login
 * fijan .servletPath(...) explícitamente (en un contenedor real ya viene así).
 */
@SpringJUnitWebConfig(classes = {SecurityConfig.class, SecurityConfigTest.TestBeans.class})
class SecurityConfigTest {

    private static final Long ACCOUNT_ID = 5L;
    private static final Account ACCOUNT = mock(Account.class);

    static {
        when(ACCOUNT.id()).thenReturn(ACCOUNT_ID);
    }

    private static final String ADMIN = "ADMINISTRADOR";
    private static final String ORG = "ORGANIZADOR";
    private static final String MKT = "MARKETING";
    private static final String ANON = "ANONYMOUS";
    private static final List<String> ROLES = List.of(ADMIN, ORG, MKT);

    enum Outcome { ALLOWED, UNAUTHORIZED, FORBIDDEN }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class TestBeans {
        @Bean
        AccountStore accountStore() {
            return mock(AccountStore.class);
        }

        @Bean
        RequestThrottle requestThrottle() {
            return mock(RequestThrottle.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        /** Autentica ana@example.com / secret y entrega el Account mock como principal. */
        @Bean
        AuthenticationProvider testAuthenticationProvider() {
            return new AuthenticationProvider() {
                @Override
                public Authentication authenticate(Authentication authentication) {
                    if ("ana@example.com".equals(authentication.getName())
                            && "secret".equals(authentication.getCredentials())) {
                        return new UsernamePasswordAuthenticationToken(
                                ACCOUNT, null, List.of(new SimpleGrantedAuthority("ROLE_" + ADMIN)));
                    }
                    throw new BadCredentialsException("bad credentials");
                }

                @Override
                public boolean supports(Class<?> authentication) {
                    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
                }
            };
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AccountStore accounts;

    @Autowired
    private RequestThrottle throttle;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(accounts, throttle);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---- PasswordEncoder ----

    @Test
    void passwordEncoder_usesBCryptWithStrength12() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

        String hash = encoder.encode("secret");

        assertTrue(hash.startsWith("$2a$12$"));
        assertTrue(encoder.matches("secret", hash));
        assertFalse(encoder.matches("otra", hash));
    }

    // ---- Reglas de autorización ----

    @ParameterizedTest(name = "{0} {1} como {2} -> {3}")
    @MethodSource("accessMatrix")
    void authorizationRules(String method, String path, String role, Outcome expected) throws Exception {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), path).with(csrf());
        if (!ANON.equals(role)) {
            request.with(user("tester").roles(role));
        }

        int status = mockMvc.perform(request).andReturn().getResponse().getStatus();

        switch (expected) {
            case ALLOWED -> assertFalse(status == 401 || status == 403,
                    "Se esperaba acceso permitido pero fue " + status);
            case UNAUTHORIZED -> assertEquals(401, status);
            case FORBIDDEN -> assertEquals(403, status);
        }
    }

    static Stream<Arguments> accessMatrix() {
        List<Arguments> out = new ArrayList<>();

        // Públicos
        publicEndpoint(out, "GET", "/api/auth/csrf");
        publicEndpoint(out, "GET", "/api/auth/status");
        publicEndpoint(out, "GET", "/api/public/events");
        publicEndpoint(out, "GET", "/api/public/events/5");
        publicEndpoint(out, "POST", "/api/auth/setup");
        publicEndpoint(out, "POST", "/api/auth/activate");
        publicEndpoint(out, "POST", "/api/public/events/5/registrations");

        // Cualquier usuario autenticado
        restricted(out, "GET", "/api/auth/me", ADMIN, ORG, MKT);
        restricted(out, "POST", "/api/auth/active-role", ADMIN, ORG, MKT);

        // Solo administrador
        restricted(out, "GET", "/api/admin/users", ADMIN);
        restricted(out, "POST", "/api/admin/users", ADMIN);
        restricted(out, "PUT", "/api/admin/users/5", ADMIN);
        restricted(out, "POST", "/api/admin/users/5/activation", ADMIN);

        // Administrador y marketing
        restricted(out, "GET", "/api/events/5/insights", ADMIN, MKT);
        restricted(out, "GET", "/api/reports/monthly", ADMIN, MKT);
        restricted(out, "POST", "/api/reports/export", ADMIN, MKT);
        restricted(out, "GET", "/api/analytics/campaigns", ADMIN, MKT);
        restricted(out, "GET", "/api/analytics/participants", ADMIN, MKT);
        restricted(out, "GET", "/api/analytics/loyalty", ADMIN, MKT);

        // Lectura para administrador, organizador y marketing
        restricted(out, "GET", "/api/analytics/overview", ADMIN, ORG, MKT);
        restricted(out, "GET", "/api/events", ADMIN, ORG, MKT);
        restricted(out, "GET", "/api/events/5", ADMIN, ORG, MKT);
        restricted(out, "GET", "/api/products", ADMIN, ORG, MKT);
        restricted(out, "GET", "/api/campaigns", ADMIN, ORG, MKT);
        restricted(out, "GET", "/api/participants", ADMIN, ORG, MKT);
        restricted(out, "GET", "/api/event-products", ADMIN, ORG, MKT);

        // Operación del evento: administrador y organizador (cualquier método)
        for (String path : List.of("/api/activities", "/api/surveys/5", "/api/interactions",
                "/api/product-interactions", "/api/conversions", "/api/registrations", "/api/attendances")) {
            restricted(out, "GET", path, ADMIN, ORG);
            restricted(out, "POST", path, ADMIN, ORG);
        }
        restricted(out, "DELETE", "/api/activities/5", ADMIN, ORG);

        // Escritura sobre catálogos: solo administrador
        restricted(out, "POST", "/api/events", ADMIN);
        restricted(out, "PUT", "/api/events/5", ADMIN);
        restricted(out, "DELETE", "/api/products/5", ADMIN);
        restricted(out, "POST", "/api/campaigns", ADMIN);
        restricted(out, "PATCH", "/api/participants/5", ADMIN);
        restricted(out, "POST", "/api/event-products", ADMIN);

        // Todo lo demás: denegado
        restricted(out, "GET", "/api/unknown");
        restricted(out, "POST", "/api/unknown");
        restricted(out, "POST", "/api/analytics/overview");
        restricted(out, "GET", "/health");

        return out.stream();
    }

    private static void publicEndpoint(List<Arguments> out, String method, String path) {
        out.add(Arguments.of(method, path, ANON, Outcome.ALLOWED));
        ROLES.forEach(role -> out.add(Arguments.of(method, path, role, Outcome.ALLOWED)));
    }

    private static void restricted(List<Arguments> out, String method, String path, String... allowedRoles) {
        Set<String> allowed = Set.of(allowedRoles);
        out.add(Arguments.of(method, path, ANON, Outcome.UNAUTHORIZED));
        ROLES.forEach(role -> out.add(Arguments.of(
                method, path, role, allowed.contains(role) ? Outcome.ALLOWED : Outcome.FORBIDDEN)));
    }

    // ---- Respuestas de error (entry point y access denied) ----

    @Test
    void unauthenticatedRequest_returns401JsonMessage() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Inicia sesión para continuar."));
    }

    @Test
    void authenticatedWithoutPermission_returns403JsonMessage() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(user("org").roles(ORG)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("No tienes permiso para esta operación."));
    }

    @Test
    void missingCsrfToken_returns403JsonMessage() throws Exception {
        mockMvc.perform(post("/api/activities").with(user("admin").roles(ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tienes permiso para esta operación."));
    }

    // ---- Login ----

    @Test
    void login_withValidCredentials_returnsAuthenticatedAndAuditsLogin() throws Exception {
        mockMvc.perform(loginRequest("ana@example.com", "secret"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.authenticated").value(true));

        verify(throttle).check("login:127.0.0.1", 30);
        verify(accounts).audit(ACCOUNT_ID, "LOGIN", ACCOUNT_ID);
    }

    @Test
    void login_withInvalidCredentials_returns401JsonMessage() throws Exception {
        mockMvc.perform(loginRequest("ana@example.com", "incorrecta"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos."));

        verify(throttle).check("login:127.0.0.1", 30);
        verifyNoInteractions(accounts);
    }

    @Test
    void login_returns429WhenThrottleRejectsTheClient() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .when(throttle).check("login:127.0.0.1", 30);

        mockMvc.perform(loginRequest("ana@example.com", "secret"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message")
                        .value("Demasiados intentos. Intenta nuevamente en unos minutos."));

        verifyNoInteractions(accounts);
    }

    @Test
    void throttle_isNotAppliedToOtherPathsOrToNonPostLoginRequests() throws Exception {
        int postToOtherPath = mockMvc.perform(post("/api/auth/setup")
                        .servletPath("/api/auth/setup").with(csrf()))
                .andReturn().getResponse().getStatus();
        int getToLoginPath = mockMvc.perform(get("/api/auth/login").servletPath("/api/auth/login"))
                .andReturn().getResponse().getStatus();

        assertNotEquals(429, postToOtherPath);
        assertEquals(401, getToLoginPath);
        verifyNoInteractions(throttle);
    }

    // ---- Logout ----

    @Test
    void logout_returns204ForAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(user("ana").roles(ADMIN)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private static MockHttpServletRequestBuilder loginRequest(String email, String password) {
        return post("/api/auth/login")
                .servletPath("/api/auth/login")
                .param("email", email)
                .param("password", password)
                .with(csrf());
    }
}