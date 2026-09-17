/** A modular consumer of the SDK, used only to verify the SDK on the module path. */
module ai.typesafe.sdk.test.jpms {
    requires ai.typesafe.sdk;
    requires jdk.httpserver;

    // Records passed as state are read reflectively, so their package must be opened to the SDK.
    opens ai.typesafe.sdk.jpms.opened to ai.typesafe.sdk;
    // ai.typesafe.sdk.jpms.closed is deliberately neither exported nor opened.
}
