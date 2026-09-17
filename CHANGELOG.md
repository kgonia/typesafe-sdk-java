# Changelog

## 0.1.0

Initial release.

- Packages by concern: `ai.typesafe.sdk` (client), `systemone`, `models`, `http`, `errors`, and unexported `internal`.
- `TypeSafeClient` and `Models` interfaces with synchronous and `CompletableFuture` variants of `systemOne` and `models().list()`.
- Sealed `Question` (`Noul`, `Choice`, `Score`, `Raw`) and sealed `Answer` (`Noul`, `Choice`, `Score`, `Unknown`).
- Configuration from the builder, `TYPESAFE_*` environment variables, and defaults.
- Retries with exponential backoff, jitter, `Retry-After` support, and an optional total time budget.
- Per-attempt timeouts, cancellation, and interrupt handling.
- Status-specific exceptions with request IDs and parsed error bodies.
- Logging through `System.Logger` with credential redaction.
- No runtime dependencies: JDK `HttpClient` and a built-in JSON codec.
