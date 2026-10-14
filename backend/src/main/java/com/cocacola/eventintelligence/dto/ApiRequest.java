package com.cocacola.eventintelligence.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ApiRequest<T>(@NotNull @Valid T data) {
}