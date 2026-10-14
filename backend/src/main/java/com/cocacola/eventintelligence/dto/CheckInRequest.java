package com.cocacola.eventintelligence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckInRequest(
        @NotBlank(message = "El token QR es obligatorio")
        @Size(max = 500, message = "El token QR no es válido")
        String qrToken) {
}