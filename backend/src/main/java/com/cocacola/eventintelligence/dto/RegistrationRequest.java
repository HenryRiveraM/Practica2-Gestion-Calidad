package com.cocacola.eventintelligence.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotNull(message = "El evento es obligatorio")
        @Positive(message = "El evento debe ser válido")
        Long eventId,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100)
        String firstName,
        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100)
        String lastName,
        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no es válido")
        @Size(max = 255)
        String email,
        @Pattern(regexp = "^$|^\\+?[0-9][0-9 ()-]{5,29}$", message = "El teléfono no es válido")
        String phone,
        @Size(max=100) String city,
        @Pattern(regexp="<18|18-24|25-34|35-44|45-54|55\\+") String ageRange,
        @Size(max=100) String productPreference,
        Boolean communicationConsent) {
    public RegistrationRequest(Long eventId, String firstName, String lastName, String email, String phone) {
        this(eventId, firstName, lastName, email, phone, null, null, null, false);
    }
}
