package io.github.kgonia.typesafe.internal;

import java.net.http.HttpHeaders;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Parses the {@code retry-after-ms} and {@code Retry-After} response headers. */
public final class RetryAfter {

    private RetryAfter() {
    }

    /** Preferred: {@code retry-after-ms}, then {@code Retry-After} in seconds or as an HTTP date. */
    public static Optional<Duration> parse(HttpHeaders headers) {
        return parse(headers, Clock.systemUTC());
    }

    public static Optional<Duration> parse(HttpHeaders headers, Clock clock) {
        if (headers == null) {
            return Optional.empty();
        }
        Optional<String> ms = headers.firstValue("retry-after-ms");
        if (ms.isPresent()) {
            Optional<Duration> parsed = parseNumber(ms.get(), 1);
            if (parsed.isPresent()) {
                return parsed;
            }
        }
        Optional<String> raw = headers.firstValue("retry-after");
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        Optional<Duration> seconds = parseNumber(raw.get(), 1000);
        if (seconds.isPresent()) {
            return seconds;
        }
        try {
            ZonedDateTime at = ZonedDateTime.parse(raw.get().trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
            Duration until = Duration.between(clock.instant(), at.toInstant());
            return Optional.of(until.isNegative() ? Duration.ZERO : until);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static Optional<Duration> parseNumber(String raw, long multiplier) {
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
                return Optional.empty();
            }
            double millis = value * multiplier;
            if (millis > Long.MAX_VALUE / 2) {
                return Optional.empty();
            }
            return Optional.of(Duration.ofMillis(Math.round(millis)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
