package com.cocacola.eventintelligence.security;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class AccountStore implements UserDetailsService {
    private final JdbcTemplate jdbc;
    public AccountStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Account> find(Long id) {
        return jdbc.query("SELECT * FROM app_users WHERE id = ?", (rs, n) -> new Account(
                rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("password_hash"),
                rs.getBoolean("enabled"), rs.getInt("auth_version"),
                new LinkedHashSet<>(jdbc.query("SELECT role FROM app_user_roles WHERE user_id = ? ORDER BY role",
                        (r, i) -> Role.valueOf(r.getString(1)), id)),
                new LinkedHashSet<>(jdbc.queryForList("SELECT event_id FROM app_user_events WHERE user_id = ?", Long.class, id))
        ), id).stream().findFirst();
    }
    @Override public Account loadUserByUsername(String email) {
        return jdbc.queryForList("SELECT id FROM app_users WHERE email = ?", Long.class, email.trim().toLowerCase(Locale.ROOT))
                .stream().findFirst().flatMap(this::find)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
    }
    public boolean isEmpty() { return jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Long.class) == 0; }
    public void audit(Long actor, String action, Long target) {
        jdbc.update("INSERT INTO app_access_audit(actor_id, action, target_id) VALUES (?,?,?)", actor, action, target);
    }
}
