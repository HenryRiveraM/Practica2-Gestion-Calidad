package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.security.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountStore accounts;
    private final AccountManagement management;
    private final AccessPolicy access;
    private final RequestThrottle throttle;
    public AuthController(AccountStore accounts, AccountManagement management, AccessPolicy access, RequestThrottle throttle) {
        this.accounts=accounts; this.management=management; this.access=access; this.throttle=throttle;
    }
    public record Setup(@NotBlank @Size(max=64) String code, @NotBlank @Size(max=120) String name,
                        @NotBlank @Email @Size(max=255) String email, @NotBlank @Size(min=12,max=72) String password) {}
    public record Activation(@NotBlank @Size(max=64) String token, @NotBlank @Size(min=12,max=72) String password) {}
    public record SwitchRole(@NotNull Role role) {}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @GetMapping("/status") public Map<String,Boolean> status() { return Map.of("setupRequired",accounts.isEmpty()); }
    @GetMapping("/me") public Map<String,Object> me() { return management.view(access.account(),access.role()); }
    @PostMapping("/setup") public Map<String,Boolean> setup(@Valid @RequestBody Setup body, HttpServletRequest request) {
        throttle.check("setup:"+request.getRemoteAddr(), 10);
        management.setup(body.code(),body.name(),body.email(),body.password());
        return Map.of("created", true);
    }
    @PostMapping("/activate") public Map<String,Boolean> activate(@Valid @RequestBody Activation body, HttpServletRequest request) {
        throttle.check("activate:"+request.getRemoteAddr(),20); management.activate(body.token(),body.password()); return Map.of("activated",true);
    }
    @PostMapping("/active-role") public Map<String,Object> switchRole(@Valid @RequestBody SwitchRole body, HttpServletRequest request) {
        if (!access.account().roles().contains(body.role())) throw new AccessDeniedException("Esta función no está asignada a tu cuenta.");
        request.getSession().setAttribute(SessionAccountFilter.ACTIVE_ROLE, body.role());
        return management.view(access.account(),body.role());
    }
}
