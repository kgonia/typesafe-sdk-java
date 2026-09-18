/** A modular consumer of the SDK, used only to verify the SDK on the module path. */
module io.github.kgonia.typesafe.test.jpms {
    requires io.github.kgonia.typesafe;
    requires jdk.httpserver;

    // Records passed as state are read reflectively, so their package must be opened to the SDK.
    opens io.github.kgonia.typesafe.jpms.opened to io.github.kgonia.typesafe;
    // io.github.kgonia.typesafe.jpms.closed is deliberately neither exported nor opened.
}
