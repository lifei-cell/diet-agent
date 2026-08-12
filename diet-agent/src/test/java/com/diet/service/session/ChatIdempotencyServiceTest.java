package com.diet.service.session;

import com.diet.enums.SourceMode;
import com.diet.mapper.ChatIdempotencyMapper;
import com.diet.model.ChatIdempotencyRow;
import com.diet.model.ChatRequest;
import com.diet.model.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatIdempotencyServiceTest {

    @Test
    void returnsPersistedResponseForACompletedReplay() throws Exception {
        ChatIdempotencyMapper mapper = mock(ChatIdempotencyMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ChatIdempotencyService service = new ChatIdempotencyService(mapper, objectMapper, 90);
        ChatRequest request = new ChatRequest("req-1", "sess-1", "晚饭吃什么", SourceMode.PUBLIC, null, null);
        String hash = service.requestHash(request);
        ChatResponse expected = ChatResponse.answer("sess-1", "trace-1", "试试鸡胸肉轻食碗", java.util.List.of(), "WAIT_USER");
        ChatIdempotencyRow row = new ChatIdempotencyRow();
        row.setSessionId("sess-1");
        row.setRequestHash(hash);
        row.setStatus("COMPLETED");
        row.setResponseJson(objectMapper.writeValueAsString(expected));
        when(mapper.find(1L, "req-1")).thenReturn(row);

        ChatResponse actual = service.findCompleted(1L, "sess-1", "req-1", hash);

        assertEquals(expected.speechText(), actual.speechText());
        assertEquals(expected.traceId(), actual.traceId());
    }

    @Test
    void rejectsRequestIdReuseForDifferentPayload() {
        ChatIdempotencyMapper mapper = mock(ChatIdempotencyMapper.class);
        ChatIdempotencyService service = new ChatIdempotencyService(mapper, new ObjectMapper(), 90);
        ChatIdempotencyRow row = new ChatIdempotencyRow();
        row.setSessionId("sess-1");
        row.setRequestHash("original-hash");
        row.setStatus("PROCESSING");
        when(mapper.find(1L, "req-1")).thenReturn(row);

        assertThrows(RuntimeException.class,
                () -> service.begin(1L, "sess-1", "req-1", "different-hash"));
    }

    @Test
    void persistsNewRequestOwnership() {
        ChatIdempotencyMapper mapper = mock(ChatIdempotencyMapper.class);
        ChatIdempotencyService service = new ChatIdempotencyService(mapper, new ObjectMapper(), 90);
        when(mapper.find(1L, "req-1")).thenReturn(null);

        ChatIdempotencyService.StartResult result = service.begin(1L, "sess-1", "req-1", "hash");

        assertEquals(ChatIdempotencyService.Status.OWNER, result.status());
        verify(mapper).insert(any(ChatIdempotencyRow.class));
    }
}
