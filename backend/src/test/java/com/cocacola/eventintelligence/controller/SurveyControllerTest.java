package com.cocacola.eventintelligence.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cocacola.eventintelligence.dto.SurveyRequest;
import com.cocacola.eventintelligence.entity.Survey;
import com.cocacola.eventintelligence.exception.DuplicateSurveyException;
import com.cocacola.eventintelligence.exception.GlobalExceptionHandler;
import com.cocacola.eventintelligence.service.SurveyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SurveyControllerTest {
    private SurveyService surveyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        surveyService = org.mockito.Mockito.mock(SurveyService.class);
        var controller = new SurveyController(surveyService);
        controller.access = org.mockito.Mockito.mock(com.cocacola.eventintelligence.security.AccessPolicy.class);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldAcceptValidRatingAndNps() throws Exception {
        when(surveyService.submit(any(SurveyRequest.class))).thenReturn(new Survey());

        mockMvc.perform(post("/api/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":10,"participantId":20,"rating":5,"nps":10,"comment":"Buena experiencia"}
                                """))
                .andExpect(status().isCreated());

        verify(surveyService).submit(any(SurveyRequest.class));
    }

    @Test
    void shouldRejectRatingBelowMinimum() throws Exception {
        assertInvalidRequest("""
                {"eventId":10,"participantId":20,"rating":0,"nps":5}
                """, "rating");
    }

    @Test
    void shouldRejectRatingAboveMaximum() throws Exception {
        assertInvalidRequest("""
                {"eventId":10,"participantId":20,"rating":6,"nps":5}
                """, "rating");
    }

    @Test
    void shouldRejectNpsBelowMinimum() throws Exception {
        assertInvalidRequest("""
                {"eventId":10,"participantId":20,"rating":3,"nps":-1}
                """, "nps");
    }

    @Test
    void shouldRejectNpsAboveMaximum() throws Exception {
        assertInvalidRequest("""
                {"eventId":10,"participantId":20,"rating":3,"nps":11}
                """, "nps");
    }

    @Test
    void shouldReturnConflictWhenSurveyIsDuplicated() throws Exception {
        when(surveyService.submit(any(SurveyRequest.class)))
                .thenThrow(new DuplicateSurveyException(10L, 20L));

        mockMvc.perform(post("/api/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId":10,"participantId":20,"rating":5,"nps":10}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "El participante 20 ya envió una encuesta para el evento 10"));
    }

    private void assertInvalidRequest(String requestBody, String invalidField) throws Exception {
        mockMvc.perform(post("/api/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(invalidField)));

        verify(surveyService, never()).submit(any(SurveyRequest.class));
    }
}
