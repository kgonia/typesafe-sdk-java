package io.github.kgonia.typesafe.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Case-insensitive header merging and credential redaction for logs. */
public final class HeaderUtil {

    /** Header names whose values keep their scheme and last four characters in logs. */
    private static final Set<String> KEY_HEADERS = Set.of("authorization", "proxy-authorization", "x-api-key", "api-key");

    /** Header names whose values are fully hidden in logs. */
    private static final Set<String> OPAQUE_HEADERS = Set.of("cookie", "set-cookie");

    private HeaderUtil() {
    }

    /**
     * Merges header maps, later sources winning regardless of name casing. A {@code null} value removes the
     * header. Returns a case-insensitive, insertion-ordered, unmodifiable map.
     */
    @SafeVarargs
    public static Map<String, String> merge(Map<String, String>... sources) {
        // Keyed by lower-cased name; the value keeps the caller's spelling. Re-adding a name moves it to the end.
        Map<String, Map.Entry<String, String>> merged = new LinkedHashMap<>();
        for (Map<String, String> source : sources) {
            if (source == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : source.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                merged.remove(key);
                if (entry.getValue() != null) {
                    merged.put(key, Map.entry(entry.getKey(), entry.getValue()));
                }
            }
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : merged.values()) {
            out.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(out);
    }

    /** A copy of the headers with credential values redacted. */
    public static Map<String, String> redact(Map<String, String> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            out.put(entry.getKey(), redactValue(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    /** The value to log for a header: unchanged unless the header carries a credential. */
    public static String redactValue(String name, String value) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (KEY_HEADERS.contains(lower)) {
            return redactKey(value);
        }
        if (OPAQUE_HEADERS.contains(lower) || lower.contains("token") || lower.contains("secret")) {
            return "***";
        }
        return value;
    }

    private static String redactKey(String value) {
        if (value == null) {
            return "***";
        }
        String scheme = null;
        String secret = value;
        int space = value.indexOf(' ');
        if (space > 0) {
            scheme = value.substring(0, space);
            secret = value.substring(space + 1).trim();
        }
        String tail = secret.length() > 8 ? secret.substring(secret.length() - 4) : "";
        return (scheme == null ? "" : scheme + " ") + "***" + tail;
    }
}
