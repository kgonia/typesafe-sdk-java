package ai.typesafe.sdk.internal;

import ai.typesafe.sdk.LogLevel;
import ai.typesafe.sdk.TypeSafeClient;
import ai.typesafe.sdk.http.RequestOptions;
import ai.typesafe.sdk.http.RetryPolicy;
import ai.typesafe.sdk.models.Models;
import ai.typesafe.sdk.systemone.SystemOneRequest;
import ai.typesafe.sdk.systemone.SystemOneResponse;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** The {@link TypeSafeClient} implementation over a {@link Transport}. */
public final class DefaultTypeSafeClient implements TypeSafeClient {

    /** Path of the System One endpoint. */
    public static final String SYSTEM_ONE_PATH = "/v1/systemone";

    private final ClientConfig config;
    private final Transport transport;
    private final SdkLogger logger;
    private final Models models;

    /** Creates a client from resolved settings, creating an {@link HttpClient} when the config has none. */
    public DefaultTypeSafeClient(ClientConfig config) {
        this.config = config;
        HttpClient httpClient = config.httpClient();
        boolean owns = httpClient == null;
        if (owns) {
            httpClient = HttpClient.newBuilder().connectTimeout(config.timeout())
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
        }
        this.logger = new SdkLogger(config.logger() != null ? config.logger() : SdkLogger.defaultLogger(),
                config.logLevel());
        this.transport = new Transport(httpClient, owns, config.baseUrl(), config.apiKey(), config.defaultHeaders(),
                config.timeout(), config.retryPolicy(), logger);
        this.models = new DefaultModels(transport);
    }

    @Override
    public SystemOneResponse systemOne(SystemOneRequest request, RequestOptions options) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(options, "options");
        return transport.send("POST", SYSTEM_ONE_PATH, Codec.systemOneBody(request, config.defaultModel()), options,
                (response, endpoint) -> Codec.systemOneResponse(response, endpoint, logger));
    }

    @Override
    public CompletableFuture<SystemOneResponse> systemOneAsync(SystemOneRequest request, RequestOptions options) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(options, "options");
        return transport.sendAsync("POST", SYSTEM_ONE_PATH, Codec.systemOneBody(request, config.defaultModel()),
                options, (response, endpoint) -> Codec.systemOneResponse(response, endpoint, logger));
    }

    @Override
    public Models models() {
        return models;
    }

    @Override
    public String baseUrl() {
        return config.baseUrl();
    }

    @Override
    public String defaultModel() {
        return config.defaultModel();
    }

    @Override
    public Duration timeout() {
        return config.timeout();
    }

    @Override
    public RetryPolicy retryPolicy() {
        return config.retryPolicy();
    }

    @Override
    public LogLevel logLevel() {
        return config.logLevel();
    }

    @Override
    public Map<String, String> defaultHeaders() {
        return config.defaultHeaders();
    }

    @Override
    public HttpClient httpClient() {
        return transport.httpClient();
    }

    @Override
    public void close() {
        transport.close();
    }

    /** Configuration without the API key. */
    @Override
    public String toString() {
        return "TypeSafeClient{baseUrl=" + baseUrl() + ", defaultModel=" + defaultModel() + ", timeout=" + timeout()
                + ", retryPolicy=" + retryPolicy() + ", logLevel=" + logLevel() + "}";
    }
}
