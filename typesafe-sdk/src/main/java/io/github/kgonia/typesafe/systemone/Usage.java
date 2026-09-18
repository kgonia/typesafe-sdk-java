package io.github.kgonia.typesafe.systemone;

import java.util.OptionalInt;

/**
 * Token usage for one request, when reported by the API. Input tokens are billed; output tokens are currently
 * free.
 *
 * @param inputTokens the number of input tokens, when reported
 * @param outputTokens the number of output tokens, when reported
 */
public record Usage(OptionalInt inputTokens, OptionalInt outputTokens) {

    /** Usage with both counts present. */
    public static Usage of(int inputTokens, int outputTokens) {
        return new Usage(OptionalInt.of(inputTokens), OptionalInt.of(outputTokens));
    }
}
