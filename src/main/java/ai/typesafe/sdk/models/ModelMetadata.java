package ai.typesafe.sdk.models;

import java.util.Objects;

/**
 * A model or alias the account can use in the {@code model} field.
 *
 * @param name the model ID or alias, such as {@code jev-latest}
 * @param description what the model is for
 * @param releaseDate when the model or alias was released, as reported by the API
 */
public record ModelMetadata(String name, String description, String releaseDate) {

    /** Requires a name. */
    public ModelMetadata {
        Objects.requireNonNull(name, "name");
        description = Objects.requireNonNullElse(description, "");
        releaseDate = Objects.requireNonNullElse(releaseDate, "");
    }
}
