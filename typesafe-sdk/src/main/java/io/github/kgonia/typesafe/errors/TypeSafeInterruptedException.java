package io.github.kgonia.typesafe.errors;

/**
 * The calling thread was interrupted while a synchronous call was waiting on the network or a retry delay.
 *
 * <p>The thread's interrupt flag is restored before this is thrown, so callers that care about interruption can
 * still observe it.
 */
public class TypeSafeInterruptedException extends TypeSafeException {

    private static final long serialVersionUID = 1L;

    /** Creates the exception, wrapping the original {@link InterruptedException}. */
    public TypeSafeInterruptedException(InterruptedException cause) {
        super("Request was interrupted.", cause);
    }
}
