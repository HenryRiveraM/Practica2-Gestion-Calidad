package com.cocacola.eventintelligence.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SurveyRequest(
        @NotNull(message = "El evento es obligatorio")
        @Positive(message = "El evento debe ser válido")
        Long eventId,
        @NotNull(message = "El participante es obligatorio")
        @Positive(message = "El participante debe ser válido")
        Long participantId,
        @NotNull(message = "La calificación es obligatoria")
        @Min(value = 1, message = "La calificación mínima es 1")
        @Max(value = 5, message = "La calificación máxima es 5")
        Short rating,
        @NotNull(message = "El NPS es obligatorio")
        @Min(value = 0, message = "El NPS mínimo es 0")
        @Max(value = 10, message = "El NPS máximo es 10")
        Short nps,
        @Size(max = 2000, message = "El comentario no puede superar 2000 caracteres")
        String comment) {
}
