/**
 * Zero-dependency Java client for the TypeSafe AI System One API. Start with
 * {@link ai.typesafe.sdk.TypeSafeClient}.
 *
 * <pre>{@code
 * try (TypeSafeClient client = TypeSafeClient.create()) {   // reads TYPESAFE_API_KEY
 *     SystemOneResponse response = client.systemOne(
 *         Map.of("document", "I was charged twice. Please fix this ASAP."),
 *         Map.of(
 *             "billing", Question.noul("Is this ticket about billing?"),
 *             "tone", Question.choice("What is the customer's tone?", "calm", "frustrated", "angry"),
 *             "urgency", Question.score("How urgent is this ticket?", "can wait", "this week", "today")));
 *
 *     double billing = response.noul("billing").noul();
 *     String tone = response.choice("tone").choice();
 *     double urgency = response.score("urgency").score();
 * }
 * }</pre>
 *
 * <table class="striped">
 *   <caption>Packages</caption>
 *   <tr><th>Package</th><th>Contents</th></tr>
 *   <tr><td>{@code ai.typesafe.sdk}</td><td>The client and its client-level settings</td></tr>
 *   <tr><td>{@link ai.typesafe.sdk.systemone}</td><td>Questions, answers, and the System One request and response</td></tr>
 *   <tr><td>{@link ai.typesafe.sdk.models}</td><td>The Models resource and its types</td></tr>
 *   <tr><td>{@link ai.typesafe.sdk.http}</td><td>Per-call options, retry policy, and response metadata</td></tr>
 *   <tr><td>{@link ai.typesafe.sdk.errors}</td><td>Exceptions</td></tr>
 *   <tr><td>{@code ai.typesafe.sdk.internal}</td><td>Implementation; not exported</td></tr>
 * </table>
 *
 * @see <a href="https://docs.typesafe.ai/">TypeSafe documentation</a>
 */
package ai.typesafe.sdk;
