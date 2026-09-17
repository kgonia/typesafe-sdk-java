package ai.typesafe.sdk;

import ai.typesafe.sdk.errors.BadRequestException;
import ai.typesafe.sdk.errors.InternalServerException;
import ai.typesafe.sdk.errors.RateLimitException;
import ai.typesafe.sdk.errors.TypeSafeConnectionException;
import ai.typesafe.sdk.errors.TypeSafeInterruptedException;
import ai.typesafe.sdk.errors.TypeSafeTimeoutException;
import ai.typesafe.sdk.http.RequestOptions;
import ai.typesafe.sdk.http.RetryPolicy;
import ai.typesafe.sdk.systemone.Question;
import ai.typesafe.sdk.systemone.SystemOneRequest;
import ai.typesafe.sdk.systemone.SystemOneResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetryTest {

    private static final Map<String, Question> QUESTIONS = Map.of("q", Question.noul("Q?"));

    private MockServer server;

    @BeforeEach
    void start() {
        server = MockServer.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private SystemOneResponse call(TypeSafeClient client) {
        return client.systemOne("s", QUESTIONS);
    }

    @Test
    void retriesRateLimitsHonoringRetryAfterMs() {
        server.enqueue(MockServer.Reply.json(429, "{}").header("retry-after-ms", "120"));
        try (TypeSafeClient client = server.clientBuilder().build()) {
            long started = System.nanoTime();
            assertEquals(0.5, call(client).noul("q").noul());
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            assertTrue(elapsedMs >= 100, "expected to wait for Retry-After, waited " + elapsedMs + "ms");
        }
        List<MockServer.Recorded> requests = server.requests();
        assertEquals(2, requests.size());
        assertNull(requests.get(0).header("X-TypeSafe-Retry-Count"));
        assertEquals("1", requests.get(1).header("X-TypeSafe-Retry-Count"));
    }

    @Test
    void retriesServerErrorsWithBackoff() {
        server.enqueue(MockServer.Reply.json(503, "{}"), MockServer.Reply.json(500, "{}"));
        try (TypeSafeClient client = server.clientBuilder().build()) {
            assertEquals(0.5, call(client).noul("q").noul());
        }
        List<MockServer.Recorded> requests = server.requests();
        assertEquals(3, requests.size());
        assertEquals("2", requests.get(2).header("X-TypeSafe-Retry-Count"));
        assertEquals(requests.get(0).body(), requests.get(2).body());
    }

    @Test
    void givesUpAfterMaxRetries() {
        server.respondWith(r -> MockServer.Reply.json(500, "{\"message\":\"down\"}"));
        try (TypeSafeClient client = server.clientBuilder()
                .retryPolicy(MockServer.fastRetries().toBuilder().maxRetries(3).build()).build()) {
            InternalServerException e = assertThrows(InternalServerException.class, () -> call(client));
            assertTrue(e.getMessage().endsWith("500 down"), e.getMessage());
        }
        assertEquals(4, server.requests().size());
    }

    @Test
    void doesNotRetryNonRetryableStatuses() {
        server.respondWith(r -> MockServer.Reply.json(400, "{}"));
        try (TypeSafeClient client = server.clientBuilder().build()) {
            assertThrows(BadRequestException.class, () -> call(client));
        }
        assertEquals(1, server.requests().size());
    }

    @Test
    void retriesConnectionErrors() {
        server.enqueue(MockServer.Reply.drop());
        try (TypeSafeClient client = server.clientBuilder().build()) {
            assertEquals(0.5, call(client).noul("q").noul());
        }
        assertEquals(2, server.requests().size());
    }

    @Test
    void connectionErrorsSurfaceWhenRetriesAreDisabled() {
        server.respondWith(r -> MockServer.Reply.drop());
        try (TypeSafeClient client = server.clientBuilder().retryPolicy(RetryPolicy.none()).build()) {
            TypeSafeConnectionException e = assertThrows(TypeSafeConnectionException.class, () -> call(client));
            assertTrue(e.getMessage().startsWith("Connection error"), e.getMessage());
        }
        assertEquals(1, server.requests().size());
    }

    @Test
    void retriesTimeouts() {
        server.enqueue(MockServer.Reply.json(MockServer.SYSTEM_ONE_OK).delay(400));
        try (TypeSafeClient client = server.clientBuilder().timeout(Duration.ofMillis(80)).build()) {
            assertEquals(0.5, call(client).noul("q").noul());
        }
        assertEquals(2, server.requests().size());
    }

    @Test
    void timeoutsSurfaceWhenNotRetried() {
        server.respondWith(r -> MockServer.Reply.json(MockServer.SYSTEM_ONE_OK).delay(400));
        try (TypeSafeClient client = server.clientBuilder().timeout(Duration.ofMillis(80))
                .retryPolicy(MockServer.fastRetries().toBuilder().retryTimeouts(false).build()).build()) {
            TypeSafeTimeoutException e = assertThrows(TypeSafeTimeoutException.class, () -> call(client));
            assertEquals(Duration.ofMillis(80), e.timeout());
            assertTrue(e.getMessage().contains("80ms"), e.getMessage());
        }
        assertEquals(1, server.requests().size());
    }

    @Test
    void perCallTimeoutOverridesClientTimeout() {
        server.respondWith(r -> MockServer.Reply.json(MockServer.SYSTEM_ONE_OK).delay(300));
        try (TypeSafeClient client = server.clientBuilder().timeout(Duration.ofMillis(50)).build()) {
            RequestOptions options = RequestOptions.builder().timeout(Duration.ofSeconds(5)).build();
            assertEquals(0.5, client.systemOne(SystemOneRequest.of("s", QUESTIONS), options).noul("q").noul());
        }
        assertEquals(1, server.requests().size());
    }

    @Test
    void perCallRetryPolicyOverridesClientPolicy() {
        server.respondWith(r -> MockServer.Reply.json(503, "{}"));
        try (TypeSafeClient client = server.clientBuilder().build()) {
            RequestOptions options = RequestOptions.builder().retryPolicy(RetryPolicy.none()).build();
            assertThrows(InternalServerException.class,
                    () -> client.systemOne(SystemOneRequest.of("s", QUESTIONS), options));
        }
        assertEquals(1, server.requests().size());
    }

    @Test
    void ignoresRetryAfterWhenDisabledOrTooLong() {
        server.enqueue(MockServer.Reply.json(429, "{}").header("retry-after-ms", "3000"));
        try (TypeSafeClient client = server.clientBuilder()
                .retryPolicy(MockServer.fastRetries().toBuilder().respectRetryAfter(false).build()).build()) {
            long started = System.nanoTime();
            call(client);
            assertTrue((System.nanoTime() - started) / 1_000_000 < 1000);
        }
        server.enqueue(MockServer.Reply.json(429, "{}").header("retry-after-ms", "3000"));
        try (TypeSafeClient client = server.clientBuilder()
                .retryPolicy(MockServer.fastRetries().toBuilder().maxRetryAfter(Duration.ofMillis(10)).build()).build()) {
            long started = System.nanoTime();
            call(client);
            assertTrue((System.nanoTime() - started) / 1_000_000 < 1000);
        }
        assertEquals(4, server.requests().size());
    }

    @Test
    void stopsBeforeARetryThatWouldExceedTheBudget() {
        server.respondWith(r -> MockServer.Reply.json(429, "{}").header("retry-after-ms", "800"));
        try (TypeSafeClient client = server.clientBuilder()
                .retryPolicy(MockServer.fastRetries().toBuilder().maxElapsed(Duration.ofMillis(200)).build()).build()) {
            long started = System.nanoTime();
            assertThrows(RateLimitException.class, () -> call(client));
            assertTrue((System.nanoTime() - started) / 1_000_000 < 500);
        }
        assertEquals(1, server.requests().size());
    }

    @Test
    void interruptionStopsWaitingAndRestoresTheFlag() throws Exception {
        server.respondWith(r -> MockServer.Reply.json(429, "{}").header("retry-after-ms", "5000"));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        try (TypeSafeClient client = server.clientBuilder().build()) {
            Thread worker = new Thread(() -> {
                try {
                    call(client);
                } catch (Throwable t) {
                    thrown.set(t);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            worker.start();
            Thread.sleep(200);
            worker.interrupt();
            worker.join(2000);
        }
        assertEquals(TypeSafeInterruptedException.class, thrown.get().getClass());
        assertTrue(interrupted.get());
    }
}
