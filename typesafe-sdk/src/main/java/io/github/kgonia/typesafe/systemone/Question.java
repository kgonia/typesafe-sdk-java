package io.github.kgonia.typesafe.systemone;

import io.github.kgonia.typesafe.errors.TypeSafeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A typed question to ask about a {@code state}. One of {@link Noul} (yes/no), {@link Choice} (pick one option),
 * {@link Score} (rate on an ordered rubric), or {@link Raw} (any JSON, for API features this SDK version
 * predates).
 *
 * <p>Instructions and criteria descriptions can be a {@link String}, a JSON object ({@link Map}), a JSON array
 * ({@link List}), or {@code null}. See the <a href="https://docs.typesafe.ai/primitives">primitives guide</a>
 * for how to write good questions.
 *
 * <pre>{@code
 * Map<String, Question> questions = Map.of(
 *     "billing", Question.noul("Is this ticket about billing?"),
 *     "tone", Question.choice("What is the customer's tone?", "calm", "frustrated", "angry"),
 *     "urgency", Question.score("How urgent is this?", "can wait", "this week", "today"));
 * }</pre>
 */
public sealed interface Question permits Question.Noul, Question.Choice, Question.Score, Question.Raw {

    /** The wire {@code type} of this question: {@code noul}, {@code choice}, {@code score}, or a raw value. */
    String type();

    // ---------------------------------------------------------------------------------------------
    // Factories
    // ---------------------------------------------------------------------------------------------

    /** A yes/no question. See {@link Noul}. */
    static Noul noul(Object instructions) {
        return Noul.of(instructions);
    }

    /** A yes/no question with descriptions of what a yes and a no mean. See {@link Noul}. */
    static Noul noul(Object instructions, Object yesMeans, Object noMeans) {
        return Noul.of(instructions, yesMeans, noMeans);
    }

    /** A question that picks one of the given undescribed options. See {@link Choice}. */
    static Choice choice(Object instructions, String... options) {
        return Choice.of(instructions, options);
    }

    /** A question that picks one option from a map of option to description ({@code null} allowed). */
    static Choice choice(Object instructions, Map<String, ?> criteria) {
        return Choice.of(instructions, criteria);
    }

    /** A question that rates the state on an ordered rubric of at least two levels. See {@link Score}. */
    static Score score(Object instructions, String... levels) {
        return Score.of(instructions, levels);
    }

    /** A question that rates the state on an ordered rubric of at least two levels. See {@link Score}. */
    static Score score(Object instructions, List<?> levels) {
        return Score.of(instructions, levels);
    }

    /** A question sent exactly as given; {@code fields} must include a string {@code type}. See {@link Raw}. */
    static Raw raw(Map<String, ?> fields) {
        return Raw.of(fields);
    }

    // ---------------------------------------------------------------------------------------------
    // Noul
    // ---------------------------------------------------------------------------------------------

    /**
     * A yes/no question. The answer is an {@link Answer.Noul} holding the probability of yes.
     *
     * @param instructions the question as text, a JSON object, or a JSON array; may be {@code null}
     * @param criteria optional descriptions of what a yes and a no mean; may be {@code null}
     * @see <a href="https://docs.typesafe.ai/primitives/noul">Noul primitive</a>
     */
    record Noul(Object instructions, Criteria criteria) implements Question {

        /**
         * Optional descriptions of the two outcomes of a {@link Noul} question.
         *
         * @param yesMeans what a yes (value near 1) means; text, object, array, or {@code null}
         * @param noMeans what a no (value near 0) means; text, object, array, or {@code null}
         */
        public record Criteria(Object yesMeans, Object noMeans) {
        }

        /** A yes/no question without outcome descriptions. */
        public static Noul of(Object instructions) {
            return new Noul(instructions, null);
        }

        /** A yes/no question describing what a yes ({@code yesMeans}) and a no ({@code noMeans}) look like. */
        public static Noul of(Object instructions, Object yesMeans, Object noMeans) {
            return new Noul(instructions, new Criteria(yesMeans, noMeans));
        }

