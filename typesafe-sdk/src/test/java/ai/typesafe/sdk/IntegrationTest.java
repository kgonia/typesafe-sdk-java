package ai.typesafe.sdk;

import ai.typesafe.sdk.models.ListModelsResponse;
import ai.typesafe.sdk.systemone.Answer;
import ai.typesafe.sdk.systemone.Question;
import ai.typesafe.sdk.systemone.SystemOneResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Calls the real API. Runs only when {@code TYPESAFE_API_KEY} is set. */
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
class IntegrationTest {

    @Test
    void listsModelsAndAnswersQuestions() {
        try (TypeSafeClient client = TypeSafeClient.builder().logLevel(LogLevel.INFO).build()) {
            ListModelsResponse models = client.models().list();
            assertFalse(models.models().isEmpty());
            assertTrue(models.models().stream().anyMatch(m -> m.name().equals("jev-latest")));

            SystemOneResponse response = client.systemOne(
                    Map.of("subject", "Charged twice this month",
                            "body", "I see two charges of $49 on my card for August. Please fix this ASAP."),
                    Map.of(
                            "billing", Question.noul("Is this ticket about billing?"),
                            "tone", Question.choice("What is the customer's tone?", "calm", "frustrated", "angry"),
                            "urgency", Question.score("How urgent is this ticket?",
                                    List.of("can wait", "this week", "today", "right now"))));

            Answer.Noul billing = response.noul("billing");
            assertTrue(billing.noul() >= 0 && billing.noul() <= 1);
            assertTrue(billing.noul() > 0.5, "expected a billing ticket, got " + billing.noul());
            Answer.Choice tone = response.choice("tone");
            assertTrue(Map.of("calm", 1, "frustrated", 1, "angry", 1).containsKey(tone.choice()));
            assertEquals(1.0, tone.probabilities().values().stream().mapToDouble(Double::doubleValue).sum(), 0.01);
            Answer.Score urgency = response.score("urgency");
            assertTrue(urgency.score() >= 0 && urgency.score() <= 3);
            assertEquals(4, urgency.legend().size());
            assertEquals("can wait", urgency.legend().get(0));
            assertTrue(response.requestId().isPresent());
            assertTrue(response.usage().inputTokens().isPresent());
            assertFalse(response.model().isBlank());
        }
    }
}
