package com.cocacola.eventintelligence.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, AccountStore accounts, ObjectMapper json, RequestThrottle throttle) throws Exception {
        http.cors(cors -> {}).csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/auth/status", "/api/public/events", "/api/public/events/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/setup", "/api/auth/activate", "/api/public/events/*/registrations").permitAll()
                .requestMatchers("/api/auth/me", "/api/auth/active-role", "/api/auth/logout").authenticated()
                .requestMatchers("/api/admin/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/api/events/*/insights", "/api/reports/**", "/api/analytics/campaigns", "/api/analytics/participants", "/api/analytics/loyalty").hasAnyRole("ADMINISTRADOR", "MARKETING")
                .requestMatchers(HttpMethod.GET, "/api/analytics/**", "/api/events/**", "/api/products/**", "/api/campaigns/**", "/api/participants/**", "/api/event-products/**").hasAnyRole("ADMINISTRADOR", "ORGANIZADOR", "MARKETING")
                .requestMatchers("/api/activities/**", "/api/surveys/**", "/api/interactions/**", "/api/product-interactions/**", "/api/conversions/**", "/api/registrations", "/api/attendances/**").hasAnyRole("ADMINISTRADOR", "ORGANIZADOR")
                .requestMatchers("/api/events/**", "/api/products/**", "/api/campaigns/**", "/api/participants/**", "/api/event-products/**").hasRole("ADMINISTRADOR")
                .anyRequest().denyAll())
            .formLogin(form -> form.loginProcessingUrl("/api/auth/login").usernameParameter("email")
                .successHandler((request, response, authentication) -> {
                    Account account = (Account) authentication.getPrincipal();
                    accounts.audit(account.id(), "LOGIN", account.id());
                    response.setContentType("application/json");
                    response.getWriter().write("{\"authenticated\":true}");
                })
                .failureHandler((request, response, error) -> writeError(json, response, 401, "Correo o contraseña incorrectos.")))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> writeError(json, response, 401, "Inicia sesión para continuar."))
                .accessDeniedHandler((request, response, error) -> writeError(json, response, 403, "No tienes permiso para esta operación.")))
            .addFilterBefore(new org.springframework.web.filter.OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {
                    if (request.getServletPath().equals("/api/auth/login") && request.getMethod().equals("POST")) {
                        try { throttle.check("login:" + request.getRemoteAddr(), 30); }
                        catch (org.springframework.web.server.ResponseStatusException ex) { writeError(json, response, 429, "Demasiados intentos. Intenta nuevamente en unos minutos."); return; }
                    }
                    chain.doFilter(request, response);
                }
            }, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new SessionAccountFilter(accounts), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    private static void writeError(ObjectMapper json, HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status); response.setContentType("application/json");
        json.writeValue(response.getWriter(), Map.of("status", status, "message", message));
    }
}
