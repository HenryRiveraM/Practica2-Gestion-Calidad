package com.cocacola.eventintelligence.security;

import com.cocacola.eventintelligence.exception.BusinessRuleException;
import com.cocacola.eventintelligence.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountManagement implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final AccountStore accounts;
    private final PasswordEncoder passwords;
    private final String setupToken = token();
    public AccountManagement(JdbcTemplate jdbc, AccountStore accounts, PasswordEncoder passwords) {
        this.jdbc = jdbc; this.accounts = accounts; this.passwords = passwords;
    }
    @Override public void run(ApplicationArguments args) {
        if (accounts.isEmpty()) org.slf4j.LoggerFactory.getLogger(getClass()).warn(
                "Configuración inicial pendiente. Abre /login y usa este código temporal de administrador: {}", setupToken);
    }
    public Map<String, Object> view(Account account, Role activeRole) {
        return Map.of("id", account.id(), "name", account.name(), "email", account.email(), "roles", account.roles(),
                "activeRole", activeRole, "permissions", activeRole.permissions(), "eventIds", account.eventIds());
    }
    public Map<String, Object> adminView(Account account) {
        return Map.of("id", account.id(), "name", account.name(), "email", account.email(), "roles", account.roles(),
                "enabled", account.enabled(), "activated", account.passwordHash() != null, "eventIds", account.eventIds());
    }
    public List<Map<String, Object>> list() {
        return jdbc.queryForList("SELECT id FROM app_users ORDER BY id", Long.class).stream()
                .map(id -> adminView(accounts.find(id).orElseThrow())).toList();
    }
    @Transactional public void setup(String code, String name, String email, String password) {
        lock();
        if (!accounts.isEmpty() || !MessageDigest.isEqual(setupToken.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8)))
            throw new BusinessRuleException("El código de configuración no es válido o ya fue utilizado.");
        validatePassword(password);
        Long id = jdbc.queryForObject("INSERT INTO app_users(name,email,password_hash) VALUES (?,?,?) RETURNING id", Long.class,
                name.trim(), email.trim().toLowerCase(Locale.ROOT), passwords.encode(password));
        jdbc.update("INSERT INTO app_user_roles(user_id,role) VALUES (?, 'ADMINISTRADOR')", id);
        accounts.audit(id, "INITIAL_ADMIN_CREATED", id);
    }
    @Transactional public Map<String, Object> create(Long actor, String name, String email, Set<Role> roles, Set<Long> events) {
        lock();
        validateRoles(roles);
        Long id = jdbc.queryForObject("INSERT INTO app_users(name,email) VALUES (?,?) RETURNING id", Long.class, name.trim(), email.trim().toLowerCase(Locale.ROOT));
        saveAssignments(id, roles, events);
        accounts.audit(actor, "USER_CREATED", id);
        return Map.of("user", adminView(accounts.find(id).orElseThrow()), "activationToken", issueToken(id));
    }
    @Transactional public Map<String, Object> update(Long actor, Long id, String name, boolean enabled, Set<Role> roles, Set<Long> events) {
        lock();
        validateRoles(roles);
        Account existing = accounts.find(id).orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        if (existing.roles().contains(Role.ADMINISTRADOR) && existing.enabled() && (!enabled || !roles.contains(Role.ADMINISTRADOR))) {
            Long administrators = jdbc.queryForObject("SELECT COUNT(*) FROM app_users u JOIN app_user_roles r ON u.id=r.user_id WHERE u.enabled=TRUE AND u.password_hash IS NOT NULL AND r.role='ADMINISTRADOR'", Long.class);
            if (administrators <= 1 || actor.equals(id)) throw new BusinessRuleException("Conserva al menos otro administrador activo antes de retirar este acceso.");
        }
        jdbc.update("UPDATE app_users SET name=?, enabled=?, auth_version=auth_version+1 WHERE id=?", name.trim(), enabled, id);
        saveAssignments(id, roles, events);
        accounts.audit(actor, "USER_ACCESS_UPDATED", id);
        return adminView(accounts.find(id).orElseThrow());
    }
    @Transactional public String invite(Long actor, Long id) {
        lock();
        Account account = accounts.find(id).orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        if (account.passwordHash() != null || !account.enabled()) throw new BusinessRuleException("Solo se pueden activar cuentas pendientes y habilitadas.");
        accounts.audit(actor, "ACTIVATION_LINK_CREATED", id);
        return issueToken(id);
    }
    @Transactional public void activate(String code, String password) {
        lock();
        validatePassword(password);
        var ids = jdbc.queryForList("SELECT user_id FROM app_activation_tokens WHERE token_hash=? AND used_at IS NULL AND expires_at>CURRENT_TIMESTAMP FOR UPDATE", Long.class, hash(code));
        if (ids.isEmpty()) throw new BusinessRuleException("El enlace no es válido, venció o ya fue utilizado. Solicita uno nuevo al administrador.");
        Long id = ids.getFirst();
        lock();
        Account account = accounts.find(id).orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        if (!account.enabled() || account.passwordHash() != null) throw new BusinessRuleException("La cuenta no está disponible para activación.");
        jdbc.update("UPDATE app_users SET password_hash=?, auth_version=auth_version+1 WHERE id=?", passwords.encode(password), id);
        jdbc.update("UPDATE app_activation_tokens SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL", id);
        accounts.audit(id, "ACCOUNT_ACTIVATED", id);
    }
    private void saveAssignments(Long id, Set<Role> roles, Set<Long> events) {
        jdbc.update("DELETE FROM app_user_roles WHERE user_id=?", id);
        roles.forEach(role -> jdbc.update("INSERT INTO app_user_roles(user_id,role) VALUES (?,?)", id, role.name()));
        jdbc.update("DELETE FROM app_user_events WHERE user_id=?", id);
        if (roles.contains(Role.ORGANIZADOR)) events.forEach(event -> jdbc.update("INSERT INTO app_user_events(user_id,event_id) VALUES (?,?)", id, event));
    }
    private String issueToken(Long id) {
        jdbc.update("UPDATE app_activation_tokens SET used_at=CURRENT_TIMESTAMP WHERE user_id=? AND used_at IS NULL", id);
        String raw = token();
        jdbc.update("INSERT INTO app_activation_tokens(token_hash,user_id,expires_at) VALUES (?,?,CURRENT_TIMESTAMP + INTERVAL '24 hours')", hash(raw), id);
        return raw;
    }
    private void lock() { jdbc.execute("SELECT pg_advisory_xact_lock(49170421)"); }
    private void validateRoles(Set<Role> roles) { if (roles == null || roles.isEmpty()) throw new BusinessRuleException("Asigna al menos una función."); }
    private void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessRuleException("La contraseña debe tener al menos 12 caracteres y un máximo de 72 bytes.");
    }
    static String token() { byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
