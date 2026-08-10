package com.diet.service.checkin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodImageRecognitionServiceTest {

    @Test
    void shouldReturnEditableManualDraftWhenVisionProviderIsNotConfigured() {
        FoodImageRecognitionService service = new FoodImageRecognitionService(
                new ObjectMapper(), "", "qwen3.7-plus", "https://example.invalid/chat/completions");

        FoodImageRecognitionService.RecognitionResult result = service.recognize(new byte[]{1, 2, 3}, "image/png");

        assertFalse(result.automated());
        assertTrue(result.items().isEmpty());
        assertTrue(result.message().contains("手动"));
    }
}
