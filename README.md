# TypeSafe Java SDK

Zero-dependency Java client for the [TypeSafe AI](https://typesafe.ai) System One API. Requires Java 21 or newer.

TypeSafe turns natural language and application state into typed judgments: yes/no probabilities, choices with
full distributions, and scores on rubrics you define. Learn what you can build in the
[TypeSafe docs](https://docs.typesafe.ai/).

## Quickstart

Add the dependency:

```xml
<dependency>
    <groupId>io.github.kgonia</groupId>
    <artifactId>typesafe-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

Set `TYPESAFE_API_KEY` in your environment (create a key in the [console](https://console.typesafe.ai/)), then:

```java
import io.github.kgonia.typesafe.TypeSafeClient;
import io.github.kgonia.typesafe.systemone.*;
import java.util.Map;

try (TypeSafeClient client = TypeSafeClient.create()) {
    SystemOneResponse response = client.systemOne(
        Map.of("document", "I was charged twice. Please fix this ASAP."),
        Map.of(
            "billing", Question.noul("Is this ticket about billing?"),
            "tone", Question.choice("What is the customer's tone?", "calm", "frustrated", "angry"),
            "urgency", Question.score("How urgent is this ticket?", "can wait", "this week", "today")));

    System.out.println(response.noul("billing").noul());      // 0.97
    System.out.println(response.choice("tone").choice());     // "frustrated"
    System.out.println(response.score("urgency").score());    // 1.8
}
```

## Questions and answers

| Need                                | Question                                  | Answer                                                   |
|-------------------------------------|-------------------------------------------|----------------------------------------------------------|
| Whether a condition holds           | `Question.noul(instructions)`             | `Answer.Noul`: `noul()` probability of yes                |
| One of a defined set                | `Question.choice(instructions, options…)` | `Answer.Choice`: `choice()`, `probabilities()`, `confidence()` |
| Degree along an ordered rubric      | `Question.score(instructions, levels…)`   | `Answer.Score`: `score()`, `legend()`, `probabilities()`, `confidence()` |

Instructions and criteria descriptions can be a `String`, a `Map` (JSON object), or a `List` (JSON array).
The `state` can be a `String`, a `Map`, a `List`, or a record.

```java
Question.Choice tone = Question.Choice.builder("What is the customer's tone?")
    .option("calm")
    .option("frustrated", "Annoyed but polite")
    .option("angry", "Hostile or threatening")
    .build();

Question.Noul spam = Question.noul("Is this message spam?",
    "Unsolicited advertising or scams",     // what a yes means
    "A genuine message from a person");     // what a no means
```

Answers form a sealed hierarchy, so you can switch over them exhaustively:

```java
for (var entry : response.answers().entrySet()) {
    String line = switch (entry.getValue()) {
        case Answer.Noul n -> "yes with p=" + n.noul();
        case Answer.Choice c -> "chose " + c.choice() + " (confidence " + c.confidence() + ")";
        case Answer.Score s -> "scored " + s.score() + " = " + s.nearestLevelDescription();
        case Answer.Unknown u -> "unrecognized answer type " + u.type();
    };
    System.out.println(entry.getKey() + ": " + line);
}
```

## Configuration

Explicit builder settings win over environment variables, which win over SDK defaults. Blank environment values
are ignored.

| Variable                 | Configures                  | Default                   |
|--------------------------|-----------------------------|---------------------------|
| `TYPESAFE_API_KEY`       | API key (required)          | none                      |
| `TYPESAFE_BASE_URL`      | API root URL                | `https://api.typesafe.ai` |
| `TYPESAFE_DEFAULT_MODEL` | Model when a request names none | `jev-latest`          |
| `TYPESAFE_LOG_LEVEL`     | SDK log level               | `warn`                    |

```java
TypeSafeClient client = TypeSafeClient.builder()
    .apiKey("...")
    .defaultModel("jev-1.13.0")                       // pin a version instead of the alias
    .timeout(Duration.ofSeconds(20))                  // per attempt
    .retryPolicy(RetryPolicy.builder().maxRetries(5).build())
    .defaultHeader("X-Team", "support-bot")
    .logLevel(LogLevel.INFO)
    .build();
```

The client is thread-safe. Create one per application and share it. Close it when you are done; `close()` shuts
down the HTTP client the SDK created. An `HttpClient` you pass through `builder().httpClient(...)` stays open for
you to manage.

### Per-request options

```java
SystemOneRequest request = SystemOneRequest.builder()
    .state(ticket)
    .question("billing", Question.noul("Is this about billing?"))
    .model("jev-preview")
    .build();

RequestOptions options = RequestOptions.builder()
    .timeout(Duration.ofSeconds(30))
    .retryPolicy(RetryPolicy.none())
    .header("X-Request-Source", "batch-job")
    .build();

SystemOneResponse response = client.systemOne(request, options);
```

## Async

Every call has an `Async` variant returning a `CompletableFuture`. Cancelling the future cancels the in-flight
request and any pending retry.

```java
CompletableFuture<SystemOneResponse> future = client.systemOneAsync(state, questions);
future.thenAccept(r -> System.out.println(r.choice("tone").choice()));
```

The synchronous methods block on the same machinery and park cleanly on virtual threads.

## Errors

All exceptions are unchecked and extend `TypeSafeException`.

```java
import io.github.kgonia.typesafe.errors.*;

try {
    client.systemOne(state, questions);
} catch (RateLimitException e) {
    // 429 after all retries; e.retryAfter() holds the server's requested delay
} catch (TypeSafeApiException e) {
    System.err.println(e.statusCode() + " " + e.requestId().orElse("-") + ": " + e.getMessage());
    Object body = e.body(); // parsed JSON, text, or null
} catch (TypeSafeTimeoutException e) {
    // an attempt exceeded its timeout, after all retries
} catch (TypeSafeConnectionException e) {
    // the request never got a response, after all retries
}
```

| Status | Exception                        |
|--------|----------------------------------|
| 400    | `BadRequestException`            |
| 401    | `AuthenticationException`        |
| 403    | `PermissionDeniedException`      |
| 404    | `NotFoundException`              |
| 422    | `UnprocessableEntityException`   |
| 429    | `RateLimitException`             |
| 5xx    | `InternalServerException`        |
| other  | `TypeSafeApiException`           |

A 2xx response whose body does not match the expected shape raises `ResponseValidationException` with the
offending `fieldPath()`.

## Retries

By default the SDK retries HTTP 408, 429, and 5xx responses, connection errors, and timeouts up to two times,
waiting 500ms, then 1s, capped at 5s, with 25% jitter. It honors `Retry-After` and `retry-after-ms` headers up to
60 seconds. Retried attempts carry an `X-TypeSafe-Retry-Count` header.

```java
RetryPolicy policy = RetryPolicy.defaults().toBuilder()
    .maxRetries(4)
    .maxElapsed(Duration.ofSeconds(45))   // total budget per call, including delays
    .build();
```

`RetryPolicy.none()` disables retries.

## Models

```java
for (ModelMetadata model : client.models().list().models()) {
    System.out.println(model.name() + "  " + model.releaseDate() + "  " + model.description());
}
```

The `model` field on a response reports the versioned ID that answered, so you can log which model produced each
result even when you request an alias.

## Logging

The SDK logs through `System.getLogger("io.github.kgonia.typesafe")`, which reaches `java.util.logging` by default and SLF4J
or Log4j when they provide a `System.LoggerFinder`. `info` logs one line per attempt; `debug` adds headers and
bodies. Credential headers are redacted from log output. Request and response bodies are not.

Set `TYPESAFE_LOG_LEVEL`, call `builder().logLevel(LogLevel.DEBUG)`, or pass your own `System.Logger` with
`builder().logger(...)`.

## Forward compatibility

Use `SystemOneRequest.Builder.extraBodyField` for request fields this SDK version predates, and `Question.raw` for
question fields it does not model:

```java
Question weighted = Question.raw(Map.of("type", "noul", "instructions", "About billing?", "weight", 2));
```

Answer types the SDK does not recognize arrive as `Answer.Unknown` with their full JSON. Unknown fields on known
responses are ignored, and the raw body is always available through `response.metadata().rawBody()`.

## Example

`examples/Demo.java` is a complete program. Build the SDK and run it as a single-file program:

```sh
mvn -q package -DskipTests
TYPESAFE_API_KEY=... java -cp target/classes examples/Demo.java
```

## Repository layout

```
typesafe-sdk-java/
├── pom.xml          parent: shared plugin versions and Java 21 floor
├── typesafe-sdk/    the library; the only published artifact (io.github.kgonia:typesafe-sdk)
├── examples/        runnable samples, built with the SDK, never published
└── test-jpms/       a modular consumer that verifies the SDK on the module path
```

Inside the library, the root package holds only the entry point. Each API feature has its own package, HTTP-level
settings are shared in one place, and everything under `internal` is implementation that the module does not
export.

| Package                     | Contents                                                              |
|-----------------------------|-----------------------------------------------------------------------|
| `io.github.kgonia.typesafe`           | `TypeSafeClient` (interface and builder), `LogLevel`. Start here.     |
| `io.github.kgonia.typesafe.systemone` | `Question`, `Answer`, `SystemOneRequest`, `SystemOneResponse`, `Usage` |
| `io.github.kgonia.typesafe.models`    | `Models` (interface), `ModelMetadata`, `ListModelsResponse`           |
| `io.github.kgonia.typesafe.http`      | `RequestOptions`, `RetryPolicy`, `ResponseMetadata`                   |
| `io.github.kgonia.typesafe.errors`    | `TypeSafeException` and its subclasses                                |
| `io.github.kgonia.typesafe.internal`  | Client and resource implementations, transport, codec. Not exported.  |

`TypeSafeClient` and `Models` are interfaces, so code that depends on them can be tested with a mock. Question
and answer kinds are nested in their sealed parents: `Question.Noul`, `Question.Choice`, `Question.Score`,
`Question.Raw`, and `Answer.Noul`, `Answer.Choice`, `Answer.Score`, `Answer.Unknown`.

## Modular applications

The SDK is a named module, `io.github.kgonia.typesafe`. Add `requires io.github.kgonia.typesafe;` to your `module-info.java`. If you
pass records as `state`, the SDK reads their components reflectively, so open their package to it:

```java
opens com.example.tickets to io.github.kgonia.typesafe;
```

Without that, the SDK raises a `TypeSafeException` naming the package to open. Passing a `Map` needs no opens.

## Example

`examples/` holds a complete program. Install the SDK locally once, then run it:

```sh
mvn -q install -DskipTests
TYPESAFE_API_KEY=... mvn -q -pl examples exec:java
```

## Development

```sh
mvn verify                       # unit tests, module-path tests, Javadoc, sources jar, zero-dependency check
TYPESAFE_API_KEY=... mvn verify  # also runs the live integration test
```

## Releasing

One-time setup: create an account at [central.sonatype.com](https://central.sonatype.com), verify the
`io.github.kgonia` namespace, generate a user token, and put it in `~/.m2/settings.xml` as
`<server><id>central</id>...</server>`. Generate a GPG key and publish it to a keyserver.

```sh
mvn -P release deploy
```

This signs and uploads the library and its parent pom, then waits for you to press Publish in the portal.
Bump `project.build.outputTimestamp` in the parent pom for each release so builds stay reproducible.

## License

MIT
