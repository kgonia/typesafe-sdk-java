package ai.typesafe.sdk.errors;

import ai.typesafe.sdk.http.RetryPolicy;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.Optional;

/**
 * HTTP 429: the rate limit was exceeded.
 *
 * <p>The SDK retries these automatically under the configured {@code RetryPolicy}. When this exception reaches
 * your code, all retries have been exhausted. {@link #retryAfter()} reports the delay the server asked for.
 */
public class RateLimitException extends TypeSafeApiException {

    private static final long serialVersionUID = 1L;

    private final transient Duration retryAfter;

    /**
     * Creates the exception.
     *
     * @param retryAfter the delay parsed from the response headers, or {@code null}; see
     *     {@link TypeSafeApiException#TypeSafeApiException(int, Object, HttpHeaders, String, String)} for the rest
     */
    public RateLimitException(int statusCode, Object body, HttpHeaders headers, String endpoint, String detail,
            Duration retryAfter) {
        super(statusCode, body, headers, endpoint, detail);
        this.retryAfter = retryAfter;
    }

    /** The delay requested through {@code retry-after-ms} or {@code Retry-After}, when the server sent one. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
