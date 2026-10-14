package com.cocacola.eventintelligence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record ActivityRequest(
        @NotNull(message = "El evento es obligatorio")
        @Positive(message = "El evento debe ser válido")
        Long eventId,
        @Positive(message = "El participante debe ser válido")
        Long participantId,
        @NotBlank(message = "El tipo de actividad es obligatorio")
        @Size(max = 20)
        String type,
        @NotNull(message = "La cantidad es obligatoria")
        @PositiveOrZero(message = "La cantidad no puede ser negativa")
        Integer quantity,
        @NotBlank(message = "El resultado es obligatorio")
        String result,
        @NotNull(message = "La fecha y hora son obligatorias")
        OffsetDateTime occurredAt) {
}
