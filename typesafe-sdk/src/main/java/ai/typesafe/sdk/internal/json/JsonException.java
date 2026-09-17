package ai.typesafe.sdk.internal.json;

/** Thrown when text cannot be parsed as JSON or a value cannot be written as JSON. */
public class JsonException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Creates an exception with the given message. */
    public JsonException(String message) {
        super(message);
    }

    /** Creates an exception with the given message and cause. */
    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
