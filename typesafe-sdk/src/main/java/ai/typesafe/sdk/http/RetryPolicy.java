package ai.typesafe.sdk.http;

import ai.typesafe.sdk.errors.TypeSafeException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * How the client retries failed attempts.
 *
 * <p>By default the SDK retries HTTP 408, 429, and 5xx responses, connection errors, and per-attempt timeouts up
 * to two times, waiting with capped exponential backoff and jitter, and honoring {@code Retry-After} headers.
 * {@link #none()} disables retries. Use {@link #toBuilder()} to change one setting while keeping the rest.
 *
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.defaults().toBuilder().maxRetries(5).maxElapsed(Duration.ofMinutes(1)).build();
 * }</pre>
 *
 * @param maxRetries the maximum number of retries after the initial attempt; 0 disables retries
 * @param initialBackoff the first backoff delay, doubled each retry up to {@code maxBackoff}
 * @param maxBackoff the longest backoff delay
 * @param jitter the fraction of each backoff delay randomly subtracted, from 0 to 1
 * @param retryableStatuses the HTTP status codes that are retried
 * @param respectRetryAfter whether a server-supplied {@code retry-after-ms} or {@code Retry-After} delay replaces
 *     the computed backoff
 * @param maxRetryAfter the longest server-supplied delay to honor; longer delays fall back to backoff
 * @param retryConnectionErrors whether {@link ai.typesafe.sdk.errors.TypeSafeConnectionException} is retried
 * @param retryTimeouts whether {@link ai.typesafe.sdk.errors.TypeSafeTimeoutException} is retried
 * @param maxElapsed an optional total budget per call, including attempts and delays; a retry whose delay would
 *     end past the budget is skipped and the last error is thrown
 */
