import ai.typesafe.sdk.LogLevel;
import ai.typesafe.sdk.TypeSafeClient;
import ai.typesafe.sdk.errors.TypeSafeApiException;
import ai.typesafe.sdk.models.ListModelsResponse;
import ai.typesafe.sdk.models.ModelMetadata;
import ai.typesafe.sdk.systemone.Answer;
import ai.typesafe.sdk.systemone.Question;
import ai.typesafe.sdk.systemone.SystemOneResponse;
import java.util.List;
import java.util.Map;

/**
 * A complete example. Needs TYPESAFE_API_KEY in the environment.
 *
 * <pre>
 * mvn -q package -DskipTests
 * java -cp target/classes examples/Demo.java
 * </pre>
 */
public class Demo {

    record Ticket(String subject, String body) {
    }

    public static void main(String[] args) {
        try (TypeSafeClient client = TypeSafeClient.builder().logLevel(LogLevel.INFO).build()) {
            ListModelsResponse models = client.models().list();
            System.out.println("Available models: "
                    + String.join(", ", models.models().stream().map(ModelMetadata::name).toList()));

            Ticket ticket = new Ticket("Charged twice this month",
                    "Hi, I see two charges of $49 on my card for August. I only have one account. "
                            + "Please fix this ASAP, I'm pretty frustrated.");

            SystemOneResponse response = client.systemOne(ticket, Map.of(
                    "isBilling", Question.noul("Is this ticket about billing?"),
                    "sentiment", Question.choice("What is the customer's tone?", "calm", "frustrated", "angry"),
                    "urgency", Question.score("How urgent is this ticket?",
                            List.of("can wait", "this week", "today", "right now")),
                    "refundRisk", Question.score("How likely is the customer to demand a refund?",
                            List.of("unlikely", "possible", "likely"))));

            Answer.Noul isBilling = response.noul("isBilling");
            Answer.Choice sentiment = response.choice("sentiment");
            Answer.Score urgency = response.score("urgency");
            Answer.Score refundRisk = response.score("refundRisk");

            System.out.printf("billing?     %.2f%n", isBilling.noul());
            System.out.printf("tone         %s (%.2f)%n", sentiment.choice(), sentiment.probabilityOf(sentiment.choice()));
            System.out.printf("urgency      %.2f on a 0-3 scale: %s%n", urgency.score(), urgency.legend());
            System.out.printf("refund risk  %.2f (%.2f confidence)%n", refundRisk.score(), refundRisk.confidence());
            System.out.printf("model        %s%n", response.model());
            System.out.printf("tokens       %s in / %s out%n", response.usage().inputTokens(), response.usage().outputTokens());
        } catch (TypeSafeApiException e) {
            System.err.printf("API error %d (request %s): %s%n", e.statusCode(), e.requestId().orElse("unknown"), e.body());
            System.exit(1);
        }
    }
}
