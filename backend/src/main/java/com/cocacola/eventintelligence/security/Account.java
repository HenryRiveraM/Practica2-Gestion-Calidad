package com.cocacola.eventintelligence.security;

import java.io.Serializable;
import java.util.Set;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record Account(Long id, String name, String email, String passwordHash,
                      boolean enabled, int authVersion, Set<Role> roles, Set<Long> eventIds) implements UserDetails, Serializable {
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash == null ? "" : passwordHash; }
    @Override public boolean isEnabled() { return enabled && passwordHash != null && !roles.isEmpty(); }
    @Override public java.util.Collection<SimpleGrantedAuthority> getAuthorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
    }
    public Account withoutPassword() { return new Account(id, name, email, "", enabled, authVersion, roles, eventIds); }
}
