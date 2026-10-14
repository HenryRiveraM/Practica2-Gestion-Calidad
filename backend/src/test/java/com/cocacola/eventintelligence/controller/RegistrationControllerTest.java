package com.cocacola.eventintelligence.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocacola.eventintelligence.dto.RegistrationRequest;
import com.cocacola.eventintelligence.dto.RegistrationResponse;
import com.cocacola.eventintelligence.exception.DuplicateRegistrationException;
import com.cocacola.eventintelligence.exception.GlobalExceptionHandler;
import com.cocacola.eventintelligence.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;

class RegistrationControllerTest {
    private RegistrationService registrationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registrationService = mock(RegistrationService.class);
        var controller = new RegistrationController(registrationService);
        controller.access = org.mockito.Mockito.mock(com.cocacola.eventintelligence.security.AccessPolicy.class);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRejectInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":10,"firstName":"Ana","lastName":"Perez","email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));

        verify(registrationService, never()).register(any(RegistrationRequest.class));
    }

    @Test
    void shouldReturnConflictForDuplicateRegistration() throws Exception {
        when(registrationService.register(any(RegistrationRequest.class)))
                .thenThrow(new DuplicateRegistrationException(10L, 20L));

        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":10,"firstName":"Ana","lastName":"Perez","email":"ana@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El participante 20 ya está registrado en el evento 10"));
    }

    @Test
    void shouldReturnCreatedRegistrationAndBackendToken() throws Exception {
        when(registrationService.register(any(RegistrationRequest.class)))
                .thenReturn(new RegistrationResponse(30L, 10L, "Evento demo", 20L, "Ana Perez",
                        "server-generated-token"));

        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":10,"firstName":"Ana","lastName":"Perez","email":"ana@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationId").value(30))
                .andExpect(jsonPath("$.participantName").value("Ana Perez"))
                .andExpect(jsonPath("$.eventName").value("Evento demo"))
                .andExpect(jsonPath("$.qrToken").value("server-generated-token"));
    }
}
