package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.security.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
    private final AccountManagement management;
    private final AccessPolicy access;
    public UserAdminController(AccountManagement management, AccessPolicy access) { this.management=management; this.access=access; }
    public record CreateUser(@NotBlank @Size(max=120) String name, @NotBlank @Email @Size(max=255) String email,
                             @NotEmpty Set<@NotNull Role> roles, @NotNull Set<@Positive Long> eventIds) {}
    public record UpdateUser(@NotBlank @Size(max=120) String name, boolean enabled,
                             @NotEmpty Set<@NotNull Role> roles, @NotNull Set<@Positive Long> eventIds) {}
    @GetMapping public List<Map<String,Object>> list() { access.requireAdmin(); return management.list(); }
    @PostMapping public Map<String,Object> create(@Valid @RequestBody CreateUser body) {
        access.requireAdmin(); return management.create(access.account().id(),body.name(),body.email(),body.roles(),body.eventIds());
    }
    @PutMapping("/{id}") public Map<String,Object> update(@PathVariable Long id, @Valid @RequestBody UpdateUser body) {
        access.requireAdmin(); return management.update(access.account().id(),id,body.name(),body.enabled(),body.roles(),body.eventIds());
    }
    @PostMapping("/{id}/activation") public Map<String,String> invite(@PathVariable Long id) {
        access.requireAdmin(); return Map.of("activationToken",management.invite(access.account().id(),id));
    }
}
