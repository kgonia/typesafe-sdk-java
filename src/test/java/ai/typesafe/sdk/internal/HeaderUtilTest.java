package ai.typesafe.sdk.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeaderUtilTest {

    @Test
    void mergesCaseInsensitivelyWithLastWriterWinning() {
        Map<String, String> merged = HeaderUtil.merge(Map.of("X-A", "1", "x-b", "2"), Map.of("x-a", "3"));
        assertEquals(Map.of("x-b", "2", "x-a", "3"), merged);
        assertEquals(List.of("x-b", "x-a"), List.copyOf(merged.keySet()));
    }

    @Test
    void nullValueRemovesHeader() {
        Map<String, String> removal = new HashMap<>();
        removal.put("Content-Type", null);
        Map<String, String> merged = HeaderUtil.merge(Map.of("content-type", "x", "Accept", "y"), removal);
        assertFalse(merged.containsKey("content-type"));
        assertEquals(Map.of("Accept", "y"), merged);
    }

    @Test
    void redactsCredentials() {
        assertEquals("Bearer ***7890", HeaderUtil.redactValue("Authorization", "Bearer sk-1234567890"));
        assertEquals("Bearer ***", HeaderUtil.redactValue("authorization", "Bearer short"));
        assertEquals("***cdef", HeaderUtil.redactValue("X-API-Key", "0123456789abcdef"));
        assertEquals("***", HeaderUtil.redactValue("Cookie", "session=abc"));
        assertEquals("***", HeaderUtil.redactValue("X-Access-Token", "abc"));
        assertEquals("***", HeaderUtil.redactValue("X-Secret-Thing", "abc"));
        assertEquals("application/json", HeaderUtil.redactValue("Content-Type", "application/json"));
    }
}
