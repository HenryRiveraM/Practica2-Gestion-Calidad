package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cocacola.eventintelligence.exception.AiProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiAiInsightProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldSendCompactRealMetricsAndParseGeminiJsonReport() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/"
                        + "gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-only-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    String prompt = objectMapper.readTree(body).path("contents").get(0)
                            .path("parts").get(0).path("text").asText();
                    assertTrue(prompt.contains("\"attendance_rate\":75.0"));
                    assertTrue(prompt.contains("\"type\":\"SAMPLING\""));
                    assertFalse(prompt.contains("participantEmail"));
                    assertFalse(prompt.contains("private participant data"));
                    assertTrue(body.contains("\"maxOutputTokens\":2048"));
                })
                .andRespond(withSuccess("""
                        {
                          "candidates": [{
                            "content": {
                              "parts": [{
                                "text": "{\\"summary\\":\\"Asistieron 15 de 20 personas y la asistencia fue 75%.\\",\\"insights\\":[\\"La asistencia registrada fue 75%.\\"],\\"anomalies\\":[],\\"recommendations\\":[\\"Revisar la convocatoria del evento.\\"]}"
                              }]
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        GeminiAiInsightProvider provider = new GeminiAiInsightProvider(clientBuilder, objectMapper, "test-only-key");
        InsightReport report = provider.generate(eventAnalytics());

        assertTrue(provider.isConfigured());
        assertEquals("gemini", provider.providerName());
        assertEquals("gemini-3.5-flash-lite", provider.modelName());
        assertTrue(report.summary().contains("75%"));
        assertEquals(1, report.insights().size());
        assertEquals(0, report.anomalies().size());
        server.verify();
    }

    @Test
    void shouldReplaceSummaryWithNeutralTextWhenItHasUngroundedNumbers() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/"
                        + "gemini-3.5-flash-lite:generateContent"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"summary\\":\\"La asistencia fue 99%.\\",\\"insights\\":[],\\"anomalies\\":[],\\"recommendations\\":[]}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        GeminiAiInsightProvider provider = new GeminiAiInsightProvider(clientBuilder, objectMapper, "test-only-key");

        InsightReport report = provider.generate(eventAnalytics());

        org.junit.jupiter.api.Assertions.assertFalse(report.summary().contains("99"));
        server.verify();
    }

    @Test
    void shouldDropUngroundedInsightsButKeepGroundedOnes() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/"
                        + "gemini-3.5-flash-lite:generateContent"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"parts":[{"text":"{\\"summary\\":\\"La asistencia fue 75%.\\",\\"insights\\":[\\"La asistencia fue 75%.\\",\\"Hubo 99 quejas.\\"],\\"anomalies\\":[],\\"recommendations\\":[]}"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        GeminiAiInsightProvider provider = new GeminiAiInsightProvider(clientBuilder, objectMapper, "test-only-key");

        InsightReport report = provider.generate(eventAnalytics());

        assertEquals(1, report.insights().size());
        assertEquals("La asistencia fue 75%.", report.insights().get(0));
        server.verify();
    }

    @Test
    void shouldFailClearlyWhenProviderHasNoApiKey() {
        GeminiAiInsightProvider provider = new GeminiAiInsightProvider(RestClient.builder(), objectMapper, " ");

        assertFalse(provider.isConfigured());
        assertThrows(AiProviderException.class, () -> provider.generate(eventAnalytics()));
    }

    @Test
    void shouldExplainWhenGeminiRejectsAuthenticationWithoutExposingTheApiKey() {
        RestClient.Builder clientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(clientBuilder).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/"
                        + "gemini-3.5-flash-lite:generateContent"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"permission denied\"}}"));

        GeminiAiInsightProvider provider = new GeminiAiInsightProvider(clientBuilder, objectMapper, "test-only-key");
        AiProviderException exception = assertThrows(
                AiProviderException.class, () -> provider.generate(eventAnalytics()));

        assertTrue(exception.getClientMessage().contains("HTTP 403"));
        assertFalse(exception.getClientMessage().contains("test-only-key"));
        server.verify();
    }

    private static Map<String, Object> eventAnalytics() {
        return Map.of(
                "event", Map.of("id", 9L, "name", "Evento real", "event_date", "2026-10-03"),
                "metrics", Map.ofEntries(
                        Map.entry("registered", 20L),
                        Map.entry("attendees", 15L),
                        Map.entry("attendance_rate", 75.0),
                        Map.entry("conversions", 3L),
                        Map.entry("conversion_rate", 20.0),
                        Map.entry("average_rating", 4.5),
                        Map.entry("nps", 40.0),
                        Map.entry("new_participants", 10L),
                        Map.entry("recurrent_participants", 5L),
                        Map.entry("engaged_participants", 12L),
                        Map.entry("engagement_rate", 80.0),
                        Map.entry("peakAttendanceHours", List.of(Map.of("hour", 18, "attendees", 8L)))),
                "activities", List.of(Map.of(
                        "type", "SAMPLING",
                        "activity_count", 2L,
                        "total_quantity", 30L,
                        "participants", 3L)),
                "participantEmail", "private participant data");
    }
}