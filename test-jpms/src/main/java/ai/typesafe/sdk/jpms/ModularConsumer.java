package ai.typesafe.sdk.jpms;

import ai.typesafe.sdk.TypeSafeClient;
import ai.typesafe.sdk.http.RetryPolicy;
import ai.typesafe.sdk.systemone.Question;
import ai.typesafe.sdk.systemone.SystemOneResponse;
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
