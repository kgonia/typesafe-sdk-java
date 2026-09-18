package io.github.kgonia.typesafe.internal;

import io.github.kgonia.typesafe.LogLevel;
import io.github.kgonia.typesafe.SdkVersion;
import io.github.kgonia.typesafe.errors.AuthenticationException;
import io.github.kgonia.typesafe.errors.BadRequestException;
import io.github.kgonia.typesafe.errors.InternalServerException;
import io.github.kgonia.typesafe.errors.NotFoundException;
import io.github.kgonia.typesafe.errors.PermissionDeniedException;
import io.github.kgonia.typesafe.errors.RateLimitException;
import io.github.kgonia.typesafe.errors.TypeSafeApiException;
import io.github.kgonia.typesafe.errors.TypeSafeConnectionException;
import io.github.kgonia.typesafe.errors.TypeSafeException;
import io.github.kgonia.typesafe.errors.TypeSafeInterruptedException;
import io.github.kgonia.typesafe.errors.TypeSafeTimeoutException;
import io.github.kgonia.typesafe.errors.UnprocessableEntityException;
import io.github.kgonia.typesafe.http.RequestOptions;
import io.github.kgonia.typesafe.http.ResponseMetadata;
import io.github.kgonia.typesafe.http.RetryPolicy;
import io.github.kgonia.typesafe.internal.json.Json;
import io.github.kgonia.typesafe.internal.json.JsonException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;

/**
 * Sends JSON requests with per-attempt timeouts and retries, synchronously or asynchronously.
 *
 * <p>Both paths share one attempt implementation built on {@link HttpClient#sendAsync}; the synchronous path
 * blocks on the resulting future, which parks cleanly on virtual threads.
 */
public final class Transport implements AutoCloseable {

    /** Header added to retried attempts with the retry number. */
    public static final String RETRY_COUNT_HEADER = "X-TypeSafe-Retry-Count";

    private final HttpClient httpClient;
    private final boolean ownsHttpClient;
    private final String baseUrl;
    private final String apiKey;
    private final Map<String, String> defaultHeaders;
    private final Duration timeout;
    private final RetryPolicy retryPolicy;
    private final SdkLogger logger;
    private final DoubleSupplier random;
    private final AtomicLong requestCounter = new AtomicLong();

    /** Creates a transport. {@code baseUrl} must have no trailing slash. */
    public Transport(HttpClient httpClient, boolean ownsHttpClient, String baseUrl, String apiKey,
            Map<String, String> defaultHeaders, Duration timeout, RetryPolicy retryPolicy, SdkLogger logger) {
        this(httpClient, ownsHttpClient, baseUrl, apiKey, defaultHeaders, timeout, retryPolicy, logger,
                () -> ThreadLocalRandom.current().nextDouble());
    }

    /** Creates a transport with a custom source of jitter, for tests. */
    public Transport(HttpClient httpClient, boolean ownsHttpClient, String baseUrl, String apiKey,
            Map<String, String> defaultHeaders, Duration timeout, RetryPolicy retryPolicy, SdkLogger logger,
            DoubleSupplier random) {
        this.httpClient = httpClient;
        this.ownsHttpClient = ownsHttpClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.defaultHeaders = defaultHeaders;
        this.timeout = timeout;
        this.retryPolicy = retryPolicy;
        this.logger = logger;
        this.random = random;
    }

    /** The underlying HTTP client. */
    public HttpClient httpClient() {
        return httpClient;
    }

    /** Decodes a successful raw response into a result type. */
    @FunctionalInterface
    public interface Decoder<T> {
        /** Decodes the response; throw {@link io.github.kgonia.typesafe.errors.ResponseValidationException} when invalid. */
        T decode(RawResponse response, String endpoint);
    }

    /** A complete HTTP response with its body buffered as text. */
    public record RawResponse(int statusCode, HttpHeaders headers, String body) {

        /** Whether the status is 2xx. */
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        /** The body as parsed JSON, or as text when it is not JSON, or {@code null} when empty. */
        public Object parsedBody() {
            if (body.isEmpty()) {
                return null;
            }
            try {
                return Json.parse(body);
            } catch (JsonException notJson) {
                return body;
            }
        }

