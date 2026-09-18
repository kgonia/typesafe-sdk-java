package io.github.kgonia.typesafe;

/** The SDK's version and identifying strings sent with every request. */
public final class SdkVersion {

    /** The SDK version. Kept in sync with the build's project version. */
    public static final String VERSION = "0.1.0";

    /** The {@code User-Agent} and {@code X-TypeSafe-SDK} value: {@code typesafe-sdk-java/VERSION}. */
    public static final String USER_AGENT = "typesafe-sdk-java/" + VERSION;

    private SdkVersion() {
    }
}
