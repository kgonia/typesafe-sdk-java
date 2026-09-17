package ai.typesafe.sdk.systemone;

import ai.typesafe.sdk.errors.TypeSafeException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@code POST /v1/systemone} request: the state to evaluate and the questions to ask about it.
 *
 * <p>Questions in one request run in parallel and cannot see one another's answers, so ask every independent
 * question over the same state together.
 *
 * @param state the content to evaluate: a {@link String}, a JSON object ({@link Map} or record), or a JSON array
 *     ({@link java.util.List}). See the <a href="https://docs.typesafe.ai/concepts/state">state guide</a>.
 * @param questions questions keyed by the names under which answers come back; never empty
 * @param model the model to use, or empty to use the client's default
 * @param extraBody extra top-level request fields, merged last-wins over {@code state}, {@code model}, and
 *     {@code questions}; for API features this SDK version predates
 */
public record SystemOneRequest(Object state, Map<String, Question> questions, Optional<String> model,
        Map<String, Object> extraBody) {

    /** Validates the questions and defensively copies the maps. */
    public SystemOneRequest {
        Objects.requireNonNull(state, "state must not be null; pass a String, Map, List, or record");
        Objects.requireNonNull(questions, "questions");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(extraBody, "extraBody");
        if (questions.isEmpty()) {
            throw new TypeSafeException("At least one question is required.");
        }
        Map<String, Question> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Question> entry : questions.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new TypeSafeException("Question names must be non-blank strings.");
            }
            if (entry.getValue() == null) {
                throw new TypeSafeException("Question \"" + entry.getKey() + "\" is null.");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        questions = Collections.unmodifiableMap(copy);
        extraBody = Collections.unmodifiableMap(new LinkedHashMap<>(extraBody));
    }

    /** A request using the client's default model. */
    public static SystemOneRequest of(Object state, Map<String, ? extends Question> questions) {
        Objects.requireNonNull(questions, "questions");
        return new SystemOneRequest(state, new LinkedHashMap<>(questions), Optional.empty(), Map.of());
    }

    /** Starts a builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builds a {@link SystemOneRequest}. */
    public static final class Builder {
        private Object state;
        private final Map<String, Question> questions = new LinkedHashMap<>();
        private String model;
        private final Map<String, Object> extraBody = new LinkedHashMap<>();

        private Builder() {
        }

        /** Sets the content to evaluate: text, a {@link Map}, a {@link java.util.List}, or a record. */
        public Builder state(Object state) {
            this.state = state;
            return this;
        }

        /** Adds one question under the given name. */
        public Builder question(String name, Question question) {
            questions.put(name, question);
            return this;
        }

        /** Adds every question in the map. */
        public Builder questions(Map<String, ? extends Question> questions) {
            this.questions.putAll(questions);
            return this;
        }

        /** Overrides the client's default model for this request. */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /** Adds an extra top-level body field; an existing field of the same name is replaced. */
        public Builder extraBodyField(String name, Object value) {
            extraBody.put(name, value);
            return this;
        }

        /** Creates the request. */
        public SystemOneRequest build() {
            return new SystemOneRequest(state, questions, Optional.ofNullable(model), extraBody);
        }
    }
}
