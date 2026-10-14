package com.cocacola.eventintelligence.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAccountFilter extends OncePerRequestFilter {
    public static final String ACTIVE_ROLE = "active-role";
    private final AccountStore accounts;
    public SessionAccountFilter(AccountStore accounts) { this.accounts = accounts; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Account previous) {
            var fresh = accounts.find(previous.id()).orElse(null);
            if (fresh == null || !fresh.isEnabled() || fresh.authVersion() != previous.authVersion()) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) request.getSession(false).invalidate();
            } else {
                var session = request.getSession();
                Role role = session.getAttribute(ACTIVE_ROLE) instanceof Role saved && fresh.roles().contains(saved)
                        ? saved : fresh.roles().stream().sorted().findFirst().orElseThrow();
                session.setAttribute(ACTIVE_ROLE, role);
                SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        fresh.withoutPassword(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
            }
        }
        chain.doFilter(request, response);
    }
}
