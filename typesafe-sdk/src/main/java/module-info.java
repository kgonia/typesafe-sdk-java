/**
 * Zero-dependency Java client for the TypeSafe AI System One API.
 *
 * <p>Exported packages: {@code ai.typesafe.sdk} (client), {@code ai.typesafe.sdk.systemone} and
 * {@code ai.typesafe.sdk.models} (endpoints), {@code ai.typesafe.sdk.http} (transport settings), and
 * {@code ai.typesafe.sdk.errors}. Everything under {@code ai.typesafe.sdk.internal} is implementation.
 */
module ai.typesafe.sdk {
    requires transitive java.net.http;

    exports ai.typesafe.sdk;
    exports ai.typesafe.sdk.systemone;
    exports ai.typesafe.sdk.models;
    exports ai.typesafe.sdk.http;
    exports ai.typesafe.sdk.errors;
}
