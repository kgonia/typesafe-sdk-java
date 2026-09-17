package ai.typesafe.sdk.internal;

import ai.typesafe.sdk.http.RequestOptions;
import ai.typesafe.sdk.models.ListModelsResponse;
import ai.typesafe.sdk.models.Models;
import java.util.concurrent.CompletableFuture;

/** The {@link Models} implementation over a {@link Transport}. */
public final class DefaultModels implements Models {

    private final Transport transport;

    /** Creates the resource. */
    public DefaultModels(Transport transport) {
        this.transport = transport;
    }

    @Override
    public ListModelsResponse list(RequestOptions options) {
        return transport.send("GET", PATH, null, options, Codec::modelsResponse);
    }

    @Override
    public CompletableFuture<ListModelsResponse> listAsync(RequestOptions options) {
        return transport.sendAsync("GET", PATH, null, options, Codec::modelsResponse);
    }
}
