package io.github.kgonia.typesafe;

import io.github.kgonia.typesafe.errors.RateLimitException;
import io.github.kgonia.typesafe.errors.TypeSafeApiException;
import io.github.kgonia.typesafe.http.RetryPolicy;
import io.github.kgonia.typesafe.systemone.Question;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ErrorMappingTest {

    private MockServer server;
    private TypeSafeClient client;

    @BeforeEach
    void start() {
        server = MockServer.start();
        client = server.clientBuilder().retryPolicy(RetryPolicy.none()).build();
    }

    @AfterEach
    void stop() {
        client.close();
        server.close();
    }

    private TypeSafeApiException call(int status, String body) {
        server.enqueue(MockServer.Reply.json(status, body));
        return assertThrows(TypeSafeApiException.class, () -> client.systemOne("s", Map.of("q", Question.noul("Q?"))));
    }

    @ParameterizedTest
    @CsvSource({
            "400, io.github.kgonia.typesafe.errors.BadRequestException",
            "401, io.github.kgonia.typesafe.errors.AuthenticationException",
            "403, io.github.kgonia.typesafe.errors.PermissionDeniedException",
            "404, io.github.kgonia.typesafe.errors.NotFoundException",
            "422, io.github.kgonia.typesafe.errors.UnprocessableEntityException",
            "429, io.github.kgonia.typesafe.errors.RateLimitException",
            "500, io.github.kgonia.typesafe.errors.InternalServerException",
            "529, io.github.kgonia.typesafe.errors.InternalServerException",
            "418, io.github.kgonia.typesafe.errors.TypeSafeApiException"})
    void mapsStatusesToExceptionTypes(int status, String type) throws Exception {
        TypeSafeApiException e = call(status, "{\"message\":\"m\"}");
        assertEquals(Class.forName(type), e.getClass());
        assertEquals(status, e.statusCode());
        assertEquals(Optional.of("POST /v1/systemone"), e.endpoint());
    }

    @Test
    void extractsMessagesFromKnownShapes() {
        assertTrue(call(400, "{\"error\":\"plain\"}").getMessage().endsWith("400 plain"));
        assertTrue(call(400, "{\"error\":{\"message\":\"nested\"}}").getMessage().endsWith("400 nested"));
        assertTrue(call(400, "{\"message\":\"msg\"}").getMessage().endsWith("400 msg"));
        assertTrue(call(400, "{\"detail\":\"det\"}").getMessage().endsWith("400 det"));
        assertTrue(call(400, "{\"detail\":{\"message\":\"dm\"}}").getMessage().endsWith("400 dm"));
        String validation = "{\"detail\":[{\"loc\":[\"body\",\"questions\",\"u\",\"criteria\"],\"msg\":\"Field required\",\"type\":\"missing\"},"
                + "{\"loc\":[\"body\"],\"msg\":\"bad\"},{\"nope\":1}]}";
        assertTrue(call(422, validation).getMessage().endsWith("422 questions.u.criteria: Field required; bad"));
    }

    @Test
    void fallsBackToRawBodyOrNoBody() {
        TypeSafeApiException empty = assertThrows(TypeSafeApiException.class, () -> {
            server.enqueue(MockServer.Reply.empty(502));
            client.systemOne("s", Map.of("q", Question.noul("Q?")));
        });
        assertTrue(empty.getMessage().endsWith("502 status code (no body)"), empty.getMessage());
        assertNull(empty.body());

        TypeSafeApiException text = assertThrows(TypeSafeApiException.class, () -> {
            server.enqueue(MockServer.Reply.text(503, "Service Unavailable"));
            client.systemOne("s", Map.of("q", Question.noul("Q?")));
        });
        assertTrue(text.getMessage().endsWith("503 Service Unavailable"), text.getMessage());
        assertEquals("Service Unavailable", text.body());

        String longBody = "{\"unknown\":\"" + "x".repeat(300) + "\"}";
        TypeSafeApiException truncated = call(400, longBody);
        assertTrue(truncated.getMessage().contains("…"), truncated.getMessage());
        assertTrue(truncated.getMessage().length() < 300, truncated.getMessage());
        assertInstanceOf(Map.class, truncated.body());
    }

    @Test
    void includesRequestIdAndRateLimitDelay() {
        server.enqueue(MockServer.Reply.json(429, "{\"message\":\"slow down\"}")
                .header("x-typesafe-request-id", "req-9").header("retry-after", "7"));
        RateLimitException e = assertThrows(RateLimitException.class,
                () -> client.systemOne("s", Map.of("q", Question.noul("Q?"))));
        assertEquals(Optional.of("req-9"), e.requestId());
        assertEquals(Optional.of(Duration.ofSeconds(7)), e.retryAfter());
        assertTrue(e.getMessage().endsWith("429 slow down (request_id=req-9)"), e.getMessage());
        assertEquals(List.of("7"), e.headers().allValues("retry-after"));
    }

    @Test
    void constructsWithoutHeadersOrDetail() {
        TypeSafeApiException e = new TypeSafeApiException(500, null, null, null, null);
        assertEquals("500", e.getMessage());
        assertEquals(Optional.empty(), new RateLimitException(429, null, null, null, null, null).retryAfter());
        assertEquals(Optional.empty(), e.requestId());
        assertEquals(Optional.empty(), e.endpoint());
        assertEquals(HttpHeaders.of(Map.of(), (a, b) -> true).map(), e.headers().map());
    }
}
