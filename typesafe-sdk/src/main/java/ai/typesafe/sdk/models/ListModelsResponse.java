package ai.typesafe.sdk.models;

import ai.typesafe.sdk.http.ResponseMetadata;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The models available to the account, from {@code GET /v1/models}.
 *
 * @param models one entry per model or alias
 * @param metadata the HTTP status, headers, request ID, and raw body
 */
public record ListModelsResponse(List<ModelMetadata> models, ResponseMetadata metadata) {

    /** Defensively copies the list. */
    public ListModelsResponse {
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(metadata, "metadata");
        models = List.copyOf(models);
    }

    /** The {@code x-typesafe-request-id} response header, when present. */
    public Optional<String> requestId() {
        return metadata.requestId();
    }
}
