package ai.typesafe.sdk;

import ai.typesafe.sdk.errors.AuthenticationException;
import ai.typesafe.sdk.errors.ResponseValidationException;
import ai.typesafe.sdk.models.ListModelsResponse;
import ai.typesafe.sdk.models.ModelMetadata;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelsTest {

    private static final String MODELS = """
            {"models":[{"name":"jev-latest","description":"Latest","release_date":"2026-09-15"},
                       {"name":"jev-preview","description":"Preview","release_date":"2026-09-15"}]}
            """;

    private MockServer server;
    private TypeSafeClient client;

    @BeforeEach
    void start() {
        server = MockServer.start();
        client = server.clientBuilder().build();
    }

    @AfterEach
    void stop() {
        client.close();
        server.close();
    }

    @Test
    void listsModels() {
        server.enqueue(MockServer.Reply.json(MODELS).header("x-typesafe-request-id", "m-1"));
        ListModelsResponse response = client.models().list();
        assertEquals(List.of(new ModelMetadata("jev-latest", "Latest", "2026-09-15"),
                new ModelMetadata("jev-preview", "Preview", "2026-09-15")), response.models());
        assertEquals(Optional.of("m-1"), response.requestId());
        MockServer.Recorded request = server.lastRequest();
        assertEquals("GET", request.method());
        assertEquals("/v1/models", request.path());
        assertEquals("", request.body());
        assertNull(request.header("Content-Type"));
        assertEquals("Bearer test-key-1234567890", request.header("Authorization"));
    }

    @Test
    void listsModelsAsync() throws Exception {
        server.enqueue(MockServer.Reply.json(MODELS));
        assertEquals(2, client.models().listAsync().get(5, TimeUnit.SECONDS).models().size());
    }

    @Test
    void mapsErrorsAndInvalidBodies() {
        server.enqueue(MockServer.Reply.json(401, "{\"message\":\"bad key\"}"));
        assertThrows(AuthenticationException.class, () -> client.models().list());
        server.enqueue(MockServer.Reply.json("{\"models\":[{\"description\":\"no name\"}]}"));
        assertEquals("models[0].name", assertThrows(ResponseValidationException.class, () -> client.models().list()).fieldPath());
        server.enqueue(MockServer.Reply.json("[]"));
        assertEquals("body", assertThrows(ResponseValidationException.class, () -> client.models().list()).fieldPath());
    }
}
