package io.github.kgonia.typesafe.internal;

import java.util.Locale;

/** Describes the JVM for the {@code X-TypeSafe-Runtime} header, as {@code java/21.0.2 (linux; amd64)}. */
public final class RuntimeInfo {

    /** The runtime description computed once per process. */
    public static final String DESCRIPTION = describe();

    private RuntimeInfo() {
    }

    private static String describe() {
        Runtime.Version version = Runtime.version();
        String os = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT).replace(' ', '-');
        String arch = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ROOT);
        return "java/" + version.feature() + "." + version.interim() + "." + version.update() + " (" + os + "; " + arch
                + ")";
    }
}
