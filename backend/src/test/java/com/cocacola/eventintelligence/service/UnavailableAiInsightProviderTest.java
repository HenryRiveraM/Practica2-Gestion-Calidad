package com.cocacola.eventintelligence.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cocacola.eventintelligence.exception.AiProviderException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnavailableAiInsightProviderTest {

    private UnavailableAiInsightProvider provider;

    @BeforeEach
    void setUp() {
        provider = new UnavailableAiInsightProvider();
    }

    @Test
    void isConfigured_returnsFalse() {
        assertFalse(provider.isConfigured());
    }

    @Test
    void generate_throwsAiProviderExceptionWithExplanatoryMessage() {
        AiProviderException thrown = assertThrows(
                AiProviderException.class,
                () -> provider.generate(Map.of("registered", 20L)));

        assertEquals("No hay un proveedor de IA configurado.", thrown.getMessage());
    }

    @Test
    void generate_throwsEvenWhenAnalyticsAreEmptyOrNull() {
        assertThrows(AiProviderException.class, () -> provider.generate(Map.of()));
        assertThrows(AiProviderException.class, () -> provider.generate(null));
    }

    @Test
    void providerName_returnsUnavailable() {
        assertEquals("unavailable", provider.providerName());
    }

    @Test
    void modelName_returnsNone() {
        assertEquals("none", provider.modelName());
    }

    @Test
    void shouldBeUsableAsAiInsightProvider() {
        AiInsightProvider asInterface = provider;

        assertTrue(asInterface instanceof UnavailableAiInsightProvider);
        assertFalse(asInterface.isConfigured());
    }
}