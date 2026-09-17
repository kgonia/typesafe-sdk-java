package ai.typesafe.sdk;

import ai.typesafe.sdk.errors.RateLimitException;
import ai.typesafe.sdk.errors.TypeSafeException;
import ai.typesafe.sdk.errors.TypeSafeTimeoutException;
import ai.typesafe.sdk.http.RequestOptions;
import ai.typesafe.sdk.http.RetryPolicy;
import ai.typesafe.sdk.systemone.Question;
import ai.typesafe.sdk.systemone.SystemOneRequest;
import ai.typesafe.sdk.systemone.SystemOneResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AsyncTest {

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

    @Test
    void completesWithDecodedResponse() throws Exception {
        try (TypeSafeClient client = server.clientBuilder().build()) {
            SystemOneResponse response = client.systemOneAsync("s", QUESTIONS).get(5, TimeUnit.SECONDS);
            assertEquals(0.5, response.noul("q").noul());
            assertEquals("jev-1.13.0", response.model());
        }
    }

    @Test
    void failsWithTheSameExceptionsAsSync() {
        server.respondWith(r -> MockServer.Reply.json(429, "{\"message\":\"slow\"}"));
        try (TypeSafeClient client = server.clientBuilder().retryPolicy(RetryPolicy.none()).build()) {
            ExecutionException e = assertThrows(ExecutionException.class,
                    () -> client.systemOneAsync("s", QUESTIONS).get(5, TimeUnit.SECONDS));
            assertInstanceOf(RateLimitException.class, e.getCause());
        }
    }

    @Test
    void retriesAsynchronously() throws Exception {
        server.enqueue(MockServer.Reply.json(503, "{}"), MockServer.Reply.drop());
        try (TypeSafeClient client = server.clientBuilder().build()) {
            assertEquals(0.5, client.systemOneAsync("s", QUESTIONS).get(5, TimeUnit.SECONDS).noul("q").noul());
        }
        assertEquals(3, server.requests().size());
    }

    @Test
    void timesOutAsynchronously() {
        server.respondWith(r -> MockServer.Reply.json(MockServer.SYSTEM_ONE_OK).delay(500));
        try (TypeSafeClient client = server.clientBuilder().timeout(Duration.ofMillis(60))
                .retryPolicy(RetryPolicy.none()).build()) {
            ExecutionException e = assertThrows(ExecutionException.class,
                    () -> client.systemOneAsync("s", QUESTIONS).get(5, TimeUnit.SECONDS));
            assertInstanceOf(TypeSafeTimeoutException.class, e.getCause());
        }
    }

    @Test
    void cancellationStopsInFlightRequestsAndPendingRetries() throws Exception {
        server.respondWith(r -> MockServer.Reply.json(429, "{}").header("retry-after-ms", "2000"));
        try (TypeSafeClient client = server.clientBuilder().build()) {
            CompletableFuture<SystemOneResponse> future = client.systemOneAsync("s", QUESTIONS);
            Thread.sleep(200);
            assertTrue(future.cancel(true));
            assertThrows(CancellationException.class, () -> future.get(1, TimeUnit.SECONDS));
            Thread.sleep(2300);
            assertEquals(1, server.requests().size(), "no retry should run after cancellation");
        }
    }

    @Test
    void cancellationDuringAnAttemptCancelsTheRequest() throws Exception {
        server.respondWith(r -> MockServer.Reply.json(MockServer.SYSTEM_ONE_OK).delay(3000));
        try (TypeSafeClient client = server.clientBuilder().retryPolicy(RetryPolicy.none()).build()) {
            long started = System.nanoTime();
            CompletableFuture<SystemOneResponse> future = client.systemOneAsync("s", QUESTIONS);
            Thread.sleep(100);
            future.cancel(true);
            assertTrue(future.isCancelled());
            assertTrue((System.nanoTime() - started) / 1_000_000 < 2000);
        }
    }

    @Test
    void invalidRequestsFailTheFutureInsteadOfThrowing() {
        try (TypeSafeClient client = server.clientBuilder().build()) {
            assertThrows(TypeSafeException.class, () -> client.systemOneAsync("s", Map.of()));
            CompletableFuture<SystemOneResponse> future = client.systemOneAsync(
                    SystemOneRequest.of("s", QUESTIONS), RequestOptions.builder().header("Host", "x").build());
            ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertInstanceOf(TypeSafeException.class, e.getCause());
        }
    }
}
