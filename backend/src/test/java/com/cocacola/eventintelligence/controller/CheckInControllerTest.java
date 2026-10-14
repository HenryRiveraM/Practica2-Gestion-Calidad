package com.cocacola.eventintelligence.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocacola.eventintelligence.dto.CheckInRequest;
import com.cocacola.eventintelligence.dto.CheckInResponse;
import com.cocacola.eventintelligence.exception.BusinessRuleException;
import com.cocacola.eventintelligence.exception.DuplicateCheckInException;
import com.cocacola.eventintelligence.exception.GlobalExceptionHandler;
import com.cocacola.eventintelligence.exception.InvalidRegistrationTokenException;
import com.cocacola.eventintelligence.repository.AttendanceRepository;
import com.cocacola.eventintelligence.service.AttendanceService;
import com.cocacola.eventintelligence.service.CheckInService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CheckInControllerTest {
    private CheckInService checkInService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        checkInService = mock(CheckInService.class);
        AttendanceService attendanceService = new AttendanceService(mock(AttendanceRepository.class));
        mockMvc = MockMvcBuilders.standaloneSetup(new AttendanceController(attendanceService, checkInService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRejectEmptyTokenWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/attendances/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qrToken":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("token")));

        verify(checkInService, never()).checkIn(any(CheckInRequest.class));
    }

    @Test
    void shouldReturnNotFoundWhenTokenHasNoRegistration() throws Exception {
        when(checkInService.checkIn(any(CheckInRequest.class)))
                .thenThrow(new InvalidRegistrationTokenException("No existe una inscripción para este token"));

        mockMvc.perform(post("/api/attendances/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qrToken":"123e4567-e89b-12d3-a456-426614174000"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No existe una inscripción para este token"));
    }

    @Test
    void shouldReturnConflictForDuplicateCheckIn() throws Exception {
        when(checkInService.checkIn(any(CheckInRequest.class))).thenThrow(new DuplicateCheckInException());

        mockMvc.perform(post("/api/attendances/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qrToken":"123e4567-e89b-12d3-a456-426614174000"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Esta inscripción ya realizó el check-in"));
    }

    @Test
    void shouldReturnCheckedInAttendance() throws Exception {
        when(checkInService.checkIn(any(CheckInRequest.class)))
                .thenReturn(new CheckInResponse(11L, 4L, 8L, "Evento real", "Ana Perez", "CHECKED_IN",
                        java.time.OffsetDateTime.parse("2026-10-03T13:00:00Z")));

        mockMvc.perform(post("/api/attendances/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qrToken":"123e4567-e89b-12d3-a456-426614174000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.eventName").value("Evento real"))
                .andExpect(jsonPath("$.participantName").value("Ana Perez"))
                .andExpect(jsonPath("$.checkInAt").exists());
    }

    @Test
    void shouldRejectMalformedQrWithBadRequest() throws Exception {
        when(checkInService.checkIn(any(CheckInRequest.class)))
                .thenThrow(new BusinessRuleException("El token QR no es válido"));

        mockMvc.perform(post("/api/attendances/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qrToken":"no-es-un-token-valido"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
