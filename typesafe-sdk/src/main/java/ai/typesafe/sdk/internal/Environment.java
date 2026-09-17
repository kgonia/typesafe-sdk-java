package ai.typesafe.sdk.internal;

import java.util.Optional;
import java.util.function.Function;

/** Reads configuration from environment variables, treating blank values as unset. */
public final class Environment {

    private Environment() {
    }

    /** A trimmed, non-blank value, or empty. */
    public static Optional<String> read(Function<String, String> env, String name) {
        String value = env.apply(name);
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }

    /** The explicit value when non-null, else the environment value, else empty. */
    public static Optional<String> resolve(String explicit, Function<String, String> env, String name) {
        return explicit != null ? Optional.of(explicit) : read(env, name);
    }
}
