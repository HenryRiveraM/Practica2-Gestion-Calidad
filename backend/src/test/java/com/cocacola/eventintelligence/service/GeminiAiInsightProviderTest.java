package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.cocacola.eventintelligence.exception.AiProviderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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

    // =====================================================================
    // HU-CAL: pruebas de caminos independientes (ver matriz V(G) del informe)
    // Convención: R-xx identifica el camino independiente cubierto.
    // =====================================================================

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
            + "gemini-3.5-flash-lite:generateContent";
    private static final String VALID_REPORT =
            "{\"summary\":\"Resumen sin cifras.\",\"insights\":[],\"anomalies\":[],\"recommendations\":[]}";

    // ---- generate / clientMessageForStatus --------------------------------

    /** generate: camino catch(RestClientResponseException); clientMessageForStatus: una fila por rama del switch. */
    @ParameterizedTest(name = "HTTP {0} produce un mensaje claro para el cliente")
    @CsvSource({
            "400, Gemini rechazó la solicitud (HTTP 400)",
            "401, Gemini rechazó la autenticación o el acceso (HTTP 401)",
            "404, Gemini no encontró el modelo o endpoint solicitado (HTTP 404)",
            "429, Gemini alcanzó el límite de uso o cuota (HTTP 429)",
            "500, Gemini presenta una interrupción temporal (HTTP 500)",
            "503, Gemini presenta una interrupción temporal (HTTP 503)",
            "418, Gemini rechazó la solicitud (HTTP 418)"
    })
    void shouldTranslateGeminiHttpStatusIntoClientMessage(int status, String expectedFragment) {
        // Arrange
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL)).andRespond(withStatus(HttpStatusCode.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"detalle\"}}"));
        GeminiAiInsightProvider provider = new GeminiAiInsightProvider(builder, objectMapper, "test-only-key");

        // Act
        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.generate(eventAnalytics()));

        // Assert
        assertEquals("Gemini respondió con HTTP " + status + ".", exception.getMessage());
        assertTrue(exception.getClientMessage().contains(expectedFragment));
        assertFalse(exception.getClientMessage().contains("test-only-key"));
        assertTrue(exception.getCause() instanceof RestClientResponseException);
        server.verify();
    }

    /** generate: camino catch(RestClientException | IllegalArgumentException) por fallo de transporte. */
    @Test
    void shouldReportConnectionFailureWhenTransportFails() {
        // Arrange
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL)).andRespond(withException(new IOException("sin red")));
        GeminiAiInsightProvider provider = new GeminiAiInsightProvider(builder, objectMapper, "test-only-key");

        // Act
        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.generate(eventAnalytics()));

        // Assert
        assertEquals("No fue posible conectar con Gemini.", exception.getMessage());
        assertTrue(exception.getClientMessage().startsWith("No se pudo conectar con Gemini."));
        server.verify();
    }

    // ---- extractText -------------------------------------------------------

    /** extractText: response == null (HTTP 204 sin cuerpo). */
    @Test
    void shouldRejectNoContentResponse() {
        // Arrange
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL)).andRespond(withNoContent());
        GeminiAiInsightProvider provider = new GeminiAiInsightProvider(builder, objectMapper, "test-only-key");

        // Act
        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.generate(eventAnalytics()));

        // Assert
        assertEquals("Gemini devolvió una respuesta sin contenido.", exception.getMessage());
    }

    /** extractText: candidates ausente o vacío. */
    @ParameterizedTest(name = "respuesta {0} se rechaza por no tener candidatos")
    @ValueSource(strings = {"{}", "{\"candidates\":[]}", "{\"candidates\":\"texto\"}"})
    void shouldRejectResponseWithoutCandidates(String rawResponse) {
        // Arrange / Act
        AiProviderException exception = generateExpectingFailure(rawResponse);

        // Assert
        assertEquals("Gemini devolvió una respuesta sin contenido.", exception.getMessage());
    }

    /** extractText: finishReason = MAX_TOKENS. */
    @Test
    void shouldRejectResponseTruncatedByTokenLimit() {
        // Arrange
        String raw = "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"{\"}]}}]}";

        // Act
        AiProviderException exception = generateExpectingFailure(raw);

        // Assert
        assertTrue(exception.getMessage().contains("MAX_TOKENS"));
        assertEquals("Gemini cortó la respuesta antes de terminar. Vuelve a intentarlo.",
                exception.getClientMessage());
    }

    /** extractText: el candidato no tiene arreglo de partes. */
    @Test
    void shouldRejectCandidateWithoutPartsArray() {
        // Arrange
        String raw = "{\"candidates\":[{\"content\":{}}]}";

        // Act
        AiProviderException exception = generateExpectingFailure(raw);

        // Assert
        assertEquals("Gemini devolvió una respuesta con formato inválido.", exception.getMessage());
    }

    /** extractText: ninguna parte contiene texto utilizable (vacía, sin texto, numérica, nula o en blanco). */
    @ParameterizedTest(name = "partes {0} no contienen texto de análisis")
    @ValueSource(strings = {
            "[]",
            "[{\"inlineData\":{}}]",
            "[{\"text\":123}]",
            "[{\"text\":null}]",
            "[{\"text\":\"   \"}]"
    })
    void shouldRejectPartsWithoutUsableText(String partsJson) {
        // Arrange
        String raw = "{\"candidates\":[{\"content\":{\"parts\":" + partsJson + "}}]}";

        // Act
        AiProviderException exception = generateExpectingFailure(raw);

        // Assert
        assertEquals("Gemini no devolvió texto de análisis.", exception.getMessage());
    }

    /** extractText: el ciclo omite una parte en blanco y usa la siguiente. */
    @Test
    void shouldSkipBlankPartAndUseNextTextPart() throws Exception {
        // Arrange
        String raw = objectMapper.writeValueAsString(Map.of("candidates", List.of(Map.of("content",
                Map.of("parts", List.of(Map.of("text", "   "), Map.of("text", VALID_REPORT)))))));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL)).andRespond(withSuccess(raw, MediaType.APPLICATION_JSON));
        GeminiAiInsightProvider provider = new GeminiAiInsightProvider(builder, objectMapper, "test-only-key");

        // Act
        InsightReport report = provider.generate(eventAnalytics());

        // Assert
        assertEquals("Resumen sin cifras.", report.summary());
        server.verify();
    }

    // ---- stripCodeFence / parseReport -------------------------------------

    /** stripCodeFence: bloque ```json ... ``` completo. */
    @Test
    void shouldStripJsonCodeFenceBeforeParsing() throws Exception {
        // Arrange
        String fenced = "```json\n" + VALID_REPORT + "\n```";

        // Act
        InsightReport report = generateFromReportText(fenced, eventAnalytics());

        // Assert
        assertEquals("Resumen sin cifras.", report.summary());
    }

    /** stripCodeFence: bloque sin marcador de cierre. */
    @Test
    void shouldAcceptCodeFenceWithoutClosingMarker() throws Exception {
        // Arrange
        String unclosed = "```json\n" + VALID_REPORT;

        // Act
        InsightReport report = generateFromReportText(unclosed, eventAnalytics());

        // Assert
        assertEquals("Resumen sin cifras.", report.summary());
    }

    /** stripCodeFence: marcador sin salto de línea ni contenido. */
    @Test
    void shouldRejectCodeFenceWithoutContent() throws Exception {
        // Arrange / Act
        AiProviderException exception = generateExpectingFailure(envelope("```"));

        // Assert
        assertNotNull(exception.getMessage());
    }

    /** parseReport: camino catch(Exception) por JSON mal formado. */
    @Test
    void shouldRejectMalformedJsonReport() throws Exception {
        // Arrange / Act
        AiProviderException exception = generateExpectingFailure(envelope("{esto no es json"));

        // Assert
        assertEquals("Gemini devolvió un reporte que no es JSON válido.", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    // ---- requiredText ------------------------------------------------------

    /** requiredText: summary ausente, nulo, numérico o en blanco. */
    @ParameterizedTest(name = "reporte {0} se rechaza por summary inválido")
    @ValueSource(strings = {
            "{\"insights\":[]}",
            "{\"summary\":null}",
            "{\"summary\":42}",
            "{\"summary\":\"   \"}"
    })
    void shouldRejectReportWithoutValidSummary(String report) throws Exception {
        // Arrange / Act
        AiProviderException exception = generateExpectingFailure(envelope(report));

        // Assert
        assertEquals("Gemini omitió el campo obligatorio: summary", exception.getMessage());
    }

    /** requiredText: un resumen más largo que el límite se recorta con puntos suspensivos. */
    @Test
    void shouldTruncateSummaryLongerThanLimit() throws Exception {
        // Arrange
        String report = reportJson("a".repeat(500), List.of());

        // Act
        InsightReport result = generateFromReportText(report, eventAnalytics());

        // Assert
        assertEquals(400, result.summary().length());
        assertTrue(result.summary().endsWith("…"));
    }

    // ---- textArray ---------------------------------------------------------

    /** textArray: lista ausente o nula se convierte en lista vacía. */
    @Test
    void shouldTreatMissingAndNullListsAsEmpty() throws Exception {
        // Arrange
        String report = "{\"summary\":\"Resumen sin cifras.\",\"insights\":null}";

        // Act
        InsightReport result = generateFromReportText(report, eventAnalytics());

        // Assert
        assertEquals(List.of(), result.insights());
        assertEquals(List.of(), result.anomalies());
        assertEquals(List.of(), result.recommendations());
    }

    /** textArray: valor que no es arreglo. */
    @Test
    void shouldRejectListThatIsNotAnArray() throws Exception {
        // Arrange
        String report = "{\"summary\":\"Resumen sin cifras.\",\"insights\":\"texto\"}";

        // Act
        AiProviderException exception = generateExpectingFailure(envelope(report));

        // Assert
        assertEquals("Gemini devolvió una lista inválida: insights", exception.getMessage());
    }

    /** textArray: se conservan como máximo 5 elementos. */
    @Test
    void shouldLimitListsToFiveItems() throws Exception {
        // Arrange
        String report = reportJson("Resumen sin cifras.", List.of(
                "Punto uno", "Punto dos", "Punto tres", "Punto cuatro", "Punto cinco", "Punto seis", "Punto siete"));

        // Act
        InsightReport result = generateFromReportText(report, eventAnalytics());

        // Assert
        assertEquals(5, result.insights().size());
        assertEquals("Punto cinco", result.insights().get(4));
    }

    /** textArray: se omiten elementos no textuales o en blanco. */
    @Test
    void shouldSkipNonTextualAndBlankListItems() throws Exception {
        // Arrange
        String report = "{\"summary\":\"Resumen sin cifras.\",\"insights\":[12,\"   \",\"Observación válida\",null,true]}";

        // Act
        InsightReport result = generateFromReportText(report, eventAnalytics());

        // Assert
        assertEquals(List.of("Observación válida"), result.insights());
    }

    /** textArray: un elemento más largo que el límite se recorta. */
    @Test
    void shouldTruncateListItemLongerThanLimit() throws Exception {
        // Arrange
        String report = reportJson("Resumen sin cifras.", List.of("x".repeat(900)));

        // Act
        InsightReport result = generateFromReportText(report, eventAnalytics());

        // Assert
        assertEquals(800, result.insights().get(0).length());
        assertTrue(result.insights().get(0).endsWith("…"));
    }

    // ---- groundedSummary / firstUngroundedNumber / keepGroundedClaims ------

    /** groundedSummary: se conservan las frases respaldadas y se descartan las demás. */
    @Test
    void shouldKeepOnlyGroundedSentencesOfSummary() throws Exception {
        // Arrange
        String report = reportJson("La asistencia fue 75%. Hubo 99 quejas.", List.of());

        // Act
        InsightReport result = generateFromReportText(report, eventAnalytics());

        // Assert
        assertEquals("La asistencia fue 75%.", result.summary());
    }

    /** groundedSummary: sin frases respaldadas se usa el resumen neutro. */
    @Test
    void shouldUseNeutralSummaryWhenNoSentenceIsGrounded() throws Exception {
        // Arrange
        String report = reportJson("Hubo 99 quejas.", List.of());

        // Act
        InsightReport result = generateFromReportText(report, eventAnalytics());

        // Assert
        assertEquals("Resumen basado en las métricas registradas del evento. Revisa las observaciones y recomendaciones.",
                result.summary());
    }

    /** addDerivedVariants: acepta redondeos y el final del rango horario; firstUngroundedNumber ignora ":00". */
    @Test
    void shouldAcceptRoundedValuesAndHourRangeEndAsGrounded() throws Exception {
        // Arrange
        String rounded = "El promedio supera los 4 puntos.";
        String hourRange = "El pico fue entre las 18:00 y las 19:00.";
        String ungrounded = "Hubo 21 asistentes sin registro.";
        String report = reportJson("Resumen sin cifras.", List.of(rounded, hourRange, ungrounded));

        // Act
        InsightReport result = generateFromReportText(report, eventAnalytics());

        // Assert
        assertEquals(List.of(rounded, hourRange), result.insights());
    }

    /** firstUngroundedNumber: "00" al inicio del texto o sin ":" previo no se ignora y no está respaldado. */
    @ParameterizedTest(name = "\"{0}\" se descarta por cifra no respaldada")
    @ValueSource(strings = {"00 asistentes confirmados.", "Hubo 00 asistentes."})
    void shouldDiscardDoubleZeroClaimsThatAreNotTimeSuffixes(String insight) throws Exception {
        // Arrange
        String report = reportJson("Resumen sin cifras.", List.of(insight));

        // Act
        InsightReport result = generateFromReportText(report, eventAnalytics());

        // Assert
        assertEquals(List.of(), result.insights());
    }

    /** keepGroundedClaims: las partes de la fecha del evento respaldan cifras (03 se normaliza a 3). */
    @Test
    void shouldAcceptNumbersFromEventDate() throws Exception {
        // Arrange
        String insight = "El evento se realizó el 03/10/2026.";
        String report = reportJson("Resumen sin cifras.", List.of(insight));

        // Act
        InsightReport result = generateFromReportText(report, eventAnalytics());

        // Assert
        assertEquals(List.of(insight), result.insights());
    }

    /** keepGroundedClaims: sin fecha de evento, el año ya no está respaldado. */
    @Test
    void shouldDiscardYearWhenEventHasNoDate() throws Exception {
        // Arrange
        Map<String, Object> analytics = analyticsWithEvent(Map.of("id", 9L, "name", "Evento real"));
        String report = reportJson("Resumen sin cifras.", List.of("El evento se realizó en 2026."));

        // Act
        InsightReport result = generateFromReportText(report, analytics);

        // Assert
        assertEquals(List.of(), result.insights());
    }

    /** keepGroundedClaims: un prefijo no numérico en la fecha genera un fragmento vacío que se ignora. */
    @Test
    void shouldIgnoreNonDigitPrefixInEventDate() throws Exception {
        // Arrange
        Map<String, Object> analytics = analyticsWithEvent(
                Map.of("id", 9L, "name", "Evento real", "event_date", "Sáb 2026-10-03"));
        String insight = "El evento fue en 2026.";
        String report = reportJson("Resumen sin cifras.", List.of(insight));

        // Act
        InsightReport result = generateFromReportText(report, analytics);

        // Assert
        assertEquals(List.of(insight), result.insights());
    }

    /** normalizeNumber: una coma decimal equivale al punto decimal. */
    @Test
    void shouldNormalizeCommaDecimalNumbers() throws Exception {
        // Arrange
        String insight = "La calificación fue 4,5.";
        String report = reportJson("Resumen sin cifras.", List.of(insight));

        // Act
        InsightReport result = generateFromReportText(report, eventAnalytics());

        // Assert
        assertEquals(List.of(insight), result.insights());
    }

    // ---- collectNumbers / addDerivedVariants -------------------------------

    /** collectNumbers: los valores textuales con dígitos (p. ej. "Zona 7") respaldan cifras. */
    @Test
    void shouldCollectNumbersFromTextualContextValues() throws Exception {
        // Arrange
        Map<String, Object> analytics = new HashMap<>(eventAnalytics());
        analytics.put("activities", List.of(Map.of(
                "type", "Zona 7", "activity_count", 1L, "total_quantity", 2L, "participants", 3L)));
        String insight = "Hubo actividad en la zona 7.";
        String report = reportJson("Resumen sin cifras.", List.of(insight));

        // Act
        InsightReport result = generateFromReportText(report, analytics);

        // Assert
        assertEquals(List.of(insight), result.insights());
    }

    /** collectNumbers: una métrica ausente (valor null) se ignora sin afectar la validación. */
    @Test
    void shouldIgnoreMissingMetricValuesWhenCollectingNumbers() throws Exception {
        // Arrange
        Map<String, Object> analytics = analyticsWithMetric("nps", null);
        String insight = "Asistieron 15 personas.";
        String report = reportJson("Resumen sin cifras.", List.of(insight));

        // Act
        InsightReport result = generateFromReportText(report, analytics);

        // Assert
        assertEquals(List.of(insight), result.insights());
    }

    /** addDerivedVariants: un valor no finito (NaN) no es numérico y no rompe la validación. */
    @Test
    void shouldIgnoreNonFiniteMetricValuesWhenBuildingKnownNumbers() throws Exception {
        // Arrange
        Map<String, Object> analytics = analyticsWithMetric("attendance_rate", Double.NaN);
        String insight = "Asistieron 15 personas.";
        String report = reportJson("Resumen sin cifras.", List.of(insight));

        // Act
        InsightReport result = generateFromReportText(report, analytics);

        // Assert
        assertEquals(List.of(insight), result.insights());
    }

    /** addDerivedVariants: sin horas pico no se inventa el final del rango horario. */
    @Test
    void shouldNotInventHourRangeEndWhenThereAreNoPeakHours() throws Exception {
        // Arrange
        Map<String, Object> analytics = analyticsWithMetric("peakAttendanceHours", null);
        String report = reportJson("Resumen sin cifras.", List.of("El pico fue a las 19."));

        // Act
        InsightReport result = generateFromReportText(report, analytics);

        // Assert
        assertEquals(List.of(), result.insights());
    }

    /** addDerivedVariants: entradas de hora pico mal formadas (no mapa u hora no numérica) se ignoran. */
    @Test
    void shouldIgnoreMalformedPeakHourEntries() throws Exception {
        // Arrange
        Map<String, Object> analytics = analyticsWithMetric("peakAttendanceHours",
                List.of("texto", Map.of("hour", "18", "attendees", 8L)));
        String report = reportJson("Resumen sin cifras.", List.of("El pico fue a las 19."));

        // Act
        InsightReport result = generateFromReportText(report, analytics);

        // Assert
        assertEquals(List.of(), result.insights());
    }

    // ---- compactContext ----------------------------------------------------

    /** compactContext: actividades y horas pico ausentes se envían como listas vacías. */
    @Test
    void shouldSendEmptyListsWhenActivitiesAndPeakHoursAreAbsent() throws Exception {
        // Arrange
        Map<String, Object> analytics = analyticsWithMetric("peakAttendanceHours", null);
        analytics.remove("activities");

        // Act
        String prompt = captureRequestPrompt(analytics);

        // Assert
        assertTrue(prompt.contains("\"activities\":[]"));
        assertTrue(prompt.contains("\"peakAttendanceHours\":[]"));
    }

    /** compactContext: se envían como máximo 8 actividades y 5 horas pico. */
    @Test
    void shouldLimitActivitiesAndPeakHoursSentToGemini() throws Exception {
        // Arrange
        List<Map<String, Object>> activities = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            activities.add(Map.of("type", "Zona", "activity_count", 1L, "total_quantity", 2L, "participants", 3L));
        }
        List<Map<String, Object>> hours = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            hours.add(Map.of("hour", i, "attendees", 1L));
        }
        Map<String, Object> analytics = analyticsWithMetric("peakAttendanceHours", hours);
        analytics.put("activities", activities);

        // Act
        String prompt = captureRequestPrompt(analytics);

        // Assert
        assertEquals(8, occurrences(prompt, "\"activityCount\""));
        assertEquals(5, occurrences(prompt, "\"hour\":"));
    }

    // ---- writeJson / constructor -------------------------------------------

    /** writeJson: si el contexto no se puede serializar se informa sin llamar a Gemini. */
    @Test
    void shouldFailWhenContextCannotBeSerialized() {
        // Arrange
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new JsonProcessingException("fallo simulado") { };
            }
        };
        GeminiAiInsightProvider provider = new GeminiAiInsightProvider(RestClient.builder(), failingMapper, "test-only-key");

        // Act
        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.generate(eventAnalytics()));

        // Assert
        assertEquals("No fue posible preparar el contexto de métricas para Gemini.", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    /** Constructor: una clave nula se trata como no configurada. */
    @Test
    void shouldTreatNullApiKeyAsNotConfigured() {
        // Arrange
        GeminiAiInsightProvider provider = new GeminiAiInsightProvider(RestClient.builder(), objectMapper, null);

        // Act
        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.generate(eventAnalytics()));

        // Assert
        assertFalse(provider.isConfigured());
        assertEquals("GEMINI_API_KEY no está configurada.", exception.getMessage());
    }

    // ---- utilidades de prueba ----------------------------------------------

    private String envelope(String generatedText) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of("candidates", List.of(Map.of("content",
                Map.of("parts", List.of(Map.of("text", generatedText)))))));
    }

    private String reportJson(String summary, List<String> insights) throws JsonProcessingException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("summary", summary);
        report.put("insights", insights);
        report.put("anomalies", List.of());
        report.put("recommendations", List.of());
        return objectMapper.writeValueAsString(report);
    }

    private InsightReport generateFromReportText(String generatedText, Map<String, Object> analytics) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(envelope(generatedText), MediaType.APPLICATION_JSON));
        InsightReport report = new GeminiAiInsightProvider(builder, objectMapper, "test-only-key").generate(analytics);
        server.verify();
        return report;
    }

    private AiProviderException generateExpectingFailure(String rawResponse) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL)).andRespond(withSuccess(rawResponse, MediaType.APPLICATION_JSON));
        GeminiAiInsightProvider provider = new GeminiAiInsightProvider(builder, objectMapper, "test-only-key");
        AiProviderException exception = assertThrows(AiProviderException.class,
                () -> provider.generate(eventAnalytics()));
        server.verify();
        return exception;
    }

    private String captureRequestPrompt(Map<String, Object> analytics) throws Exception {
        AtomicReference<String> prompt = new AtomicReference<>();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    prompt.set(objectMapper.readTree(body).path("contents").get(0)
                            .path("parts").get(0).path("text").asText());
                })
                .andRespond(withSuccess(envelope(VALID_REPORT), MediaType.APPLICATION_JSON));
        new GeminiAiInsightProvider(builder, objectMapper, "test-only-key").generate(analytics);
        server.verify();
        return prompt.get();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> analyticsWithMetric(String key, Object value) {
        Map<String, Object> analytics = new HashMap<>(eventAnalytics());
        Map<String, Object> metrics = new HashMap<>((Map<String, Object>) analytics.get("metrics"));
        if (value == null) {
            metrics.remove(key);
        } else {
            metrics.put(key, value);
        }
        analytics.put("metrics", metrics);
        return analytics;
    }

    private static Map<String, Object> analyticsWithEvent(Map<String, Object> event) {
        Map<String, Object> analytics = new HashMap<>(eventAnalytics());
        analytics.put("event", event);
        return analytics;
    }

    private static int occurrences(String text, String fragment) {
        int count = 0;
        for (int i = text.indexOf(fragment); i >= 0; i = text.indexOf(fragment, i + fragment.length())) {
            count++;
        }
        return count;
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