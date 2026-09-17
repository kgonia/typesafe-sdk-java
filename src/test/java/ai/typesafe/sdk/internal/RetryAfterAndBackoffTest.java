package ai.typesafe.sdk.internal;

import ai.typesafe.sdk.http.RetryPolicy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpHeaders;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RetryAfterAndBackoffTest {

    private static HttpHeaders headers(String... pairs) {
        Map<String, List<String>> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], List.of(pairs[i + 1]));
        }
        return HttpHeaders.of(map, (a, b) -> true);
    }

    @Test
    void parsesRetryAfterMsFirst() {
        assertEquals(Optional.of(Duration.ofMillis(250)), RetryAfter.parse(headers("retry-after-ms", "250", "retry-after", "9")));
    }

    @Test
    void parsesRetryAfterSeconds() {
        assertEquals(Optional.of(Duration.ofSeconds(2)), RetryAfter.parse(headers("Retry-After", "2")));
        assertEquals(Optional.of(Duration.ofMillis(1500)), RetryAfter.parse(headers("retry-after", "1.5")));
        assertEquals(Optional.of(Duration.ZERO), RetryAfter.parse(headers("retry-after", "0")));
    }

    @Test
    void parsesHttpDates() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        assertEquals(Optional.of(Duration.ofSeconds(30)),
                RetryAfter.parse(headers("retry-after", "Thu, 01 Jan 2026 00:00:30 GMT"), clock));
        assertEquals(Optional.of(Duration.ZERO),
                RetryAfter.parse(headers("retry-after", "Wed, 31 Dec 2025 23:00:00 GMT"), clock));
    }

    @Test
    void ignoresInvalidValues() {
        assertEquals(Optional.empty(), RetryAfter.parse(headers("retry-after", "soon")));
        assertEquals(Optional.empty(), RetryAfter.parse(headers("retry-after", "-1")));
        assertEquals(Optional.empty(), RetryAfter.parse(headers("retry-after-ms", "NaN")));
        assertEquals(Optional.empty(), RetryAfter.parse(headers()));
        assertEquals(Optional.empty(), RetryAfter.parse(null));
        // An invalid ms header falls back to the seconds header.
        assertEquals(Optional.of(Duration.ofSeconds(3)), RetryAfter.parse(headers("retry-after-ms", "x", "retry-after", "3")));
    }

    @Test
    void exponentialBackoffWithCapAndJitter() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertEquals(Duration.ofMillis(500), Backoff.delay(policy, 0, null, () -> 0));
        assertEquals(Duration.ofMillis(1000), Backoff.delay(policy, 1, null, () -> 0));
        assertEquals(Duration.ofMillis(4000), Backoff.delay(policy, 3, null, () -> 0));
        assertEquals(Duration.ofMillis(5000), Backoff.delay(policy, 10, null, () -> 0));
        assertEquals(Duration.ofMillis(375), Backoff.delay(policy, 0, null, () -> 1));
        Duration random = Backoff.delay(policy, 0, null, () -> Math.random());
        assertTrue(random.toMillis() >= 375 && random.toMillis() <= 500, random.toString());
    }

    @Test
    void honorsRetryAfterWithinCap() {
        RetryPolicy policy = RetryPolicy.defaults();
        assertEquals(Duration.ofMillis(1234), Backoff.delay(policy, 0, headers("retry-after-ms", "1234"), () -> 0));
        assertEquals(Duration.ofMillis(500), Backoff.delay(policy, 0, headers("retry-after", "61"), () -> 0));
        RetryPolicy ignoring = policy.toBuilder().respectRetryAfter(false).build();
        assertEquals(Duration.ofMillis(500), Backoff.delay(ignoring, 0, headers("retry-after-ms", "1234"), () -> 0));
    }
}
