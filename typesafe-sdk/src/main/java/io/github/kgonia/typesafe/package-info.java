/**
 * Zero-dependency Java client for the TypeSafe AI System One API. Start with
 * {@link io.github.kgonia.typesafe.TypeSafeClient}.
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
 *   <tr><td>{@code io.github.kgonia.typesafe}</td><td>The client and its client-level settings</td></tr>
 *   <tr><td>{@link io.github.kgonia.typesafe.systemone}</td><td>Questions, answers, and the System One request and response</td></tr>
 *   <tr><td>{@link io.github.kgonia.typesafe.models}</td><td>The Models resource and its types</td></tr>
 *   <tr><td>{@link io.github.kgonia.typesafe.http}</td><td>Per-call options, retry policy, and response metadata</td></tr>
 *   <tr><td>{@link io.github.kgonia.typesafe.errors}</td><td>Exceptions</td></tr>
 *   <tr><td>{@code io.github.kgonia.typesafe.internal}</td><td>Implementation; not exported</td></tr>
 * </table>
 *
 * @see <a href="https://docs.typesafe.ai/">TypeSafe documentation</a>
 */
package io.github.kgonia.typesafe;
