package com.cocacola.eventintelligence.controller;

import com.cocacola.eventintelligence.dto.RegistrationRequest;
import com.cocacola.eventintelligence.dto.RegistrationResponse;
import com.cocacola.eventintelligence.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {
    @org.springframework.beans.factory.annotation.Autowired
    protected com.cocacola.eventintelligence.security.AccessPolicy access;

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        access.requireOperate(request.eventId());
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
    }
}
