/**
 * Zero-dependency Java client for the TypeSafe AI System One API.
 *
 * <p>Exported packages: {@code io.github.kgonia.typesafe} (client), {@code io.github.kgonia.typesafe.systemone} and
 * {@code io.github.kgonia.typesafe.models} (endpoints), {@code io.github.kgonia.typesafe.http} (transport settings), and
 * {@code io.github.kgonia.typesafe.errors}. Everything under {@code io.github.kgonia.typesafe.internal} is implementation.
 */
module io.github.kgonia.typesafe {
    requires transitive java.net.http;

    exports io.github.kgonia.typesafe;
    exports io.github.kgonia.typesafe.systemone;
    exports io.github.kgonia.typesafe.models;
    exports io.github.kgonia.typesafe.http;
    exports io.github.kgonia.typesafe.errors;
}
