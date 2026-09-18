package io.github.kgonia.typesafe.errors;

import io.github.kgonia.typesafe.http.RetryPolicy;
import java.time.Duration;

/**
 * A single attempt did not complete within its timeout. A kind of {@link TypeSafeConnectionException}, retried
 * by default under the {@code RetryPolicy}.
 */
public class TypeSafeTimeoutException extends TypeSafeConnectionException {

    private static final long serialVersionUID = 1L;

    private final transient Duration timeout;

    /** Creates the exception for the given per-attempt timeout. */
    public TypeSafeTimeoutException(Duration timeout) {
        this(timeout, null);
    }

    /** Creates the exception for the given per-attempt timeout with an underlying cause. */
    public TypeSafeTimeoutException(Duration timeout, Throwable cause) {
        super("Request timed out after " + timeout.toMillis() + "ms.", cause);
        this.timeout = timeout;
    }

    /** The per-attempt timeout that elapsed. */
    public Duration timeout() {
        return timeout;
    }
}
