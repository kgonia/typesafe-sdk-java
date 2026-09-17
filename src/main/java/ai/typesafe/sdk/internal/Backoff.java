package ai.typesafe.sdk.internal;

import ai.typesafe.sdk.http.RetryPolicy;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/** Computes the delay before a retry. */
public final class Backoff {

    private Backoff() {
    }

    /**
     * The delay before retry number {@code attempt} (zero-based): a server-supplied delay when the policy honors
     * it and it is within {@code maxRetryAfter}, otherwise capped exponential backoff with jitter.
     */
    public static Duration delay(RetryPolicy policy, int attempt, HttpHeaders headers, DoubleSupplier random) {
        if (policy.respectRetryAfter() && headers != null) {
            Optional<Duration> retryAfter = RetryAfter.parse(headers);
            if (retryAfter.isPresent() && retryAfter.get().compareTo(policy.maxRetryAfter()) <= 0) {
                return retryAfter.get();
            }
        }
        double initial = policy.initialBackoff().toMillis();
        double max = policy.maxBackoff().toMillis();
        double exponential = Math.min(initial * Math.pow(2, attempt), max);
        double jittered = exponential * (1 - random.getAsDouble() * policy.jitter());
        return Duration.ofMillis(Math.round(jittered));
    }
}
