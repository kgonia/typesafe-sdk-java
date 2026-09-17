package ai.typesafe.sdk.systemone;

import ai.typesafe.sdk.http.ResponseMetadata;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * The answers to a {@link SystemOneRequest}, keyed by the question names you chose.
 *
 * <p>Use the typed accessors when you know the question's type ({@link #noul(String)}, {@link #choice(String)},
 * {@link #score(String)}), the grouped views ({@link #nouls()}, {@link #choices()}, {@link #scores()}), or
 * switch over {@link #answers()} values with the sealed {@link Answer} hierarchy.
 *
 * @param model the versioned model that answered; may differ from the alias requested
 * @param answers one answer per question, in the order returned by the API
 * @param usage token usage for the request
 * @param metadata the HTTP status, headers, request ID, and raw body
 */
public record SystemOneResponse(String model, Map<String, Answer> answers, Usage usage, ResponseMetadata metadata) {

    /** Defensively copies the answers. */
    public SystemOneResponse {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(answers, "answers");
        Objects.requireNonNull(usage, "usage");
        Objects.requireNonNull(metadata, "metadata");
        answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
    }

    /** The {@code x-typesafe-request-id} response header, when present. */
    public Optional<String> requestId() {
        return metadata.requestId();
    }

    /**
     * The answer to the named question.
     *
     * @throws NoSuchElementException when no answer has that name
     */
    public Answer answer(String name) {
        Answer answer = answers.get(name);
        if (answer == null) {
            throw new NoSuchElementException("No answer named \"" + name + "\". Available: " + answers.keySet() + ".");
        }
        return answer;
    }

    /**
     * The answer to the named {@link Question.Noul} question.
     *
     * @throws NoSuchElementException when no answer has that name or it is not a noul answer
     */
    public Answer.Noul noul(String name) {
        return typed(name, Answer.Noul.class, "noul");
    }

    /**
     * The answer to the named {@link Question.Choice} question.
     *
     * @throws NoSuchElementException when no answer has that name or it is not a choice answer
     */
    public Answer.Choice choice(String name) {
        return typed(name, Answer.Choice.class, "choice");
    }

    /**
     * The answer to the named {@link Question.Score} question.
     *
     * @throws NoSuchElementException when no answer has that name or it is not a score answer
     */
    public Answer.Score score(String name) {
        return typed(name, Answer.Score.class, "score");
    }

    /** All noul answers keyed by question name. */
    public Map<String, Answer.Noul> nouls() {
        return ofType(Answer.Noul.class);
    }

    /** All choice answers keyed by question name. */
    public Map<String, Answer.Choice> choices() {
        return ofType(Answer.Choice.class);
    }

    /** All score answers keyed by question name. */
    public Map<String, Answer.Score> scores() {
        return ofType(Answer.Score.class);
    }

    /** Answers whose type this SDK version does not model, keyed by question name. */
    public Map<String, Answer.Unknown> unknownAnswers() {
        return ofType(Answer.Unknown.class);
    }

    private <T extends Answer> T typed(String name, Class<T> type, String label) {
        Answer answer = answer(name);
        if (type.isInstance(answer)) {
            return type.cast(answer);
        }
        throw new NoSuchElementException("Answer \"" + name + "\" is a " + answer.type() + " answer, not a " + label
                + " answer.");
    }

    private <T extends Answer> Map<String, T> ofType(Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>();
        for (Map.Entry<String, Answer> entry : answers.entrySet()) {
            if (type.isInstance(entry.getValue())) {
                result.put(entry.getKey(), type.cast(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
