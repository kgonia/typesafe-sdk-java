package io.github.kgonia.typesafe.internal;

import io.github.kgonia.typesafe.LogLevel;
import io.github.kgonia.typesafe.http.RetryPolicy;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Fully resolved, validated client settings. Built by {@code TypeSafeClient.Builder}.
 *
 * @param apiKey the API key
 * @param baseUrl the API root without a trailing slash
 * @param defaultModel the model used when a request names none
 * @param timeout the per-attempt timeout
 * @param retryPolicy the retry policy
 * @param logLevel the SDK log level
 * @param logger a custom logger, or {@code null} for the default
 * @param defaultHeaders headers sent with every request
 * @param httpClient a caller-owned HTTP client, or {@code null} to create one
 */
public record ClientConfig(String apiKey, String baseUrl, String defaultModel, Duration timeout, RetryPolicy retryPolicy,
        LogLevel logLevel, System.Logger logger, Map<String, String> defaultHeaders, HttpClient httpClient) {

    /** Copies the headers. */
    public ClientConfig {
        defaultHeaders = HeaderUtil.merge(defaultHeaders);
    }

    /** Settings without the API key. */
    @Override
    public String toString() {
        return "ClientConfig{baseUrl=" + baseUrl + ", defaultModel=" + defaultModel + ", timeout=" + timeout
                + ", retryPolicy=" + retryPolicy + ", logLevel=" + logLevel + "}";
    }
}
