package ai.typesafe.sdk.jpms.closed;

/** A public record in a package the module keeps closed. */
public record ClosedTicket(String subject, String body) {
}
