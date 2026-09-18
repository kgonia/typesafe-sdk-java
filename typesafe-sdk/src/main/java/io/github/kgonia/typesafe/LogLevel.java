package io.github.kgonia.typesafe;

import io.github.kgonia.typesafe.errors.TypeSafeException;
import java.util.Locale;

/**
 * How much the SDK logs, from most to least verbose.
 *
 * <ul>
 *   <li>{@link #DEBUG}: request and response headers and bodies (credential headers redacted; bodies not)</li>
 *   <li>{@link #INFO}: one summary line per attempt, plus retries</li>
 *   <li>{@link #WARN}: unexpected but recoverable situations, such as unknown answer types (the default)</li>
 *   <li>{@link #ERROR}: reserved for failures the SDK cannot report through an exception</li>
 *   <li>{@link #OFF}: nothing</li>
 * </ul>
 *
 * <p>Messages go to the {@link System.Logger} named {@code io.github.kgonia.typesafe}, which routes to {@code java.util.logging}
 * by default and to SLF4J, Log4j, or another backend when one provides a {@code System.LoggerFinder}. The backend's
 * own level applies on top of this one.
 */
public enum LogLevel {
    /** Headers and bodies. */
    DEBUG,
    /** One line per attempt. */
    INFO,
    /** Recoverable surprises. */
    WARN,
    /** Unreportable failures. */
    ERROR,
    /** Nothing. */
    OFF;

    /**
     * Parses a level name such as {@code debug}, {@code info}, {@code warn} (or {@code warning}), {@code error},
     * or {@code off}, ignoring case.
     *
     * @param value the level name
     * @param source where the value came from, for the error message
     * @throws TypeSafeException when the value is not a level name
     */
    public static LogLevel parse(String value, String source) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "debug" -> DEBUG;
            case "info" -> INFO;
            case "warn", "warning" -> WARN;
            case "error" -> ERROR;
            case "off" -> OFF;
            default -> throw new TypeSafeException("Invalid log level \"" + value + "\" from " + source
                    + ". Expected one of: debug, info, warn, error, off.");
        };
    }
}
