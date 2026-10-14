package com.cocacola.eventintelligence.service;

import com.cocacola.eventintelligence.exception.AiProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GeminiAiInsightProvider implements AiInsightProvider {
    private static final String MODEL = "gemini-3.5-flash-lite";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL + ":generateContent";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![\\p{L}\\w])[-+]?\\d+(?:[.,]\\d+)?");
    private static final int MAX_TEXT_LENGTH = 800;
    private static final int MAX_LIST_ITEMS = 5;
    private static final int MAX_SUMMARY_LENGTH = 400;
    // Los modelos Gemini 2.5/3.x cuentan los tokens de "thinking" dentro de maxOutputTokens;
    // con 700 el JSON quedaba cortado y fallaba el parseo.
    private static final int MAX_OUTPUT_TOKENS = 2048;

    private final String apiKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiAiInsightProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${GEMINI_API_KEY:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public InsightReport generate(Map<String, Object> eventAnalytics) {
        if (!isConfigured()) {
            throw new AiProviderException("GEMINI_API_KEY no está configurada.");
        }

        Map<String, Object> context = compactContext(eventAnalytics);
        String prompt = """
                Analiza el siguiente JSON de métricas reales de un evento para marketing y organización.
                Usa exclusivamente los datos proporcionados. No calcules métricas nuevas, no inventes cifras,
                causas, comparaciones ni hechos. Si faltan datos, indícalo en el resumen o en la observación
                correspondiente. Las conclusiones deben distinguir observaciones de hipótesis y las recomendaciones
                deben ser acciones concretas basadas en observaciones. Devuelve únicamente un objeto JSON válido
                con las propiedades: summary (cadena breve), insights (arreglo de cadenas, máximo 5),
                anomalies (arreglo de cadenas, máximo 5) y recommendations (arreglo de cadenas, máximo 5).
                Limita cada cadena a 240 caracteres y el resumen a 400 caracteres.
                Escribe las cifras exactamente como aparecen en el JSON, sin redondear, sin sumar ni derivar valores
                y sin mencionar números que no estén en los datos (por ejemplo, no cuentes elementos de una lista).
                Datos:
                """ + writeJson(context);

        Map<String, Object> request = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", MAX_OUTPUT_TOKENS,
                        "responseMimeType", "application/json"));

        try {
            JsonNode response = restClient.post()
                    .uri(API_URL)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            String generatedText = extractText(response);
            InsightReport report = parseReport(generatedText);
            return keepGroundedClaims(report, context);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            throw new AiProviderException(
                    "Gemini respondió con HTTP " + status + ".",
                    clientMessageForStatus(ex.getStatusCode()),
                    ex);
        } catch (RestClientException | IllegalArgumentException ex) {
            throw new AiProviderException(
                    "No fue posible conectar con Gemini.",
                    "No se pudo conectar con Gemini. Comprueba la conexión a internet del backend y vuelve a intentarlo.",
                    ex);
        }
    }

    private static String clientMessageForStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Gemini rechazó la solicitud (HTTP 400). Verifica que la clave sea válida y que Gemini API esté habilitada en el proyecto asociado.";
            case 401, 403 -> "Gemini rechazó la autenticación o el acceso (HTTP " + status.value()
                    + "). Verifica que la clave esté activa y que sus restricciones permitan Gemini API.";
            case 404 -> "Gemini no encontró el modelo o endpoint solicitado (HTTP 404). Verifica la disponibilidad del modelo para tu proyecto.";
            case 429 -> "Gemini alcanzó el límite de uso o cuota (HTTP 429). Revisa la cuota del proyecto y vuelve a intentarlo más tarde.";
            default -> status.is5xxServerError()
                    ? "Gemini presenta una interrupción temporal (HTTP " + status.value() + "). Intenta nuevamente más tarde."
                    : "Gemini rechazó la solicitud (HTTP " + status.value() + "). Revisa la configuración de la API y vuelve a intentarlo.";
        };
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public String modelName() {
        return MODEL;
    }

    private Map<String, Object> compactContext(Map<String, Object> eventAnalytics) {
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) eventAnalytics.get("event");
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) eventAnalytics.get("metrics");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> activities = (List<Map<String, Object>>) eventAnalytics.get("activities");

        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("eventId", event.get("id"));
        compact.put("eventName", event.get("name"));
        compact.put("eventDate", event.get("event_date"));

        Map<String, Object> compactMetrics = new LinkedHashMap<>();
        for (String key : List.of(
                "registered", "attendees", "attendance_rate", "conversions", "conversion_rate",
                "average_rating", "nps", "new_participants", "recurrent_participants",
                "engaged_participants", "engagement_rate")) {
            compactMetrics.put(key, metrics.get(key));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> peakHours = (List<Map<String, Object>>) metrics.get("peakAttendanceHours");
        compactMetrics.put("peakAttendanceHours", peakHours == null ? List.of() : peakHours.stream().limit(5).toList());
        compact.put("metrics", compactMetrics);
        compact.put("activities", activities == null ? List.of() : activities.stream().limit(8)
                .map(activity -> {
                    Map<String, Object> compactActivity = new LinkedHashMap<>();
                    compactActivity.put("type", activity.get("type"));
                    compactActivity.put("activityCount", activity.get("activity_count"));
                    compactActivity.put("totalQuantity", activity.get("total_quantity"));
                    compactActivity.put("participants", activity.get("participants"));
                    return compactActivity;
                })
                .toList());
        return compact;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new AiProviderException("No fue posible preparar el contexto de métricas para Gemini.", ex);
        }
    }

    private String extractText(JsonNode response) {
        JsonNode candidates = response == null ? null : response.path("candidates");
        if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
            throw new AiProviderException("Gemini devolvió una respuesta sin contenido.");
        }
        JsonNode candidate = candidates.get(0);
        if ("MAX_TOKENS".equals(candidate.path("finishReason").asText(""))) {
            throw new AiProviderException(
                    "Gemini cortó la respuesta por límite de tokens (finishReason=MAX_TOKENS).",
                    "Gemini cortó la respuesta antes de terminar. Vuelve a intentarlo.",
                    null);
        }
        JsonNode parts = candidate.path("content").path("parts");
        if (!parts.isArray()) {
            throw new AiProviderException("Gemini devolvió una respuesta con formato inválido.");
        }
        for (JsonNode part : parts) {
            JsonNode text = part.get("text");
            if (text != null && text.isTextual() && !text.asText().isBlank()) {
                return text.asText();
            }
        }
        throw new AiProviderException("Gemini no devolvió texto de análisis.");
    }

    private InsightReport parseReport(String json) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(json));
            return new InsightReport(
                    requiredText(root, "summary", MAX_SUMMARY_LENGTH),
                    textArray(root, "insights"),
                    textArray(root, "anomalies"),
                    textArray(root, "recommendations"));
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiProviderException("Gemini devolvió un reporte que no es JSON válido.", ex);
        }
    }

    private static String stripCodeFence(String raw) {
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewLine = text.indexOf('\n');
            text = firstNewLine < 0 ? "" : text.substring(firstNewLine + 1);
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        return text.trim();
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1).trim() + "…";
    }

    private static String requiredText(JsonNode root, String key, int maxLength) {
        JsonNode value = root.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new AiProviderException("Gemini omitió el campo obligatorio: " + key);
        }
        // Un texto largo no es una cifra inventada: se recorta en lugar de descartar todo el reporte.
        return truncate(value.asText().trim(), maxLength);
    }

    private static List<String> textArray(JsonNode root, String key) {
        JsonNode values = root.get(key);
        if (values == null || values.isNull()) {
            return List.of();
        }
        if (!values.isArray()) {
            throw new AiProviderException("Gemini devolvió una lista inválida: " + key);
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (result.size() >= MAX_LIST_ITEMS) {
                break;
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                result.add(truncate(value.asText().trim(), MAX_TEXT_LENGTH));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Valida que las cifras del texto generado existan en los datos enviados.
     * Un resumen con una cifra inventada invalida el reporte; en las listas se descartan
     * solo los elementos con cifras no respaldadas, para no perder todo el análisis por una frase.
     */
    private InsightReport keepGroundedClaims(InsightReport report, Map<String, Object> context) {
        Set<String> knownNumbers = new LinkedHashSet<>();
        collectNumbers(context.get("metrics"), knownNumbers);
        collectNumbers(context.get("activities"), knownNumbers);
        addDerivedVariants(knownNumbers, context.get("metrics"));
        Object eventDate = context.get("eventDate");
        if (eventDate != null) {
            for (String part : eventDate.toString().split("\\D+")) {
                if (!part.isBlank()) {
                    knownNumbers.add(normalizeNumber(part));
                }
            }
        }

        return new InsightReport(
                groundedSummary(report.summary(), knownNumbers),
                groundedOnly(report.insights(), knownNumbers),
                groundedOnly(report.anomalies(), knownNumbers),
                groundedOnly(report.recommendations(), knownNumbers));
    }

    /**
     * Si el resumen contiene cifras no respaldadas, se descartan solo las frases afectadas.
     * Si no queda ninguna, se usa un resumen neutro sin cifras en lugar de fallar toda la solicitud.
     */
    private String groundedSummary(String summary, Set<String> knownNumbers) {
        if (firstUngroundedNumber(summary, knownNumbers) == null) {
            return summary;
        }
        String kept = java.util.Arrays.stream(summary.split("(?<=[.!?])\\s+"))
                .filter(sentence -> firstUngroundedNumber(sentence, knownNumbers) == null)
                .collect(java.util.stream.Collectors.joining(" "))
                .trim();
        return kept.isEmpty()
                ? "Resumen basado en las métricas registradas del evento. Revisa las observaciones y recomendaciones."
                : kept;
    }

    /** Variantes legítimas de una cifra real: redondeos de porcentajes y el fin de un rango horario. */
    private void addDerivedVariants(Set<String> numbers, Object metrics) {
        Set<String> variants = new LinkedHashSet<>();
        for (String known : numbers) {
            try {
                BigDecimal value = new BigDecimal(known);
                variants.add(value.setScale(0, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
                variants.add(value.setScale(0, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString());
                variants.add(value.setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            } catch (NumberFormatException ignored) {
                // no es numérico
            }
        }
        if (metrics instanceof Map<?, ?> map && map.get("peakAttendanceHours") instanceof Iterable<?> hours) {
            for (Object hour : hours) {
                if (hour instanceof Map<?, ?> row && row.get("hour") instanceof Number number) {
                    variants.add(String.valueOf((number.intValue() + 1) % 24));
                }
            }
        }
        numbers.addAll(variants);
    }

    private List<String> groundedOnly(List<String> items, Set<String> knownNumbers) {
        return items.stream().filter(item -> firstUngroundedNumber(item, knownNumbers) == null).toList();
    }

    private String firstUngroundedNumber(String text, Set<String> knownNumbers) {
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            if ("00".equals(matcher.group()) && matcher.start() > 0 && text.charAt(matcher.start() - 1) == ':') {
                continue;
            }
            String numericClaim = normalizeNumber(matcher.group());
            if (!knownNumbers.contains(numericClaim)) {
                return matcher.group();
            }
        }
        return null;
    }

    private void collectNumbers(Object value, Set<String> numbers) {
        if (value instanceof Number number) {
            numbers.add(normalizeNumber(number.toString()));
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> collectNumbers(item, numbers));
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> collectNumbers(item, numbers));
        } else if (value != null) {
            Matcher matcher = NUMBER_PATTERN.matcher(value.toString());
            while (matcher.find()) {
                numbers.add(normalizeNumber(matcher.group()));
            }
        }
    }

    private static String normalizeNumber(String value) {
        try {
            return new BigDecimal(value.replace(',', '.')).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            return value;
        }
    }
}