package ai.typesafe.sdk.errors;

import java.net.http.HttpHeaders;

/**
 * The server returned a successful status, but the body was missing or had structurally invalid required data.
 *
 * <p>This usually means an SDK and API version mismatch. {@link #fieldPath()} names the first offending field,
 * such as {@code answers.tone.confidence}, and {@link #body()} holds the full parsed response.
 */
public class ResponseValidationException extends TypeSafeApiException {

    private static final long serialVersionUID = 1L;

    private final String fieldPath;

    /**
     * Creates the exception.
     *
     * @param statusCode the (successful) HTTP status code
     * @param body the parsed response body
     * @param headers the response headers
     * @param endpoint the request method and path
     * @param fieldPath dotted path to the offending field
     */
    public ResponseValidationException(int statusCode, Object body, HttpHeaders headers, String endpoint,
            String fieldPath) {
        super(statusCode, body, headers, endpoint, "Invalid response data at '" + fieldPath + "'.");
        this.fieldPath = fieldPath;
    }

    /** Dotted path to the first missing or invalid field. */
    public String fieldPath() {
        return fieldPath;
    }
}
