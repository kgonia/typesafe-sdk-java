package ai.typesafe.sdk.http;

import java.net.http.HttpHeaders;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Transport details of a successful response: status, headers, request ID, and the raw body for inspecting
 * fields this SDK version does not model.
 *
 * @param statusCode the HTTP status code
 * @param headers the response headers
 * @param rawBody the response body as received
 */
public record ResponseMetadata(int statusCode, HttpHeaders headers, String rawBody) {

    /** Name of the response header carrying the request ID. */
    public static final String REQUEST_ID_HEADER = "x-typesafe-request-id";

    /** Normalizes {@code null} headers and body. */
    public ResponseMetadata {
        headers = headers == null ? HttpHeaders.of(Map.of(), (a, b) -> true) : headers;
        rawBody = Objects.requireNonNullElse(rawBody, "");
    }

    /** The {@code x-typesafe-request-id} response header, when present. */
    public Optional<String> requestId() {
        return headers.firstValue(REQUEST_ID_HEADER);
    }
}
