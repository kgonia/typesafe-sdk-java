package io.github.kgonia.typesafe.internal.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTest {

    enum Color { RED }

    record Point(int x, int y) {
    }

    record Ticket(String subject, List<String> tags, Point origin, Optional<String> note) {
    }

    @Test
    void parsesObjectsArraysAndScalars() {
        Object value = Json.parse(" {\"a\": [1, 2.5, -3e2, true, false, null, \"s\"], \"b\": {\"c\": {}}} ");
        Map<?, ?> map = assertInstanceOf(Map.class, value);
        List<?> a = assertInstanceOf(List.class, map.get("a"));
        assertEquals(List.of(1L, 2.5, -300.0, true, false), a.subList(0, 5));
        assertNull(a.get(5));
        assertEquals("s", a.get(6));
        assertEquals(Map.of("c", Map.of()), map.get("b"));
    }

    @Test
    void preservesKeyOrderAndLastDuplicateWins() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"z\":1,\"a\":2,\"z\":3}");
        assertEquals(List.of("z", "a"), List.copyOf(map.keySet()));
        assertEquals(3L, map.get("z"));
    }

    @Test
    void parsesEscapesAndSurrogatePairs() {
        assertEquals("a\"b\\c/d\b\f\n\r\t\u00e9\uD83D\uDE00", Json.parse(
                "\"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\\u00e9\\ud83d\\ude00\""));
    }

    @Test
    void parsesNumbersIntoNaturalTypes() {
        assertEquals(0L, Json.parse("0"));
        assertEquals(-12L, Json.parse("-12"));
        assertEquals(Long.MAX_VALUE, Json.parse(String.valueOf(Long.MAX_VALUE)));
        assertEquals(new BigInteger("92233720368547758070"), Json.parse("92233720368547758070"));
        assertEquals(1.5, Json.parse("1.5"));
        assertEquals(1e3, Json.parse("1E3"));
        assertEquals(0.85, Json.parse("0.85"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "{", "[1,]", "{\"a\":}", "{a:1}", "01", "1.", "1e", "-", "tru", "nul", "\"abc",
            "\"\\x\"", "\"\\u12\"", "[1] 2", "{\"a\":1,}", "\"tab\there\"", "NaN", "+1"})
    void rejectsInvalidJson(String text) {
        assertThrows(JsonException.class, () -> Json.parse(text));
    }

    @Test
    void errorMessagesIncludePosition() {
        JsonException e = assertThrows(JsonException.class, () -> Json.parse("[1, 2 3]"));
        assertTrue(e.getMessage().contains("position 6"), e.getMessage());
    }

    @Test
    void parsedContainersAreUnmodifiable() {
        Map<?, ?> map = (Map<?, ?>) Json.parse("{\"a\":[1]}");
        assertThrows(UnsupportedOperationException.class, () -> ((Map<Object, Object>) map).put("b", 1));
        assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) map.get("a")).add(1));
    }

    @Test
    void writesMapsListsScalarsInOrder() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("z", List.of(1, 2.5, true));
        map.put("a", null);
        map.put("s", "q\"\n\u0001");
        assertEquals("{\"z\":[1,2.5,true],\"a\":null,\"s\":\"q\\\"\\n\\u0001\"}", Json.write(map));
    }

    @Test
    void writesRecordsEnumsOptionalsArraysAndTemporals() {
        Ticket ticket = new Ticket("Hi", List.of("x"), new Point(1, 2), Optional.empty());
        assertEquals("{\"subject\":\"Hi\",\"tags\":[\"x\"],\"origin\":{\"x\":1,\"y\":2},\"note\":null}",
                Json.write(ticket));
        assertEquals("\"RED\"", Json.write(Color.RED));
        assertEquals("{\"RED\":1}", Json.write(Map.of(Color.RED, 1)));
        assertEquals("[1,2]", Json.write(new int[] {1, 2}));
        assertEquals("[\"a\",null]", Json.write(new String[] {"a", null}));
        assertEquals("7", Json.write(OptionalInt.of(7)));
        assertEquals("null", Json.write(OptionalInt.empty()));
        assertEquals("\"2020-01-01T00:00:00Z\"", Json.write(Instant.parse("2020-01-01T00:00:00Z")));
        UUID uuid = UUID.randomUUID();
        assertEquals("\"" + uuid + "\"", Json.write(uuid));
        assertEquals("[1,2]", Json.write(Arrays.asList(1, 2)));
        assertEquals("\"c\"", Json.write('c'));
        assertEquals("1.5", Json.write(new java.math.BigDecimal("1.5")));
    }

    @Test
    void rejectsUnsupportedValues() {
        assertThrows(JsonException.class, () -> Json.write(new Object()));
        assertThrows(JsonException.class, () -> Json.write(Double.NaN));
        assertThrows(JsonException.class, () -> Json.write(Double.POSITIVE_INFINITY));
        assertThrows(JsonException.class, () -> Json.write(Float.NaN));
        assertThrows(JsonException.class, () -> Json.write(List.of(new Thread())));
    }

    @Test
    void detectsCycles() {
        List<Object> list = new java.util.ArrayList<>();
        list.add(list);
        JsonException e = assertThrows(JsonException.class, () -> Json.write(list));
        assertTrue(e.getMessage().contains("cycle"), e.getMessage());
    }

    @Test
    void roundTrips() {
        String text = "{\"state\":{\"doc\":\"h\u00e9llo \\\"w\\\"\"},\"n\":[0.85,1,-2,1.0E-7],\"t\":true,\"x\":null}";
        assertEquals(text, Json.write(Json.parse(text)));
    }
}
