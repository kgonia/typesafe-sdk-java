package ai.typesafe.sdk.models;

import ai.typesafe.sdk.http.RequestOptions;
import java.util.concurrent.CompletableFuture;

/**
 * The Models resource, reached through {@code TypeSafeClient.models()}.
 *
 * <pre>{@code
 * for (ModelMetadata model : client.models().list().models()) {
 *     System.out.println(model.name() + " " + model.releaseDate() + " " + model.description());
 * }
 * }</pre>
 */
public interface Models {

    /** Path of the models endpoint. */
    String PATH = "/v1/models";

    /**
     * Lists the models and aliases the account can use in the {@code model} field.
     *
     * @throws ai.typesafe.sdk.errors.TypeSafeApiException when the server returns an error after any retries
     * @throws ai.typesafe.sdk.errors.TypeSafeConnectionException when the request cannot complete after any retries
     */
    default ListModelsResponse list() {
        return list(RequestOptions.none());
    }

    /** Lists the models with per-call overrides. See {@link #list()}. */
    ListModelsResponse list(RequestOptions options);

    /** Lists the models asynchronously. The future fails with the same exceptions {@link #list()} throws. */
    default CompletableFuture<ListModelsResponse> listAsync() {
        return listAsync(RequestOptions.none());
    }

    /** Lists the models asynchronously with per-call overrides. See {@link #listAsync()}. */
    CompletableFuture<ListModelsResponse> listAsync(RequestOptions options);
}
