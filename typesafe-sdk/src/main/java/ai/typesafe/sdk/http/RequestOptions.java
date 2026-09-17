package ai.typesafe.sdk.http;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-call overrides of the client's transport settings. Every field is optional; absent fields inherit the
 * client's configuration.
 *
 * @param timeout the per-attempt timeout for this call
 * @param retryPolicy the retry policy for this call; replaces the client's policy entirely, so start from
 *     {@code client.retryPolicy().toBuilder()} to change one setting
 * @param headers extra request headers, merged over the client's default headers; authentication and SDK
 *     identification headers cannot be overridden
 */
public record RequestOptions(Optional<Duration> timeout, Optional<RetryPolicy> retryPolicy,
        Map<String, String> headers) {

    private static final RequestOptions NONE = new RequestOptions(Optional.empty(), Optional.empty(), Map.of());

    /** Validates and defensively copies the headers. */
    public RequestOptions {
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        Objects.requireNonNull(headers, "headers");
        timeout.ifPresent(RetryPolicy::requirePositive);
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /** No overrides. */
    public static RequestOptions none() {
        return NONE;
    }

    /** Starts a builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builds {@link RequestOptions}. */
    public static final class Builder {
        private Duration timeout;
        private RetryPolicy retryPolicy;
        private final Map<String, String> headers = new LinkedHashMap<>();

        private Builder() {
        }

        /** Sets the per-attempt timeout for this call. */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** Sets the retry policy for this call. */
        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        /** Adds a request header. */
        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /** Adds every header in the map. */
        public Builder headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        /** Creates the options. */
        public RequestOptions build() {
            return new RequestOptions(Optional.ofNullable(timeout), Optional.ofNullable(retryPolicy), headers);
        }
    }
}