        /** Metadata for a successful response. */
        public ResponseMetadata metadata() {
            return new ResponseMetadata(statusCode, headers, body);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Public entry points
    // ---------------------------------------------------------------------------------------------

    /** Sends a request and blocks for the decoded result. */
    public <T> T send(String method, String path, Object body, RequestOptions options, Decoder<T> decoder) {
        Call call = prepare(method, path, body, options);
        long start = System.nanoTime();
        for (int attempt = 0;; attempt++) {
            int retriesLeft = call.retryPolicy.maxRetries() - attempt;
            RawResponse response;
            try {
                response = block(attempt(call, attempt));
            } catch (TypeSafeConnectionException failure) {
                if (retriesLeft <= 0 || !retries(call.retryPolicy, failure)) {
                    throw failure;
                }
                Duration delay = Backoff.delay(call.retryPolicy, attempt, null, random);
                if (overBudget(call, start, delay)) {
                    throw failure;
                }
                logRetry(call, attempt, retriesLeft, delay, failure.getMessage());
                sleep(delay);
                continue;
            }
            if (response.isSuccess()) {
                return decoder.decode(response, call.endpoint);
            }
            TypeSafeApiException error = apiError(response, call.endpoint);
            if (retriesLeft <= 0 || !call.retryPolicy.retriesStatus(response.statusCode())) {
                throw error;
            }
            Duration delay = Backoff.delay(call.retryPolicy, attempt, response.headers(), random);
            if (overBudget(call, start, delay)) {
                throw error;
            }
            logRetry(call, attempt, retriesLeft, delay, String.valueOf(response.statusCode()));
            sleep(delay);
        }
    }

    /**
     * Sends a request and returns a future for the decoded result. Cancelling the future cancels the in-flight
     * attempt and any pending retry.
     */
    public <T> CompletableFuture<T> sendAsync(String method, String path, Object body, RequestOptions options,
            Decoder<T> decoder) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Call call;
        try {
            call = prepare(method, path, body, options);
        } catch (RuntimeException e) {
            result.completeExceptionally(e);
            return result;
        }
        AtomicReference<CompletableFuture<?>> inFlight = new AtomicReference<>();
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                CompletableFuture<?> pending = inFlight.getAndSet(null);
                if (pending != null) {
                    pending.cancel(true);
                }
            }
        });
        attemptAsync(call, 0, System.nanoTime(), result, inFlight, decoder);
        return result;
    }

    private <T> void attemptAsync(Call call, int attempt, long start, CompletableFuture<T> result,
            AtomicReference<CompletableFuture<?>> inFlight, Decoder<T> decoder) {
        if (result.isDone()) {
            return;
        }
        CompletableFuture<RawResponse> pending = attempt(call, attempt);
        inFlight.set(pending);
        if (result.isCancelled()) {
            pending.cancel(true);
            return;
        }
        pending.whenComplete((response, failure) -> {
            inFlight.compareAndSet(pending, null);
            if (result.isDone()) {
                return;
            }
            try {
                int retriesLeft = call.retryPolicy.maxRetries() - attempt;
                if (failure != null) {
                    Throwable cause = unwrap(failure);
                    if (!(cause instanceof TypeSafeConnectionException connectionFailure) || retriesLeft <= 0
                            || !retries(call.retryPolicy, connectionFailure)) {
                        result.completeExceptionally(cause);
                        return;
                    }
                    Duration delay = Backoff.delay(call.retryPolicy, attempt, null, random);
                    if (overBudget(call, start, delay)) {
                        result.completeExceptionally(cause);
                        return;
                    }
                    logRetry(call, attempt, retriesLeft, delay, cause.getMessage());
                    schedule(delay, () -> attemptAsync(call, attempt + 1, start, result, inFlight, decoder));
                    return;
                }
                if (response.isSuccess()) {
                    result.complete(decoder.decode(response, call.endpoint));
                    return;
                }
                TypeSafeApiException error = apiError(response, call.endpoint);
                if (retriesLeft <= 0 || !call.retryPolicy.retriesStatus(response.statusCode())) {
                    result.completeExceptionally(error);
                    return;
                }
                Duration delay = Backoff.delay(call.retryPolicy, attempt, response.headers(), random);
                if (overBudget(call, start, delay)) {
                    result.completeExceptionally(error);
                    return;
                }
                logRetry(call, attempt, retriesLeft, delay, String.valueOf(response.statusCode()));
                schedule(delay, () -> attemptAsync(call, attempt + 1, start, result, inFlight, decoder));
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
    }

    private static void schedule(Duration delay, Runnable task) {
        if (delay.isZero() || delay.isNegative()) {
            CompletableFuture.runAsync(task);
        } else {
            CompletableFuture.delayedExecutor(delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(task);
        }
    }

    @Override
    public void close() {
        if (ownsHttpClient) {
            httpClient.close();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // One attempt
    // ---------------------------------------------------------------------------------------------

    /** A prepared request: everything needed to build each attempt. */
    private static final class Call {
        final String tag;
        final String endpoint;
        final String method;
        final URI uri;
        final String body;
        final Map<String, String> headers;
        final Duration timeout;
        final RetryPolicy retryPolicy;

        Call(String tag, String endpoint, String method, URI uri, String body, Map<String, String> headers,
                Duration timeout, RetryPolicy retryPolicy) {
            this.tag = tag;
            this.endpoint = endpoint;
            this.method = method;
            this.uri = uri;
            this.body = body;
            this.headers = headers;
            this.timeout = timeout;
            this.retryPolicy = retryPolicy;
        }

        HttpRequest request(int attempt) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout);
            try {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    builder.header(header.getKey(), header.getValue());
                }
                if (attempt > 0) {
                    builder.header(RETRY_COUNT_HEADER, String.valueOf(attempt));
                }
            } catch (IllegalArgumentException restricted) {
                throw new TypeSafeException("Invalid request header: " + restricted.getMessage(), restricted);
            }
            HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
            return builder.method(method, publisher).build();
        }
    }

    private Call prepare(String method, String path, Object body, RequestOptions options) {
        String tag = "#" + requestCounter.incrementAndGet() + " " + method + " " + path;
        String json = null;
        if (body != null) {
            try {
                json = Json.write(body);
            } catch (JsonException e) {
                throw new TypeSafeException("The request body could not be encoded as JSON: " + e.getMessage(), e);
            }
        }
        // User headers first so the protected headers below always win.
        Map<String, String> userHeaders = HeaderUtil.merge(defaultHeaders, options.headers());
        Map<String, String> protectedHeaders = new java.util.LinkedHashMap<>();
        protectedHeaders.put("Authorization", "Bearer " + apiKey);
        protectedHeaders.put("Accept", "application/json");
        protectedHeaders.put("User-Agent", SdkVersion.USER_AGENT);
        protectedHeaders.put("X-TypeSafe-SDK", SdkVersion.USER_AGENT);
        protectedHeaders.put("X-TypeSafe-Runtime", RuntimeInfo.DESCRIPTION);
        protectedHeaders.put("Content-Type", json == null ? null : "application/json");
        protectedHeaders.put(RETRY_COUNT_HEADER, null);
        Map<String, String> headers = HeaderUtil.merge(userHeaders, protectedHeaders);
        URI uri = URI.create(baseUrl + path);
        Call call = new Call(tag, method + " " + path, method, uri, json, headers, options.timeout().orElse(timeout),
                options.retryPolicy().orElse(retryPolicy));
        call.request(0); // Fail fast on headers the JDK client restricts, before any attempt starts.
        return call;
    }

    /**
     * One HTTP round trip including the full body, under the per-attempt timeout. The returned future completes
     * with the response, or exceptionally with a {@link TypeSafeConnectionException} (or
     * {@link TypeSafeTimeoutException}). Cancelling it cancels the underlying request.
     */
    private CompletableFuture<RawResponse> attempt(Call call, int attempt) {
        HttpRequest request = call.request(attempt);
        logger.debug(() -> call.tag + " -> " + call.uri + " headers=" + HeaderUtil.redact(headersOf(request))
                + " body=" + call.body);
        long started = System.nanoTime();
        CompletableFuture<RawResponse> result = new CompletableFuture<>();
        CompletableFuture<HttpResponse<byte[]>> raw;
        try {
            raw = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (RuntimeException e) {
            result.completeExceptionally(new TypeSafeConnectionException("Request could not be sent: "
                    + e.getMessage(), e));
            return result;
        }
        raw.whenComplete((response, failure) -> {
            if (failure != null) {
                result.completeExceptionally(mapFailure(call, unwrap(failure), started));
                return;
            }
            RawResponse rawResponse = new RawResponse(response.statusCode(), response.headers(),
                    new String(response.body(), StandardCharsets.UTF_8));
            logger.info(() -> call.tag + " <- " + rawResponse.statusCode() + " in " + elapsedMs(started) + "ms"
                    + rawResponse.headers().firstValue(ResponseMetadata.REQUEST_ID_HEADER)
                            .map(id -> " (request " + id + ")").orElse(""));
            logger.debug(() -> call.tag + " <- headers=" + response.headers().map() + " body=" + rawResponse.body());
            result.complete(rawResponse);
        });
        schedule(call.timeout, () -> {
            if (result.completeExceptionally(new TypeSafeTimeoutException(call.timeout))) {
                logger.info(() -> call.tag + " timed out after " + elapsedMs(started) + "ms");
                raw.cancel(true);
            }
        });
        result.whenComplete((response, failure) -> {
            if (result.isCancelled()) {
                raw.cancel(true);
            }
        });
        return result;
    }

    private TypeSafeConnectionException mapFailure(Call call, Throwable cause, long started) {
        if (cause instanceof HttpTimeoutException) {
            logger.info(() -> call.tag + " timed out after " + elapsedMs(started) + "ms");
            return new TypeSafeTimeoutException(call.timeout, cause);
        }
        if (cause instanceof CancellationException) {
            return new TypeSafeConnectionException("Request was cancelled.", cause);
        }
        String message = cause instanceof IOException ? "Connection error: " + cause.getMessage()
                : "Connection error: " + cause;
        logger.info(() -> call.tag + " connection error after " + elapsedMs(started) + "ms: " + cause);
        return new TypeSafeConnectionException(message, cause);
    }

    private static RawResponse block(CompletableFuture<RawResponse> pending) {
        try {
            return pending.get();
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new TypeSafeInterruptedException(interrupted);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TypeSafeConnectionException failure) {
                throw failure;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new TypeSafeConnectionException("Connection error: " + cause, cause);
        } catch (CancellationException cancelled) {
            throw new TypeSafeConnectionException("Request was cancelled.", cancelled);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static boolean retries(RetryPolicy policy, TypeSafeConnectionException failure) {
        if (failure instanceof TypeSafeTimeoutException) {
            return policy.retryTimeouts();
        }
        return policy.retryConnectionErrors();
    }

    private static boolean overBudget(Call call, long startNanos, Duration delay) {
        Optional<Duration> budget = call.retryPolicy.maxElapsed();
        if (budget.isEmpty()) {
            return false;
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        return elapsed.plus(delay).compareTo(budget.get()) >= 0;
    }

    private void logRetry(Call call, int attempt, int retriesLeft, Duration delay, String reason) {
        logger.info(() -> call.tag + " retrying in " + delay.toMillis() + "ms (retry " + (attempt + 1) + "/"
                + (attempt + retriesLeft) + ") after " + reason);
    }

    private static void sleep(Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TypeSafeInterruptedException(interrupted);
        }
    }

    private static Throwable unwrap(Throwable t) {
        Throwable current = t;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static Map<String, String> headersOf(HttpRequest request) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        request.headers().map().forEach((name, values) -> out.put(name, String.join(", ", values)));
        return out;
    }

    /** Builds the status-specific exception for a failed response. */
    public static TypeSafeApiException apiError(RawResponse response, String endpoint) {
        int status = response.statusCode();
        Object body = response.parsedBody();
        HttpHeaders headers = response.headers();
        String detail = ErrorMessages.describeBody(body);
        return switch (status) {
            case 400 -> new BadRequestException(status, body, headers, endpoint, detail);
            case 401 -> new AuthenticationException(status, body, headers, endpoint, detail);
            case 403 -> new PermissionDeniedException(status, body, headers, endpoint, detail);
            case 404 -> new NotFoundException(status, body, headers, endpoint, detail);
            case 422 -> new UnprocessableEntityException(status, body, headers, endpoint, detail);
            case 429 -> new RateLimitException(status, body, headers, endpoint, detail,
                    RetryAfter.parse(headers).orElse(null));
            default -> status >= 500 ? new InternalServerException(status, body, headers, endpoint, detail)
                    : new TypeSafeApiException(status, body, headers, endpoint, detail);
        };
    }

    /** Whether the logger emits at the given level; used to skip building expensive messages. */
    public boolean logs(LogLevel level) {
        return logger.enabled(level);
    }
}
