package ai.typesafe.sdk.internal.json;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * A small, strict JSON codec with no dependencies.
 *
 * <p><b>Parsing</b> produces plain Java values: {@code Map<String, Object>} for objects (insertion ordered,
 * unmodifiable), {@code List<Object>} for arrays (unmodifiable), {@link String}, {@link Boolean}, {@code null},
 * and numbers as {@link Long} when integral and in range, {@link BigInteger} for larger integers, or
 * {@link Double} otherwise. Object keys may repeat; the last value wins.
 *
 * <p><b>Writing</b> accepts {@code null}, {@link CharSequence}, {@link Character}, {@link Boolean},
 * {@link Number}, {@link Enum} (written as its {@code name()}), {@link Map} (keys converted with
 * {@code String.valueOf}, or {@code name()} for enum keys), {@link Iterable}, arrays, {@link Optional} and its
 * primitive variants (unwrapped, empty written as {@code null}), records (written as objects keyed by component
 * name), and {@link Temporal}, {@link TemporalAmount}, {@link UUID}, and {@link URI} (written as strings).
 * Any other type raises {@link JsonException}. {@code NaN} and infinite numbers are rejected.
 *
 * <p>Record support reads record components reflectively. A modular application must open the record's package
 * to this module ({@code opens my.pkg to ai.typesafe.sdk;}) or pass a {@link Map} instead. On GraalVM native
 * image, register such records for reflection.
 */
public final class Json {

    private static final int MAX_DEPTH = 512;

    private Json() {
    }

    /**
     * Parses JSON text into plain Java values.
     *
     * @throws JsonException when the text is not valid JSON
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("JSON text must not be null.");
        }
        Parser parser = new Parser(text);
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    /**
     * Writes a value as compact JSON.
     *
     * @throws JsonException when the value contains something that cannot be written
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder(64);
        write(value, out);
        return out.toString();
    }

    /**
     * Appends a value as compact JSON to a {@link StringBuilder}.
     *
     * @throws JsonException when the value contains something that cannot be written
     */
    public static void write(Object value, StringBuilder out) {
        writeValue(value, out, 0);
    }

    // ---------------------------------------------------------------------------------------------
    // Writer
    // ---------------------------------------------------------------------------------------------

    private static void writeValue(Object value, StringBuilder out, int depth) {
        if (depth > MAX_DEPTH) {
            throw new JsonException("JSON nesting deeper than " + MAX_DEPTH + " levels; is there a cycle?");
        }
        switch (value) {
            case null -> out.append("null");
            case CharSequence s -> writeString(s, out);
            case Character c -> writeString(String.valueOf(c), out);
            case Boolean b -> out.append(b.booleanValue());
            case Number n -> writeNumber(n, out);
            case Enum<?> e -> writeString(e.name(), out);
            case Optional<?> o -> writeValue(o.orElse(null), out, depth);
            case OptionalInt o -> writeValue(o.isPresent() ? o.getAsInt() : null, out, depth);
            case OptionalLong o -> writeValue(o.isPresent() ? o.getAsLong() : null, out, depth);
            case OptionalDouble o -> writeValue(o.isPresent() ? o.getAsDouble() : null, out, depth);
            case Map<?, ?> m -> writeMap(m, out, depth);
            case Iterable<?> it -> writeIterable(it, out, depth);
            case Temporal t -> writeString(t.toString(), out);
            case TemporalAmount t -> writeString(t.toString(), out);
            case UUID u -> writeString(u.toString(), out);
            case URI u -> writeString(u.toString(), out);
            case Record r -> writeRecord(r, out, depth);
            default -> {
                if (value.getClass().isArray()) {
                    writeArray(value, out, depth);
                } else {
                    throw new JsonException("Cannot write " + value.getClass().getName()
                            + " as JSON. Pass a String, Number, Boolean, Map, List, array, record, or null.");
                }
            }
        }
    }

