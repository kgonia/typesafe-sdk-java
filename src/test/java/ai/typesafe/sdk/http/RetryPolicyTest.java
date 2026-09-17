package ai.typesafe.sdk.http;

import ai.typesafe.sdk.errors.TypeSafeException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    @Test
    void defaults() {
        RetryPolicy p = RetryPolicy.defaults();
        assertEquals(2, p.maxRetries());
        assertEquals(Duration.ofMillis(500), p.initialBackoff());
        assertEquals(Duration.ofSeconds(5), p.maxBackoff());
        assertEquals(0.25, p.jitter());
        assertTrue(p.retriesStatus(408));
        assertTrue(p.retriesStatus(429));
        assertTrue(p.retriesStatus(500));
        assertTrue(p.retriesStatus(599));
        assertFalse(p.retriesStatus(400));
        assertFalse(p.retriesStatus(422));
        assertTrue(p.respectRetryAfter());
        assertEquals(Duration.ofSeconds(60), p.maxRetryAfter());
        assertTrue(p.retryConnectionErrors());
        assertTrue(p.retryTimeouts());
        assertEquals(Optional.empty(), p.maxElapsed());
        assertEquals(0, RetryPolicy.none().maxRetries());
    }

    @Test
    void toBuilderKeepsOtherSettings() {
        RetryPolicy p = RetryPolicy.defaults().toBuilder().maxRetries(5).maxElapsed(Duration.ofSeconds(9)).build();
        assertEquals(5, p.maxRetries());
        assertEquals(Optional.of(Duration.ofSeconds(9)), p.maxElapsed());
        assertEquals(RetryPolicy.defaults().retryableStatuses(), p.retryableStatuses());
        assertEquals(Optional.empty(), p.toBuilder().maxElapsed(null).build().maxElapsed());
    }

    @Test
    void validates() {
        assertThrows(TypeSafeException.class, () -> RetryPolicy.builder().maxRetries(-1).build());
        assertThrows(TypeSafeException.class, () -> RetryPolicy.builder().initialBackoff(Duration.ofMillis(-1)).build());
        assertThrows(TypeSafeException.class, () -> RetryPolicy.builder().maxBackoff(Duration.ofMillis(-1)).build());
        assertThrows(TypeSafeException.class, () -> RetryPolicy.builder().jitter(1.5).build());
        assertThrows(TypeSafeException.class, () -> RetryPolicy.builder().jitter(Double.NaN).build());
        assertThrows(TypeSafeException.class, () -> RetryPolicy.builder().retryableStatuses(Set.of(42)).build());
        assertThrows(TypeSafeException.class, () -> RetryPolicy.builder().maxRetryAfter(Duration.ofMillis(-1)).build());
        assertThrows(TypeSafeException.class, () -> RetryPolicy.builder().maxElapsed(Duration.ZERO).build());
        assertThrows(NullPointerException.class, () -> RetryPolicy.builder().retryableStatuses(null).build());
    }

    @Test
    void retryableStatusesAreCopied() {
        Set<Integer> statuses = new java.util.HashSet<>(Set.of(503));
        RetryPolicy p = RetryPolicy.builder().retryableStatuses(statuses).build();
        statuses.add(500);
        assertFalse(p.retriesStatus(500));
        assertThrows(UnsupportedOperationException.class, () -> p.retryableStatuses().add(1));
    }
}
