package ai.typesafe.sdk;

import ai.typesafe.sdk.errors.TypeSafeException;
import ai.typesafe.sdk.http.RetryPolicy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypeSafeClientConfigTest {

    private static TypeSafeClient.Builder builder(Map<String, String> env) {
        return TypeSafeClient.builder().environment(env::get);
    }

    @Test
    void fallsBackToDefaults() {
        try (TypeSafeClient client = builder(Map.of()).apiKey("k").build()) {
            assertEquals(TypeSafeClient.DEFAULT_BASE_URL, client.baseUrl());
            assertEquals(TypeSafeClient.DEFAULT_MODEL, client.defaultModel());
            assertEquals(TypeSafeClient.DEFAULT_LOG_LEVEL, client.logLevel());
            assertEquals(RetryPolicy.defaults(), client.retryPolicy());
            assertEquals(TypeSafeClient.DEFAULT_TIMEOUT, client.timeout());
            assertEquals(Map.of(), client.defaultHeaders());
        }
    }

    @Test
    void readsEverySettingFromTheEnvironment() {
        Map<String, String> env = Map.of(
                TypeSafeClient.API_KEY_ENV, "env-key",
                TypeSafeClient.BASE_URL_ENV, "https://env.test",
                TypeSafeClient.DEFAULT_MODEL_ENV, "env-model",
                TypeSafeClient.LOG_LEVEL_ENV, "debug");
        try (TypeSafeClient client = builder(env).build()) {
            assertEquals("https://env.test", client.baseUrl());
            assertEquals("env-model", client.defaultModel());
            assertEquals(LogLevel.DEBUG, client.logLevel());
        }
    }

    @Test
    void prefersExplicitOverEnvironment() {
        Map<String, String> env = Map.of(
                TypeSafeClient.API_KEY_ENV, "env-key",
                TypeSafeClient.BASE_URL_ENV, "https://env.test",
                TypeSafeClient.DEFAULT_MODEL_ENV, "env-model",
                TypeSafeClient.LOG_LEVEL_ENV, "debug");
        try (TypeSafeClient client = builder(env).apiKey("code-key").baseUrl("https://code.test")
                .defaultModel("code-model").logLevel(LogLevel.ERROR).build()) {
            assertEquals("https://code.test", client.baseUrl());
            assertEquals("code-model", client.defaultModel());
            assertEquals(LogLevel.ERROR, client.logLevel());
        }
    }

    @Test
    void treatsBlankEnvironmentValuesAsUnset() {
        Map<String, String> env = Map.of(
                TypeSafeClient.API_KEY_ENV, "k",
                TypeSafeClient.BASE_URL_ENV, "   ",
                TypeSafeClient.DEFAULT_MODEL_ENV, "",
                TypeSafeClient.LOG_LEVEL_ENV, " ");
        try (TypeSafeClient client = builder(env).build()) {
            assertEquals(TypeSafeClient.DEFAULT_BASE_URL, client.baseUrl());
            assertEquals(TypeSafeClient.DEFAULT_MODEL, client.defaultModel());
            assertEquals(LogLevel.WARN, client.logLevel());
        }
    }

    @Test
    void requiresAnApiKey() {
        TypeSafeException e = assertThrows(TypeSafeException.class, () -> builder(Map.of()).build());
        assertTrue(e.getMessage().contains(TypeSafeClient.API_KEY_ENV), e.getMessage());
        assertThrows(TypeSafeException.class, () -> builder(Map.of(TypeSafeClient.API_KEY_ENV, "  ")).build());
    }

    @Test
    void stripsTrailingSlashesFromBaseUrl() {
        try (TypeSafeClient a = builder(Map.of(TypeSafeClient.BASE_URL_ENV, "https://example.test///")).apiKey("k").build();
                TypeSafeClient b = builder(Map.of()).apiKey("k").baseUrl("https://x.test/").build();
                TypeSafeClient c = builder(Map.of()).apiKey("k").baseUrl(java.net.URI.create("https://y.test/v0/")).build()) {
            assertEquals("https://example.test", a.baseUrl());
            assertEquals("https://x.test", b.baseUrl());
            assertEquals("https://y.test/v0", c.baseUrl());
        }
    }

    @Test
    void rejectsInvalidBaseUrl() {
        assertThrows(TypeSafeException.class, () -> builder(Map.of()).apiKey("k").baseUrl("not a url").build());
        assertThrows(TypeSafeException.class, () -> builder(Map.of()).apiKey("k").baseUrl("ftp://x.test").build());
        assertThrows(TypeSafeException.class, () -> builder(Map.of()).apiKey("k").baseUrl("/relative").build());
    }

    @Test
    void acceptsEveryLogLevelNameAndRejectsOthers() {
        for (String name : new String[] {"debug", "INFO", "warn", "warning", "error", "off"}) {
            try (TypeSafeClient client = builder(Map.of(TypeSafeClient.LOG_LEVEL_ENV, name)).apiKey("k").build()) {
                assertEquals(LogLevel.parse(name, "test"), client.logLevel());
            }
        }
        TypeSafeException e = assertThrows(TypeSafeException.class,
                () -> builder(Map.of(TypeSafeClient.LOG_LEVEL_ENV, "loud")).apiKey("k").build());
        assertTrue(e.getMessage().contains("\"loud\" from " + TypeSafeClient.LOG_LEVEL_ENV), e.getMessage());
        assertTrue(e.getMessage().contains("debug, info, warn, error, off"), e.getMessage());
    }

    @Test
    void validatesTimeout() {
        assertThrows(TypeSafeException.class, () -> builder(Map.of()).apiKey("k").timeout(Duration.ZERO).build());
        assertThrows(TypeSafeException.class, () -> builder(Map.of()).apiKey("k").timeout(Duration.ofSeconds(-1)).build());
        assertThrows(NullPointerException.class, () -> builder(Map.of()).apiKey("k").timeout(null));
    }

    @Test
    void neverExposesTheApiKey() {
        try (TypeSafeClient client = builder(Map.of()).apiKey("super-secret-key").build()) {
            assertFalse(client.toString().contains("super-secret"));
        }
    }

    @Test
    void ownsItsHttpClientUnlessOneIsSupplied() {
        TypeSafeClient owned = builder(Map.of()).apiKey("k").build();
        HttpClient created = owned.httpClient();
        owned.close();
        assertTrue(created.isTerminated());

        HttpClient supplied = HttpClient.newHttpClient();
        try (TypeSafeClient client = builder(Map.of()).apiKey("k").httpClient(supplied).build()) {
            assertSame(supplied, client.httpClient());
        }
        assertFalse(supplied.isTerminated());
        supplied.close();
        assertNotSame(created, supplied);
    }

    @Test
    void createConvenienceFactories() {
        assertThrows(TypeSafeException.class, () -> TypeSafeClient.builder().environment(n -> null).build());
        try (TypeSafeClient client = TypeSafeClient.create("k")) {
            assertEquals(TypeSafeClient.DEFAULT_MODEL, client.defaultModel());
        }
    }
}
