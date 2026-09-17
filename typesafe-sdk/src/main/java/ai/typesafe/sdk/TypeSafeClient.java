package ai.typesafe.sdk;

import ai.typesafe.sdk.errors.TypeSafeException;
import ai.typesafe.sdk.http.RequestOptions;
import ai.typesafe.sdk.http.RetryPolicy;
import ai.typesafe.sdk.internal.ClientConfig;
import ai.typesafe.sdk.internal.DefaultTypeSafeClient;
import ai.typesafe.sdk.internal.Environment;
import ai.typesafe.sdk.models.Models;
import ai.typesafe.sdk.systemone.Question;
import ai.typesafe.sdk.systemone.SystemOneRequest;
import ai.typesafe.sdk.systemone.SystemOneResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Client for the TypeSafe AI API. Thread-safe; create one per application and share it.
 *
 * <p>Obtain one from {@link #create()}, {@link #create(String)}, or {@link #builder()}. Configuration comes
 * from the {@link Builder}, then environment variables, then SDK defaults. Blank environment values are ignored.
 *
 * <table class="striped">
 *   <caption>Environment variables</caption>
 *   <tr><th>Variable</th><th>Configures</th><th>Default</th></tr>
 *   <tr><td>{@code TYPESAFE_API_KEY}</td><td>API key (required)</td><td>none</td></tr>
 *   <tr><td>{@code TYPESAFE_BASE_URL}</td><td>API root URL</td><td>{@code https://api.typesafe.ai}</td></tr>
 *   <tr><td>{@code TYPESAFE_DEFAULT_MODEL}</td><td>Model when a request names none</td><td>{@code jev-latest}</td></tr>
 *   <tr><td>{@code TYPESAFE_LOG_LEVEL}</td><td>SDK log level</td><td>{@code warn}</td></tr>
 * </table>
 *
 * <pre>{@code
 * try (TypeSafeClient client = TypeSafeClient.create()) {
 *     SystemOneResponse response = client.systemOne(
 *         "I was charged twice. Please help ASAP.",
 *         Map.of("billing", Question.noul("Is this about billing?")));
 *     System.out.println(response.noul("billing").noul());
 * }
 * }</pre>
 *
 * <p>The client uses the JDK's {@link HttpClient}. It creates and owns one by default and closes it in
 * {@link #close()}; a client supplied through {@link Builder#httpClient(HttpClient)} is left open for its owner
 * to close. This is an interface so it can be mocked in tests of code that uses it.
 */
public interface TypeSafeClient extends AutoCloseable {

    /** Environment variable for the API key. */
    String API_KEY_ENV = "TYPESAFE_API_KEY";
    /** Environment variable for the API root URL. */
    String BASE_URL_ENV = "TYPESAFE_BASE_URL";
    /** Environment variable for the default model. */
    String DEFAULT_MODEL_ENV = "TYPESAFE_DEFAULT_MODEL";
    /** Environment variable for the log level. */
    String LOG_LEVEL_ENV = "TYPESAFE_LOG_LEVEL";

    /** The default API root URL. */
    String DEFAULT_BASE_URL = "https://api.typesafe.ai";
    /** The default model. */
    String DEFAULT_MODEL = "jev-latest";
    /** The default per-attempt timeout. */
    Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    /** The default log level. */
    LogLevel DEFAULT_LOG_LEVEL = LogLevel.WARN;

    /** A client configured from environment variables. Requires {@code TYPESAFE_API_KEY}. */
    static TypeSafeClient create() {
        return builder().build();
    }

    /** A client with the given API key; other settings come from the environment or defaults. */
    static TypeSafeClient create(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    /** Starts a builder. */
    static Builder builder() {
        return new Builder();
    }

    // ---------------------------------------------------------------------------------------------
    // System One
    // ---------------------------------------------------------------------------------------------

    /**
     * Answers named questions about {@code state} using the default model.
     *
     * @param state text, a JSON object ({@link Map} or record), or a JSON array ({@link java.util.List})
     * @param questions questions keyed by the names under which answers come back; never empty
     * @return one answer per question, with the model used and token usage
     * @throws TypeSafeException when the questions are invalid or the state cannot be encoded
     * @throws ai.typesafe.sdk.errors.TypeSafeApiException when the server returns an error after any retries
     * @throws ai.typesafe.sdk.errors.TypeSafeConnectionException when the request cannot complete after any retries
     * @throws ai.typesafe.sdk.errors.TypeSafeInterruptedException when the calling thread is interrupted
     */
    default SystemOneResponse systemOne(Object state, Map<String, ? extends Question> questions) {
        return systemOne(SystemOneRequest.of(state, questions));
    }

    /** Answers the questions in a prepared request. See {@link #systemOne(Object, Map)}. */
    default SystemOneResponse systemOne(SystemOneRequest request) {
        return systemOne(request, RequestOptions.none());
    }

    /** Answers the questions in a prepared request with per-call overrides. See {@link #systemOne(Object, Map)}. */
    SystemOneResponse systemOne(SystemOneRequest request, RequestOptions options);

    /**
     * Answers named questions asynchronously. The future fails with the same exceptions
     * {@link #systemOne(Object, Map)} throws; cancelling it cancels the in-flight request and any pending retry.
     */
    default CompletableFuture<SystemOneResponse> systemOneAsync(Object state,
            Map<String, ? extends Question> questions) {
        return systemOneAsync(SystemOneRequest.of(state, questions));
    }

    /** Answers a prepared request asynchronously. See {@link #systemOneAsync(Object, Map)}. */
    default CompletableFuture<SystemOneResponse> systemOneAsync(SystemOneRequest request) {
        return systemOneAsync(request, RequestOptions.none());
    }

    /** Answers a prepared request asynchronously with per-call overrides. See {@link #systemOneAsync(Object, Map)}. */
    CompletableFuture<SystemOneResponse> systemOneAsync(SystemOneRequest request, RequestOptions options);

    /** The Models resource. */
    Models models();

    // ---------------------------------------------------------------------------------------------
    // Configuration
    // ---------------------------------------------------------------------------------------------

    /** The API root URL without a trailing slash. */
    String baseUrl();

    /** The model used when a request names none. */
    String defaultModel();

    /** The per-attempt timeout. */
    Duration timeout();

    /** The retry policy. */
    RetryPolicy retryPolicy();

    /** The SDK log level. */
    LogLevel logLevel();

    /** Extra headers sent with every request. */
    Map<String, String> defaultHeaders();

    /** The underlying HTTP client. */
    HttpClient httpClient();

    /**
     * Closes the HTTP client this client created, waiting for in-flight requests to finish. Does nothing when
     * the HTTP client was supplied through the builder.
     */
    @Override
    void close();

    // ---------------------------------------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------------------------------------

    /** Builds a {@link TypeSafeClient}. */
    final class Builder {
        private String apiKey;
        private String baseUrl;
        private String defaultModel;
        private Duration timeout = DEFAULT_TIMEOUT;
        private RetryPolicy retryPolicy = RetryPolicy.defaults();
        private LogLevel logLevel;
        private System.Logger logger;
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private HttpClient httpClient;
        private Function<String, String> environment = System::getenv;

        private Builder() {
        }

        /** The API key. Falls back to {@code TYPESAFE_API_KEY}. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** The API root URL. Falls back to {@code TYPESAFE_BASE_URL}, then {@link #DEFAULT_BASE_URL}. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** The API root URL. See {@link #baseUrl(String)}. */
        public Builder baseUrl(URI baseUrl) {
            this.baseUrl = baseUrl == null ? null : baseUrl.toString();
            return this;
        }

        /** The model used when a request names none. Falls back to {@code TYPESAFE_DEFAULT_MODEL}, then {@code jev-latest}. */
        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        /**
         * The per-attempt timeout, covering connection, headers, and body. Default: 10 seconds. There is no total
         * budget across retries unless {@link RetryPolicy#maxElapsed()} sets one.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /** The retry policy. Default: {@link RetryPolicy#defaults()}. */
        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
            return this;
        }

        /** The SDK log level. Falls back to {@code TYPESAFE_LOG_LEVEL}, then {@link LogLevel#WARN}. */
        public Builder logLevel(LogLevel logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        /** The logger to write to. Default: {@code System.getLogger("ai.typesafe.sdk")}. */
        public Builder logger(System.Logger logger) {
            this.logger = logger;
            return this;
        }

        /** Adds a header sent with every request. Per-call headers take precedence. */
        public Builder defaultHeader(String name, String value) {
            defaultHeaders.put(name, value);
            return this;
        }

        /** Adds headers sent with every request. */
        public Builder defaultHeaders(Map<String, String> headers) {
            defaultHeaders.putAll(headers);
            return this;
        }

        /** A custom HTTP client, for proxies, TLS settings, or executors. The caller keeps ownership and closes it. */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /** Overrides where environment variables are read from. For tests. */
        Builder environment(Function<String, String> environment) {
            this.environment = Objects.requireNonNull(environment, "environment");
            return this;
        }

        /**
         * Creates the client.
         *
         * @throws TypeSafeException when no API key is available or a setting is invalid
         */
        public TypeSafeClient build() {
            String key = Environment.resolve(apiKey, environment, API_KEY_ENV).orElseThrow(() -> new TypeSafeException(
                    "No API key was provided. Pass apiKey to TypeSafeClient.builder() or set the " + API_KEY_ENV
                            + " environment variable."));
            String url = normalizeBaseUrl(Environment.resolve(baseUrl, environment, BASE_URL_ENV).orElse(DEFAULT_BASE_URL));
            String model = Environment.resolve(defaultModel, environment, DEFAULT_MODEL_ENV).orElse(DEFAULT_MODEL);
            LogLevel level = logLevel != null ? logLevel
                    : Environment.read(environment, LOG_LEVEL_ENV).map(v -> LogLevel.parse(v, LOG_LEVEL_ENV))
                            .orElse(DEFAULT_LOG_LEVEL);
            if (timeout.isZero() || timeout.isNegative()) {
                throw new TypeSafeException("timeout must be positive, got " + timeout + ".");
            }
            return new DefaultTypeSafeClient(new ClientConfig(key, url, model, timeout, retryPolicy, level, logger,
                    defaultHeaders, httpClient));
        }

        private static String normalizeBaseUrl(String raw) {
            String trimmed = raw.trim();
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            URI uri;
            try {
                uri = URI.create(trimmed);
            } catch (IllegalArgumentException e) {
                throw new TypeSafeException("Invalid base URL \"" + raw + "\": " + e.getMessage(), e);
            }
            String scheme = uri.getScheme();
            boolean http = scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
            if (!http || uri.getHost() == null) {
                throw new TypeSafeException("Invalid base URL \"" + raw + "\": expected an absolute http(s) URL.");
            }
            return trimmed;
        }
    }
}
