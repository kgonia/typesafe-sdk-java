package io.github.kgonia.typesafe;

import io.github.kgonia.typesafe.http.RetryPolicy;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/** An in-process HTTP server that records requests and replies from a queue of canned responses. */
final class MockServer implements AutoCloseable {

    /** A recorded request; header names are lower-cased. */
    record Recorded(String method, String path, Map<String, String> headers, String body) {
        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    /** A canned reply. A negative status drops the connection without responding. */
    record Reply(int status, Map<String, String> headers, String body, long delayMillis) {
        static Reply json(String body) {
            return json(200, body);
        }

        static Reply json(int status, String body) {
            return new Reply(status, Map.of("Content-Type", "application/json"), body, 0);
        }

        static Reply text(int status, String body) {
            return new Reply(status, Map.of("Content-Type", "text/plain"), body, 0);
        }

        static Reply empty(int status) {
            return new Reply(status, Map.of(), "", 0);
        }

        static Reply drop() {
            return new Reply(-1, Map.of(), "", 0);
        }

        Reply header(String name, String value) {
            Map<String, String> h = new LinkedHashMap<>(headers);
            h.put(name, value);
            return new Reply(status, h, body, delayMillis);
        }

        Reply delay(long millis) {
            return new Reply(status, headers, body, millis);
        }
    }

    static final String SYSTEM_ONE_OK = """
            {"model":"jev-1.13.0","answers":{"q":{"type":"noul","noul":0.5}},"usage":{"input_tokens":1,"output_tokens":2}}
            """;

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<>());
    private final Deque<Function<Recorded, Reply>> queue = new ArrayDeque<>();
    private volatile Function<Recorded, Reply> fallback = r -> Reply.json(SYSTEM_ONE_OK);

    private MockServer(HttpServer server) {
        this.server = server;
    }

    static MockServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            MockServer mock = new MockServer(server);
            server.createContext("/", mock::handle);
            server.setExecutor(mock.executor);
            server.start();
            return mock;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    List<Recorded> requests() {
        return List.copyOf(requests);
    }

    Recorded lastRequest() {
        return requests.get(requests.size() - 1);
    }

    /** Queues replies to use, in order, before falling back to {@link #respondWith}. */
    MockServer enqueue(Reply... replies) {
        synchronized (queue) {
            for (Reply reply : replies) {
                queue.add(r -> reply);
            }
        }
        return this;
    }

    /** Queues a computed reply. */
    MockServer enqueue(Function<Recorded, Reply> handler) {
        synchronized (queue) {
            queue.add(handler);
        }
        return this;
    }

    /** The reply used when the queue is empty. */
    MockServer respondWith(Function<Recorded, Reply> handler) {
        this.fallback = handler;
        return this;
    }

    /** A client builder pointed at this server with a test key, no environment, and logging off. */
    TypeSafeClient.Builder clientBuilder() {
        return TypeSafeClient.builder().apiKey("test-key-1234567890").baseUrl(baseUrl()).environment(name -> null)
                .logLevel(LogLevel.OFF).retryPolicy(fastRetries());
    }

    static RetryPolicy fastRetries() {
        return RetryPolicy.builder().initialBackoff(java.time.Duration.ofMillis(1))
                .maxBackoff(java.time.Duration.ofMillis(2)).build();
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT),
                String.join(", ", values)));
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Recorded recorded = new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, body);
        requests.add(recorded);
        Function<Recorded, Reply> handler;
        synchronized (queue) {
            handler = queue.isEmpty() ? fallback : queue.poll();
        }
        Reply reply = handler.apply(recorded);
        if (reply.delayMillis() > 0) {
            try {
                Thread.sleep(reply.delayMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (reply.status() < 0) {
            exchange.close();
            return;
        }
        reply.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            if (bytes.length > 0) {
                out.write(bytes);
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
