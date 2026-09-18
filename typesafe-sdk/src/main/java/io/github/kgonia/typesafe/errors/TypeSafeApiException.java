package io.github.kgonia.typesafe.errors;

import java.net.http.HttpHeaders;
import java.util.Optional;

/**
 * An unsuccessful HTTP response from the API, after any retries.
 *
 * <p>The SDK raises a status-specific subclass where one exists: {@link BadRequestException} (400),
 * {@link AuthenticationException} (401), {@link PermissionDeniedException} (403), {@link NotFoundException}
 * (404), {@link UnprocessableEntityException} (422), {@link RateLimitException} (429), and
 * {@link InternalServerException} (5xx). Other statuses raise this class directly.
 */
public class TypeSafeApiException extends TypeSafeException {

    private static final long serialVersionUID = 1L;

    /** Name of the response header carrying the request ID. */
    public static final String REQUEST_ID_HEADER = "x-typesafe-request-id";

    private final int statusCode;
    private final transient Object body;
    private final transient HttpHeaders headers;
    private final String endpoint;

    /**
     * Creates an API exception.
     *
     * @param statusCode the HTTP status code
     * @param body the parsed JSON body, the response text, or {@code null} for an empty body
     * @param headers the response headers
     * @param endpoint the request method and path, such as {@code POST /v1/systemone}
     * @param detail a description of the failure, usually derived from the body; may be {@code null}
     */
    public TypeSafeApiException(int statusCode, Object body, HttpHeaders headers, String endpoint, String detail) {
        super(describe(statusCode, body, headers, endpoint, detail));
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers == null ? HttpHeaders.of(java.util.Map.of(), (a, b) -> true) : headers;
        this.endpoint = endpoint;
    }

    private static String describe(int statusCode, Object body, HttpHeaders headers, String endpoint, String detail) {
        String text = detail;
        StringBuilder sb = new StringBuilder();
        if (endpoint != null) {
            sb.append(endpoint).append(": ");
        }
        sb.append(statusCode);
        if (text != null && !text.isBlank()) {
            sb.append(' ').append(text);
        }
        if (headers != null) {
            headers.firstValue(REQUEST_ID_HEADER).ifPresent(id -> sb.append(" (request_id=").append(id).append(')'));
        }
        return sb.toString();
    }

    /** The HTTP status code. */
    public int statusCode() {
        return statusCode;
    }

    /**
     * The response body: parsed JSON (a {@code Map}, {@code List}, or scalar), the raw text when the body was not
     * JSON, or {@code null} when it was empty.
     */
    public Object body() {
        return body;
    }

    /** The response headers. */
    public HttpHeaders headers() {
        return headers;
    }

    /** The request method and path, when known. */
    public Optional<String> endpoint() {
        return Optional.ofNullable(endpoint);
    }

    /** The {@code x-typesafe-request-id} response header, when present. Quote it when contacting support. */
    public Optional<String> requestId() {
        return headers.firstValue(REQUEST_ID_HEADER);
    }
}
