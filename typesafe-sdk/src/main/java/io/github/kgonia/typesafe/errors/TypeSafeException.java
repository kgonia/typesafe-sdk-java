package io.github.kgonia.typesafe.errors;

/**
 * Base class for every exception the SDK raises.
 *
 * <p>All SDK exceptions are unchecked. Catch this type to handle any SDK failure, or one of the subclasses to
 * handle a specific kind:
 * <ul>
 *   <li>{@link TypeSafeApiException}: the server returned a non-2xx response (with status-specific subclasses)</li>
 *   <li>{@link TypeSafeConnectionException}: the request never got a response (network failure)</li>
 *   <li>{@link TypeSafeTimeoutException}: the response did not arrive within the timeout</li>
 *   <li>{@link TypeSafeInterruptedException}: the calling thread was interrupted</li>
 *   <li>a plain {@code TypeSafeException}: invalid configuration or an invalid request built locally</li>
 * </ul>
 */
public class TypeSafeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Creates an exception with the given message. */
    public TypeSafeException(String message) {
        super(message);
    }

    /** Creates an exception with the given message and cause. */
    public TypeSafeException(String message, Throwable cause) {
        super(message, cause);
    }
}
