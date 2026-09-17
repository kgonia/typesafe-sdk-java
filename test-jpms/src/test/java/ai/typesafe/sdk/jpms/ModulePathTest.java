package ai.typesafe.sdk.jpms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ai.typesafe.sdk.TypeSafeClient;
import ai.typesafe.sdk.errors.TypeSafeException;
import ai.typesafe.sdk.jpms.closed.ClosedTicket;
import ai.typesafe.sdk.jpms.opened.OpenedTicket;
import ai.typesafe.sdk.systemone.SystemOneResponse;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Runs on the module path: the SDK is a named module and this test module {@code requires} it. */
class ModulePathTest {

    private static final String RESPONSE = "{\"model\":\"m\",\"answers\":{\"q\":{\"type\":\"noul\",\"noul\":0.7}},"
            + "\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";
    private static final String MODELS = "{\"models\":[{\"name\":\"jev-latest\",\"description\":\"d\",\"release_date\":\"r\"}]}";

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private TypeSafeClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = (exchange.getRequestURI().getPath().endsWith("/models") ? MODELS : RESPONSE)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(out);
            }
        });
        server.start();
        client = ModularConsumer.client("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        client.close();
        server.stop(0);
    }

    @Test
    void sdkIsANamedModuleWithTheExpectedSurface() {
        Module sdk = TypeSafeClient.class.getModule();
        assertTrue(sdk.isNamed());
        assertEquals("ai.typesafe.sdk", sdk.getName());
        for (String pkg : List.of("ai.typesafe.sdk", "ai.typesafe.sdk.systemone", "ai.typesafe.sdk.models",
                "ai.typesafe.sdk.http", "ai.typesafe.sdk.errors")) {
            assertTrue(sdk.isExported(pkg), pkg);
        }
        assertFalse(sdk.isExported("ai.typesafe.sdk.internal"));
        assertFalse(sdk.isExported("ai.typesafe.sdk.internal.json"));
        // requires transitive java.net.http: consumers can see HttpClient and HttpHeaders in the SDK's API.
        assertTrue(getClass().getModule().canRead(HttpClient.class.getModule()));
    }

    @Test
    void worksEndToEndOnTheModulePath() {
        SystemOneResponse response = ModularConsumer.ask(client, Map.of("document", "I was charged twice."));
        assertEquals(0.7, response.noul("q").noul());
        assertEquals("jev-latest", client.models().list().models().get(0).name());
    }

    @Test
    void recordInAnOpenedPackageIsSerialized() {
        ModularConsumer.ask(client, new OpenedTicket("Charged twice", "Please fix"));
        assertTrue(bodies.get(0).contains("\"state\":{\"subject\":\"Charged twice\",\"body\":\"Please fix\"}"), bodies.get(0));
    }

    @Test
    void recordInAClosedPackageExplainsTheFix() {
        TypeSafeException e = assertThrows(TypeSafeException.class,
                () -> ModularConsumer.ask(client, new ClosedTicket("Charged twice", "Please fix")));
        assertTrue(e.getMessage().contains("opens ai.typesafe.sdk.jpms.closed to ai.typesafe.sdk;"), e.getMessage());
        assertTrue(bodies.isEmpty(), "nothing should be sent");
    }
}