    private static void writeNumber(Number n, StringBuilder out) {
        switch (n) {
            case Double d -> {
                if (d.isNaN() || d.isInfinite()) {
                    throw new JsonException("Cannot write " + d + " as JSON.");
                }
                out.append(d.doubleValue());
            }
            case Float f -> {
                if (f.isNaN() || f.isInfinite()) {
                    throw new JsonException("Cannot write " + f + " as JSON.");
                }
                out.append(f.floatValue());
            }
            case BigDecimal bd -> out.append(bd.toPlainString());
            case Integer i -> out.append(i.intValue());
            case Long l -> out.append(l.longValue());
            case Short s -> out.append(s.shortValue());
            case Byte b -> out.append(b.byteValue());
            case BigInteger bi -> out.append(bi);
            default -> {
                double d = n.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    throw new JsonException("Cannot write " + n + " as JSON.");
                }
                out.append(n);
            }
        }
    }

    private static void writeMap(Map<?, ?> map, StringBuilder out, int depth) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            Object key = entry.getKey();
            String name = key instanceof Enum<?> e ? e.name() : String.valueOf(key);
            writeString(name, out);
            out.append(':');
            writeValue(entry.getValue(), out, depth + 1);
        }
        out.append('}');
    }

    private static void writeIterable(Iterable<?> iterable, StringBuilder out, int depth) {
        out.append('[');
        boolean first = true;
        for (Object element : iterable) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(element, out, depth + 1);
        }
        out.append(']');
    }

    private static void writeArray(Object array, StringBuilder out, int depth) {
        int length = java.lang.reflect.Array.getLength(array);
        out.append('[');
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                out.append(',');
            }
            writeValue(java.lang.reflect.Array.get(array, i), out, depth + 1);
        }
        out.append(']');
    }

    private static void writeRecord(Record record, StringBuilder out, int depth) {
        out.append('{');
        boolean first = true;
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(component.getName(), out);
            out.append(':');
            writeValue(readComponent(record, component), out, depth + 1);
        }
        out.append('}');
    }

    private static Object readComponent(Record record, RecordComponent component) {
        var accessor = component.getAccessor();
        try {
            try {
                return accessor.invoke(record);
            } catch (IllegalAccessException inaccessible) {
                accessor.setAccessible(true);
                return accessor.invoke(record);
            }
        } catch (InvocationTargetException e) {
            throw new JsonException("Accessor " + record.getClass().getName() + "." + component.getName()
                    + "() threw an exception.", e.getCause());
        } catch (IllegalAccessException | RuntimeException e) {
            throw new JsonException(accessHint(record.getClass(), component), e);
        }
    }

    /** Explains why a record component could not be read, with the module-system fix when that is the cause. */
    private static String accessHint(Class<?> type, RecordComponent component) {
        String base = "Cannot read record component " + type.getName() + "." + component.getName() + "().";
        Module module = type.getModule();
        String pkg = type.getPackageName();
        Module self = Json.class.getModule();
        if (module.isNamed() && !module.isOpen(pkg, self)) {
            String target = self.isNamed() ? self.getName() : "ALL-UNNAMED";
            return base + " The record is in module " + module.getName() + ", which does not open package " + pkg
                    + " to " + target + ". Add \"opens " + pkg + " to " + target + ";\" to its module-info.java,"
                    + " or pass a Map instead.";
        }
        return base + " Make the record public or pass a Map instead.";
    }

    private static void writeString(CharSequence s, StringBuilder out) {
        out.append('"');
        int length = s.length();
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00");
                        out.append(Character.forDigit(c >> 4, 16));
                        out.append(Character.forDigit(c & 0xF, 16));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    // ---------------------------------------------------------------------------------------------
    // Parser
    // ---------------------------------------------------------------------------------------------

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        JsonException error(String message) {
            return new JsonException(message + " at position " + pos + ".");
        }

        void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("JSON nesting deeper than " + MAX_DEPTH + " levels");
            }
            skipWhitespace();
            if (atEnd()) {
                throw error("Unexpected end of JSON text");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> readObject(depth);
                case '[' -> readArray(depth);
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield readNumber();
                    }
                    throw error("Unexpected character '" + c + "'");
                }
            };
        }

        private Object readLiteral(String literal, Object value) {
            if (text.startsWith(literal, pos)) {
                pos += literal.length();
                return value;
            }
            throw error("Invalid literal");
        }

        private Map<String, Object> readObject(int depth) {
            pos++; // '{'
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return Collections.unmodifiableMap(map);
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("Expected a string key");
                }
                String key = readString();
                skipWhitespace();
                if (peek() != ':') {
                    throw error("Expected ':'");
                }
                pos++;
                map.put(key, readValue(depth + 1));
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return Collections.unmodifiableMap(map);
                } else {
                    throw error("Expected ',' or '}'");
                }
            }
        }

        private List<Object> readArray(int depth) {
            pos++; // '['
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return Collections.unmodifiableList(list);
            }
            while (true) {
                list.add(readValue(depth + 1));
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return Collections.unmodifiableList(list);
                } else {
                    throw error("Expected ',' or ']'");
                }
            }
        }

        private char peek() {
            if (atEnd()) {
                throw error("Unexpected end of JSON text");
            }
            return text.charAt(pos);
        }

        private String readString() {
            pos++; // opening quote
            StringBuilder sb = null;
            int start = pos;
            while (true) {
                if (atEnd()) {
                    throw error("Unterminated string");
                }
                char c = text.charAt(pos);
                if (c == '"') {
                    String result = sb == null ? text.substring(start, pos) : sb.append(text, start, pos).toString();
                    pos++;
                    return result;
                }
                if (c < 0x20) {
                    throw error("Unescaped control character in string");
                }
                if (c == '\\') {
                    if (sb == null) {
                        sb = new StringBuilder();
                    }
                    sb.append(text, start, pos);
                    pos++;
                    if (atEnd()) {
                        throw error("Unterminated escape sequence");
                    }
                    char e = text.charAt(pos++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> sb.append(readUnicodeEscape());
                        default -> throw error("Invalid escape character '\\" + e + "'");
                    }
                    start = pos;
                } else {
                    pos++;
                }
            }
        }

        private char readUnicodeEscape() {
            if (pos + 4 > text.length()) {
                throw error("Incomplete unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(text.charAt(pos + i), 16);
                if (digit < 0) {
                    throw error("Invalid unicode escape");
                }
                value = (value << 4) | digit;
            }
            pos += 4;
            return (char) value;
        }

        private Number readNumber() {
            int start = pos;
            if (text.charAt(pos) == '-') {
                pos++;
            }
            if (atEnd()) {
                throw error("Invalid number");
            }
            char first = text.charAt(pos);
            if (first == '0') {
                pos++;
            } else if (first >= '1' && first <= '9') {
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            } else {
                throw error("Invalid number");
            }
            boolean integral = true;
            if (!atEnd() && text.charAt(pos) == '.') {
                integral = false;
                pos++;
                int digitsStart = pos;
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
                if (pos == digitsStart) {
                    throw error("Invalid number: expected digits after '.'");
                }
            }
            if (!atEnd() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                integral = false;
                pos++;
                if (!atEnd() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                int digitsStart = pos;
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
                if (pos == digitsStart) {
                    throw error("Invalid number: expected digits in exponent");
                }
            }
            String token = text.substring(start, pos);
            if (integral) {
                try {
                    return Long.valueOf(token);
                } catch (NumberFormatException overflow) {
                    return new BigInteger(token);
                }
            }
            double d = Double.parseDouble(token);
            if (Double.isInfinite(d)) {
                return new BigDecimal(token);
            }
            return d;
        }
    }
}
