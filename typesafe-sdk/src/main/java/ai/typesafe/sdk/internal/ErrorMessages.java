package ai.typesafe.sdk.internal;

import ai.typesafe.sdk.internal.json.Json;
import ai.typesafe.sdk.internal.json.JsonException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Derives a human-readable message from an error response body. */
public final class ErrorMessages {

    private static final int MAX_RAW_BODY_IN_MESSAGE = 200;

    private ErrorMessages() {
    }

    /** A message extracted from the body, or a truncated rendering of the body, or a note that it was empty. */
    public static String describeBody(Object body) {
        String detail = extract(body);
        if (detail != null) {
            return detail;
        }
        if (body == null) {
            return "status code (no body)";
        }
        String raw;
        if (body instanceof String s) {
            raw = s;
        } else {
            try {
                raw = Json.write(body);
            } catch (JsonException e) {
                raw = String.valueOf(body);
            }
        }
        return raw.length() > MAX_RAW_BODY_IN_MESSAGE ? raw.substring(0, MAX_RAW_BODY_IN_MESSAGE) + "…" : raw;
    }

    /** Looks for {@code error}, {@code error.message}, {@code message}, {@code detail}, or a validation list. */
    public static String extract(Object body) {
        if (body instanceof String s) {
            return s.isEmpty() ? null : s;
        }
        if (!(body instanceof Map<?, ?> map)) {
            return null;
        }
        Object error = map.get("error");
        if (error instanceof String s) {
            return s;
        }
        if (error instanceof Map<?, ?> em && em.get("message") instanceof String s) {
            return s;
        }
        if (map.get("message") instanceof String s) {
            return s;
        }
        Object detail = map.get("detail");
        if (detail instanceof String s) {
            return s;
        }
        if (detail instanceof Map<?, ?> dm && dm.get("message") instanceof String s) {
            return s;
        }
        if (detail instanceof List<?> list) {
            return describeValidationErrors(list);
        }
        return null;
    }

    private static String describeValidationErrors(List<?> errors) {
        List<String> parts = new ArrayList<>();
        for (Object entry : errors) {
            if (!(entry instanceof Map<?, ?> m) || !(m.get("msg") instanceof String msg)) {
                continue;
            }
            String path = "";
            if (m.get("loc") instanceof List<?> loc) {
                StringBuilder sb = new StringBuilder();
                for (Object segment : loc) {
                    if ("body".equals(segment)) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append('.');
                    }
                    sb.append(segment);
                }
                path = sb.toString();
            }
            parts.add(path.isEmpty() ? msg : path + ": " + msg);
        }
        return parts.isEmpty() ? null : String.join("; ", parts);
    }
}
