package io.github.kgonia.typesafe;

import io.github.kgonia.typesafe.systemone.Question;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoggingTest {

    private MockServer server;
    private CapturingLogger logger;

    @BeforeEach
    void start() {
        server = MockServer.start();
        logger = new CapturingLogger();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private TypeSafeClient client(LogLevel level) {
        return server.clientBuilder().logger(logger).logLevel(level).build();
    }

    @Test
    void infoLogsOneSummaryLinePerAttemptAndRetries() {
        server.enqueue(MockServer.Reply.json(429, "{}").header("retry-after-ms", "5").header("x-typesafe-request-id", "r1"));
        try (TypeSafeClient client = client(LogLevel.INFO)) {
            client.systemOne("s", Map.of("q", Question.noul("Q?")));
        }
        List<String> lines = logger.messages();
        assertEquals(3, lines.size(), lines.toString());
        assertTrue(lines.get(0).matches("#1 POST /v1/systemone <- 429 in \\d+ms \\(request r1\\)"), lines.get(0));
        assertEquals("#1 POST /v1/systemone retrying in 5ms (retry 1/2) after 429", lines.get(1));
        assertTrue(lines.get(2).matches("#1 POST /v1/systemone <- 200 in \\d+ms"), lines.get(2));
    }

    @Test
    void debugLogsHeadersAndBodiesWithCredentialsRedacted() {
        try (TypeSafeClient client = client(LogLevel.DEBUG)) {
            client.systemOne("secret-state", Map.of("q", Question.noul("Q?")));
        }
        String all = String.join("\n", logger.messages());
        assertTrue(all.contains("-> " + server.baseUrl() + "/v1/systemone"), all);
        assertTrue(all.contains("Bearer ***7890"), all);
        assertFalse(all.contains("test-key-1234567890"), all);
        assertTrue(all.contains("secret-state"), all);
        assertTrue(all.contains("<- headers="), all);
    }

    @Test
    void warnIsTheDefaultAndReportsUnknownAnswerTypes() {
        server.enqueue(MockServer.Reply.json("{\"model\":\"m\",\"answers\":{\"x\":{\"type\":\"mystery\"}},\"usage\":{}}"));
        try (TypeSafeClient client = server.clientBuilder().logger(logger).logLevel(null).environment(n -> null).build()) {
            assertEquals(LogLevel.WARN, client.logLevel());
            client.systemOne("s", Map.of("q", Question.noul("Q?")));
        }
        assertEquals(1, logger.lines.size());
        assertEquals(System.Logger.Level.WARNING, logger.lines.get(0).level());
        assertTrue(logger.lines.get(0).message().contains("mystery"));
    }

    @Test
    void offLogsNothing() {
        server.enqueue(MockServer.Reply.json(503, "{}"));
        try (TypeSafeClient client = client(LogLevel.OFF)) {
            client.systemOne("s", Map.of("q", Question.noul("Q?")));
        }
        assertTrue(logger.messages().isEmpty(), logger.messages().toString());
    }
}
