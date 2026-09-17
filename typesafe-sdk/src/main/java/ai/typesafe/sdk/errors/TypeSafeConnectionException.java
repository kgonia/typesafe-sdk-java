package ai.typesafe.sdk.errors;

import ai.typesafe.sdk.http.RetryPolicy;
/**
 * The request failed without an HTTP response: DNS failure, refused connection, TLS error, or a connection that
 * dropped while the response was being read. Retried by default under the {@code RetryPolicy}.
 */
public class TypeSafeConnectionException extends TypeSafeException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception with a message. */
    public TypeSafeConnectionException(String message) {
        super(message);
    }

    /** Creates the exception with a message and the underlying I/O failure. */
    public TypeSafeConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
