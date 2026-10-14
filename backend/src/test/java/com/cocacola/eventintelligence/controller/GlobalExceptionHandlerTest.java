package com.cocacola.eventintelligence.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocacola.eventintelligence.exception.AiProviderException;
import com.cocacola.eventintelligence.exception.BusinessRuleException;
import com.cocacola.eventintelligence.exception.GlobalExceptionHandler;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldHandleBusinessRuleException() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Regla de negocio inválida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").doesNotExist());
    }

    @Test
    void shouldMapDataIntegrityViolationToConflictWithoutDatabaseDetails() throws Exception {
        mockMvc.perform(get("/test/integrity-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("La operación entra en conflicto con los datos existentes."))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void shouldMapUnreadableJsonToBadRequest() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "El cuerpo de la solicitud está vacío o tiene un formato inválido."))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void shouldMapValidationFailureToBadRequest() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("{name=El nombre es obligatorio}"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void shouldMapEntityNotFoundToNotFound() throws Exception {
        mockMvc.perform(get("/test/entity-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void shouldMapIllegalArgumentToBadRequest() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Solicitud inválida."));
    }

    @Test
    void shouldHideUnexpectedExceptionDetailsFromResponse() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("Error interno del servidor"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void shouldHideExternalProviderDetailsFromResponse() throws Exception {
        mockMvc.perform(get("/test/provider-error"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").value(
                        "No se pudo completar la operación con el proveedor externo."));
    }

    @RestController
    @RequestMapping("/test")
    public static class TestController {
        @GetMapping("/business-error")
        public void businessError() {
            throw new BusinessRuleException("Regla de negocio inválida");
        }

        @GetMapping("/integrity-error")
        public void integrityError() {
            throw new DataIntegrityViolationException("sensitive SQL and value");
        }

        @PostMapping("/body")
        public void body(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/entity-not-found")
        public void entityNotFound() {
            throw new EntityNotFoundException("sensitive entity details");
        }

        @GetMapping("/illegal-argument")
        public void illegalArgument() {
            throw new IllegalArgumentException("Identificador inválido");
        }

        @GetMapping("/unexpected-error")
        public void unexpectedError() {
            throw new IllegalStateException("private internal details");
        }

        @GetMapping("/provider-error")
        public void providerError() {
            throw new AiProviderException("private provider details");
        }
    }

    public record TestRequest(@NotBlank(message = "El nombre es obligatorio") String name) {
    }
}
