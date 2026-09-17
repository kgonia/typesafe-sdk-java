package ai.typesafe.sdk.systemone;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The answer to one question, matching the question's type. Switch over the sealed hierarchy to handle each
 * kind:
 *
 * <pre>{@code
 * String summary = switch (answer) {
 *     case Answer.Noul n -> "yes with probability " + n.noul();
 *     case Answer.Choice c -> "chose " + c.choice();
 *     case Answer.Score s -> "scored " + s.score();
 *     case Answer.Unknown u -> "unknown answer type " + u.type();
 * };
 * }</pre>
 *
 * <p>{@link Unknown} keeps the SDK working when the API adds answer kinds this version does not know.
 *
 * @see <a href="https://docs.typesafe.ai/confidence">Confidence guide</a>
 */
public sealed interface Answer permits Answer.Noul, Answer.Choice, Answer.Score, Answer.Unknown {

    /** The wire {@code type} of this answer. */
    String type();

    /**
     * The answer to a {@link Question.Noul}.
     *
     * <p>There is no separate confidence: the probability is the answer. A value near 0.5 means the model finds
     * yes and no similarly likely, not that the answer is "medium".
     *
     * @param noul the probability that the answer is yes, from 0 (no) to 1 (yes)
     */
    record Noul(double noul) implements Answer {

        @Override
        public String type() {
            return "noul";
        }

        /** Whether the probability of yes is at least {@code threshold}. Choose thresholds against your own data. */
        public boolean isYes(double threshold) {
            return noul >= threshold;
        }
    }

    /**
     * The answer to a {@link Question.Choice}.
     *
     * @param choice the option with the highest probability
     * @param probabilities every option mapped to its probability; the values sum to 1
     * @param confidence how concentrated the distribution is, from 0 to 1
     */
    record Choice(String choice, Map<String, Double> probabilities, double confidence) implements Answer {

        /** Defensively copies the probabilities, preserving their order. */
        public Choice {
            Objects.requireNonNull(choice, "choice");
            Objects.requireNonNull(probabilities, "probabilities");
            probabilities = Collections.unmodifiableMap(new LinkedHashMap<>(probabilities));
        }

        @Override
        public String type() {
            return "choice";
        }

        /** The probability of {@code option}, or 0 when the option is not in the distribution. */
        public double probabilityOf(String option) {
            Double p = probabilities.get(option);
            return p == null ? 0.0 : p;
        }
    }

    /**
     * The answer to a {@link Question.Score}.
     *
     * @param score the probability-weighted position across the levels, from 0 to {@code levels - 1}; may fall
     *     between levels
     * @param legend each level index mapped back to its description
     * @param probabilities each level index mapped to its probability; the values sum to 1
     * @param confidence how concentrated the distribution is, from 0 to 1
     */
    record Score(double score, SortedMap<Integer, Object> legend, SortedMap<Integer, Double> probabilities,
            double confidence) implements Answer {

        /** Defensively copies the maps. */
        public Score {
            Objects.requireNonNull(legend, "legend");
            Objects.requireNonNull(probabilities, "probabilities");
            legend = Collections.unmodifiableSortedMap(new TreeMap<>(legend));
            probabilities = Collections.unmodifiableSortedMap(new TreeMap<>(probabilities));
        }

        @Override
        public String type() {
            return "score";
        }

        /** The level index nearest to {@link #score()}. */
        public int nearestLevel() {
            return (int) Math.round(score);
        }

        /** The description of the level nearest to {@link #score()}, or {@code null} if undescribed. */
        public Object nearestLevelDescription() {
            return legend.get(nearestLevel());
        }

        /** The probability of level {@code level}, or 0 when the level is not in the distribution. */
        public double probabilityOf(int level) {
            Double p = probabilities.get(level);
            return p == null ? 0.0 : p;
        }
    }

    /**
     * An answer whose {@code type} this SDK version does not recognize. The complete JSON object is kept in
     * {@link #fields()} so newer API features remain reachable.
     *
     * @param type the wire type
     * @param fields every field of the answer object, including {@code type}
     */
    record Unknown(String type, Map<String, Object> fields) implements Answer {

        /** Defensively copies the fields. */
        public Unknown {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(fields, "fields");
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    }
}