        @Override
        public String type() {
            return "noul";
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Choice
    // ---------------------------------------------------------------------------------------------

    /**
     * A question that selects one option from a set you define. The answer is an {@link Answer.Choice} with the
     * chosen option and the full probability distribution.
     *
     * <p>Options are given in order as a map from option name to an optional description (text, object, array,
     * or {@code null}). Use {@link #builder(Object)} to mix described and undescribed options, because
     * {@link Map#of} rejects {@code null} values.
     *
     * @param instructions what the model should decide; text, object, array, or {@code null}
     * @param criteria option name to description; never empty
     * @see <a href="https://docs.typesafe.ai/primitives/choice">Choice primitive</a>
     */
    record Choice(Object instructions, Map<String, Object> criteria) implements Question {

        /** Validates and defensively copies the criteria, preserving their order. */
        public Choice {
            Objects.requireNonNull(criteria, "criteria");
            if (criteria.isEmpty()) {
                throw new TypeSafeException("Choice criteria must contain at least one option.");
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : criteria.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new TypeSafeException("Choice option names must be non-blank strings.");
                }
                copy.put(entry.getKey(), entry.getValue());
            }
            criteria = Collections.unmodifiableMap(copy);
        }

        /** A choice between undescribed options. */
        public static Choice of(Object instructions, String... options) {
            Objects.requireNonNull(options, "options");
            Map<String, Object> criteria = new LinkedHashMap<>();
            for (String option : options) {
                criteria.put(option, null);
            }
            return new Choice(instructions, criteria);
        }

        /** A choice between options with descriptions; a {@code null} description leaves the option undescribed. */
        public static Choice of(Object instructions, Map<String, ?> criteria) {
            Objects.requireNonNull(criteria, "criteria");
            return new Choice(instructions, new LinkedHashMap<>(criteria));
        }

        /** Starts a builder for a choice question. */
        public static Builder builder(Object instructions) {
            return new Builder(instructions);
        }

        @Override
        public String type() {
            return "choice";
        }

        /** Builds a {@link Choice} one option at a time. */
        public static final class Builder {
            private final Object instructions;
            private final Map<String, Object> criteria = new LinkedHashMap<>();

            private Builder(Object instructions) {
                this.instructions = instructions;
            }

            /** Adds an undescribed option. */
            public Builder option(String name) {
                criteria.put(name, null);
                return this;
            }

            /** Adds an option with a description (text, object, or array). */
            public Builder option(String name, Object description) {
                criteria.put(name, description);
                return this;
            }

            /** Creates the question. */
            public Choice build() {
                return new Choice(instructions, criteria);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Score
    // ---------------------------------------------------------------------------------------------

    /**
     * A question that rates the state on an ordered rubric. The answer is an {@link Answer.Score} whose score is
     * the probability-weighted position across the levels, so it can land between levels.
     *
     * <p>Levels are indexed from zero in the order given. Each level should describe a concrete situation that
     * stands on its own; a level may be text, an object, an array, or {@code null}.
     *
     * @param instructions what the model should rate; text, object, array, or {@code null}
     * @param criteria the ordered levels; at least two
     * @see <a href="https://docs.typesafe.ai/primitives/score">Score primitive</a>
     */
    record Score(Object instructions, List<Object> criteria) implements Question {

        /** Validates and defensively copies the levels. */
        public Score {
            Objects.requireNonNull(criteria, "criteria");
            if (criteria.size() < 2) {
                throw new TypeSafeException("Score criteria must list at least two levels, got " + criteria.size()
                        + ".");
            }
            criteria = Collections.unmodifiableList(new ArrayList<>(criteria));
        }

        /** A score question over the given levels, lowest first. */
        public static Score of(Object instructions, String... levels) {
            Objects.requireNonNull(levels, "levels");
            return new Score(instructions, Arrays.asList((Object[]) levels));
        }

        /** A score question over the given levels, lowest first; a level may be text, an object, or an array. */
        public static Score of(Object instructions, List<?> levels) {
            Objects.requireNonNull(levels, "levels");
            return new Score(instructions, new ArrayList<>(levels));
        }

        @Override
        public String type() {
            return "score";
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Raw
    // ---------------------------------------------------------------------------------------------

    /**
     * A question sent to the API exactly as given, for API features this SDK version does not model yet.
     *
     * <p>The fields must include a non-blank string {@code type}. If the answer's type is one the SDK knows, it
     * is decoded normally; otherwise it arrives as an {@link Answer.Unknown}.
     *
     * @param fields the JSON fields of the question
     */
    record Raw(Map<String, Object> fields) implements Question {

        /** Validates the {@code type} field and defensively copies the fields. */
        public Raw {
            Objects.requireNonNull(fields, "fields");
            if (!(fields.get("type") instanceof String type) || type.isBlank()) {
                throw new TypeSafeException("A raw question must have a non-blank string \"type\" field.");
            }
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }

        /** Creates a raw question from its JSON fields. */
        public static Raw of(Map<String, ?> fields) {
            Objects.requireNonNull(fields, "fields");
            return new Raw(new LinkedHashMap<>(fields));
        }

        @Override
        public String type() {
            return (String) fields.get("type");
        }
    }
}