public record RetryPolicy(int maxRetries, Duration initialBackoff, Duration maxBackoff, double jitter,
        Set<Integer> retryableStatuses, boolean respectRetryAfter, Duration maxRetryAfter,
        boolean retryConnectionErrors, boolean retryTimeouts, Optional<Duration> maxElapsed) {

    /** HTTP 408, 429, and 500 to 599. */
    public static final Set<Integer> DEFAULT_RETRYABLE_STATUSES = Collections.unmodifiableSet(
            IntStream.concat(IntStream.of(408, 429), IntStream.rangeClosed(500, 599)).boxed()
                    .collect(Collectors.toCollection(() -> new LinkedHashSet<Integer>())));

    private static final RetryPolicy DEFAULTS = new RetryPolicy(2, Duration.ofMillis(500), Duration.ofSeconds(5),
            0.25, DEFAULT_RETRYABLE_STATUSES, true, Duration.ofSeconds(60), true, true, Optional.empty());

    private static final RetryPolicy NONE = DEFAULTS.toBuilder().maxRetries(0).build();

    /** Validates every setting. */
    public RetryPolicy {
        if (maxRetries < 0) {
            throw new TypeSafeException("maxRetries must be zero or more, got " + maxRetries + ".");
        }
        requireNonNegative("initialBackoff", initialBackoff);
        requireNonNegative("maxBackoff", maxBackoff);
        if (Double.isNaN(jitter) || jitter < 0 || jitter > 1) {
            throw new TypeSafeException("jitter must be between 0 and 1, got " + jitter + ".");
        }
        Objects.requireNonNull(retryableStatuses, "retryableStatuses");
        for (Integer status : retryableStatuses) {
            if (status == null || status < 100 || status > 999) {
                throw new TypeSafeException("retryableStatuses must contain HTTP status codes, got " + status + ".");
            }
        }
        retryableStatuses = Collections.unmodifiableSet(new LinkedHashSet<>(retryableStatuses));
        requireNonNegative("maxRetryAfter", maxRetryAfter);
        Objects.requireNonNull(maxElapsed, "maxElapsed");
        maxElapsed.ifPresent(RetryPolicy::requirePositive);
    }

    /** The SDK defaults: two retries, 500ms to 5s backoff with 25% jitter, and no total budget. */
    public static RetryPolicy defaults() {
        return DEFAULTS;
    }

    /** No retries. */
    public static RetryPolicy none() {
        return NONE;
    }

    /** Starts a builder pre-filled with the defaults. */
    public static Builder builder() {
        return DEFAULTS.toBuilder();
    }

    /** Starts a builder pre-filled with this policy. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Whether the policy retries the given HTTP status. */
    public boolean retriesStatus(int status) {
        return retryableStatuses.contains(status);
    }

    static void requireNonNegative(String name, Duration value) {
        Objects.requireNonNull(value, name);
        if (value.isNegative()) {
            throw new TypeSafeException(name + " must not be negative, got " + value + ".");
        }
    }

    static void requirePositive(Duration value) {
        Objects.requireNonNull(value, "timeout");
        if (value.isZero() || value.isNegative()) {
            throw new TypeSafeException("timeout must be positive, got " + value + ".");
        }
    }

    /** Builds a {@link RetryPolicy}. */
    public static final class Builder {
        private int maxRetries;
        private Duration initialBackoff;
        private Duration maxBackoff;
        private double jitter;
        private Set<Integer> retryableStatuses;
        private boolean respectRetryAfter;
        private Duration maxRetryAfter;
        private boolean retryConnectionErrors;
        private boolean retryTimeouts;
        private Duration maxElapsed;

        private Builder(RetryPolicy base) {
            maxRetries = base.maxRetries;
            initialBackoff = base.initialBackoff;
            maxBackoff = base.maxBackoff;
            jitter = base.jitter;
            retryableStatuses = base.retryableStatuses;
            respectRetryAfter = base.respectRetryAfter;
            maxRetryAfter = base.maxRetryAfter;
            retryConnectionErrors = base.retryConnectionErrors;
            retryTimeouts = base.retryTimeouts;
            maxElapsed = base.maxElapsed.orElse(null);
        }

        /** Sets the maximum number of retries after the initial attempt; 0 disables retries. */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /** Sets the first backoff delay. */
        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        /** Sets the longest backoff delay. */
        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        /** Sets the jitter fraction, from 0 to 1. */
        public Builder jitter(double jitter) {
            this.jitter = jitter;
            return this;
        }

        /** Replaces the set of retried HTTP statuses. */
        public Builder retryableStatuses(Set<Integer> retryableStatuses) {
            this.retryableStatuses = retryableStatuses;
            return this;
        }

        /** Sets whether server-supplied retry delays are honored. */
        public Builder respectRetryAfter(boolean respectRetryAfter) {
            this.respectRetryAfter = respectRetryAfter;
            return this;
        }

        /** Sets the longest server-supplied delay to honor. */
        public Builder maxRetryAfter(Duration maxRetryAfter) {
            this.maxRetryAfter = maxRetryAfter;
            return this;
        }

        /** Sets whether connection errors are retried. */
        public Builder retryConnectionErrors(boolean retryConnectionErrors) {
            this.retryConnectionErrors = retryConnectionErrors;
            return this;
        }

        /** Sets whether per-attempt timeouts are retried. */
        public Builder retryTimeouts(boolean retryTimeouts) {
            this.retryTimeouts = retryTimeouts;
            return this;
        }

        /** Sets a total time budget per call; {@code null} removes it. */
        public Builder maxElapsed(Duration maxElapsed) {
            this.maxElapsed = maxElapsed;
            return this;
        }

        /** Creates the policy. */
        public RetryPolicy build() {
            return new RetryPolicy(maxRetries, initialBackoff, maxBackoff, jitter, retryableStatuses, respectRetryAfter,
                    maxRetryAfter, retryConnectionErrors, retryTimeouts, Optional.ofNullable(maxElapsed));
        }
    }
}
