package io.github.kgonia.typesafe.internal;

import io.github.kgonia.typesafe.LogLevel;
import java.util.function.Supplier;

/** Routes SDK log lines to a {@link System.Logger}, gated by the configured {@link LogLevel}. */
public final class SdkLogger {

    /** The logger name used when no custom logger is configured. */
    public static final String LOGGER_NAME = "io.github.kgonia.typesafe";

    private final System.Logger logger;
    private final LogLevel level;

    /** Creates a gated logger. */
    public SdkLogger(System.Logger logger, LogLevel level) {
        this.logger = logger;
        this.level = level;
    }

    /** The default logger for the SDK. */
    public static System.Logger defaultLogger() {
        return System.getLogger(LOGGER_NAME);
    }

    /** Whether lines at {@code at} are emitted. */
    public boolean enabled(LogLevel at) {
        return level != LogLevel.OFF && at.compareTo(level) >= 0 && logger.isLoggable(toSystem(at));
    }

    /** Logs at debug, evaluating the message lazily. */
    public void debug(Supplier<String> message) {
        if (enabled(LogLevel.DEBUG)) {
            logger.log(System.Logger.Level.DEBUG, message.get());
        }
    }

    /** Logs at info, evaluating the message lazily. */
    public void info(Supplier<String> message) {
        if (enabled(LogLevel.INFO)) {
            logger.log(System.Logger.Level.INFO, message.get());
        }
    }

    /** Logs at warn. */
    public void warn(String message) {
        if (enabled(LogLevel.WARN)) {
            logger.log(System.Logger.Level.WARNING, message);
        }
    }

    private static System.Logger.Level toSystem(LogLevel level) {
        return switch (level) {
            case DEBUG -> System.Logger.Level.DEBUG;
            case INFO -> System.Logger.Level.INFO;
            case WARN -> System.Logger.Level.WARNING;
            case ERROR -> System.Logger.Level.ERROR;
            case OFF -> System.Logger.Level.OFF;
        };
    }
}
