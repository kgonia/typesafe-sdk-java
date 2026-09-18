package io.github.kgonia.typesafe.jpms;

import io.github.kgonia.typesafe.TypeSafeClient;
import io.github.kgonia.typesafe.http.RetryPolicy;
import io.github.kgonia.typesafe.systemone.Question;
import io.github.kgonia.typesafe.systemone.SystemOneResponse;
import java.util.Map;

/** Uses the SDK the way a modular application would. Exercised by the tests in this module. */
public final class ModularConsumer {

    private ModularConsumer() {
    }

    /** Builds a client against {@code baseUrl} with retries off. */
    public static TypeSafeClient client(String baseUrl) {
        return TypeSafeClient.builder().apiKey("jpms-test-key").baseUrl(baseUrl).retryPolicy(RetryPolicy.none()).build();
    }

    /** Asks one yes/no question about {@code state}. */
    public static SystemOneResponse ask(TypeSafeClient client, Object state) {
        return client.systemOne(state, Map.of("q", Question.noul("Is this about billing?")));
    }
}
