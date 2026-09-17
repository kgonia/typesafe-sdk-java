package ai.typesafe.sdk.internal;

import ai.typesafe.sdk.errors.ResponseValidationException;
import ai.typesafe.sdk.models.ListModelsResponse;
import ai.typesafe.sdk.models.ModelMetadata;
import ai.typesafe.sdk.systemone.Answer;
import ai.typesafe.sdk.systemone.Question;
import ai.typesafe.sdk.systemone.SystemOneRequest;
import ai.typesafe.sdk.systemone.SystemOneResponse;
import ai.typesafe.sdk.systemone.Usage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;

/** Converts between SDK types and the JSON wire format. */
public final class Codec {

    private Codec() {
    }

    // ---------------------------------------------------------------------------------------------
    // Requests
    // ---------------------------------------------------------------------------------------------

    /** The JSON body for {@code POST /v1/systemone}. */
    public static Map<String, Object> systemOneBody(SystemOneRequest request, String defaultModel) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", request.state());
        body.put("model", request.model().orElse(defaultModel));
        Map<String, Object> questions = new LinkedHashMap<>();
        for (Map.Entry<String, Question> entry : request.questions().entrySet()) {
            questions.put(entry.getKey(), question(entry.getValue()));
        }
        body.put("questions", questions);
        body.putAll(request.extraBody());
        return body;
    }

    /** The JSON object for one question. Null instructions and criteria are omitted. */
    public static Map<String, Object> question(Question question) {
        Map<String, Object> out = new LinkedHashMap<>();
        switch (question) {
            case Question.Noul noul -> {
                out.put("type", "noul");
                putIfPresent(out, "instructions", noul.instructions());
                Question.Noul.Criteria criteria = noul.criteria();
                if (criteria != null && (criteria.yesMeans() != null || criteria.noMeans() != null)) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    putIfPresent(c, "true", criteria.yesMeans());
                    putIfPresent(c, "false", criteria.noMeans());
                    out.put("criteria", c);
                }
            }
            case Question.Choice choice -> {
                out.put("type", "choice");
                putIfPresent(out, "instructions", choice.instructions());
                out.put("criteria", choice.criteria());
            }
            case Question.Score score -> {
                out.put("type", "score");
                putIfPresent(out, "instructions", score.instructions());
                out.put("criteria", score.criteria());
            }
            case Question.Raw raw -> out.putAll(raw.fields());
        }
        return out;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Responses
    // ---------------------------------------------------------------------------------------------

    /** Decodes a successful {@code POST /v1/systemone} body. */
    public static SystemOneResponse systemOneResponse(Transport.RawResponse response, String endpoint,
            SdkLogger logger) {
        Reader reader = new Reader(response, endpoint);
        Map<?, ?> root = reader.rootObject();
        String model = reader.string(root, "model", "model");
        Map<?, ?> answersJson = reader.object(root, "answers", "answers");
        Map<String, Answer> answers = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : answersJson.entrySet()) {
            String name = String.valueOf(entry.getKey());
            String path = "answers." + name;
            Map<?, ?> answer = reader.asObject(entry.getValue(), path);
            answers.put(name, decodeAnswer(reader, answer, path, name, logger));
        }
        Usage usage = decodeUsage(reader, root.get("usage"));
        return new SystemOneResponse(model, answers, usage, response.metadata());
    }

    private static Answer decodeAnswer(Reader reader, Map<?, ?> answer, String path, String name, SdkLogger logger) {
        String type = reader.string(answer, "type", path + ".type");
        return switch (type) {
            case "noul" -> new Answer.Noul(reader.number(answer, "noul", path + ".noul"));
            case "choice" -> new Answer.Choice(
                    reader.string(answer, "choice", path + ".choice"),
                    reader.stringDoubleMap(answer, "probabilities", path + ".probabilities"),
                    reader.number(answer, "confidence", path + ".confidence"));
            case "score" -> new Answer.Score(
                    reader.number(answer, "score", path + ".score"),
                    reader.intKeyedMap(answer, "legend", path + ".legend"),
                    reader.intKeyedDoubleMap(answer, "probabilities", path + ".probabilities"),
                    reader.number(answer, "confidence", path + ".confidence"));
            default -> {
                logger.warn("Answer \"" + name + "\" has unrecognized type \"" + type
                        + "\"; returning it as an Answer.Unknown.");
                Map<String, Object> fields = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : answer.entrySet()) {
                    fields.put(String.valueOf(e.getKey()), e.getValue());
                }
                yield new Answer.Unknown(type, fields);
            }
        };
    }

    private static Usage decodeUsage(Reader reader, Object usage) {
        if (!(usage instanceof Map<?, ?> map)) {
            return new Usage(OptionalInt.empty(), OptionalInt.empty());
        }
        return new Usage(reader.optionalInt(map, "input_tokens", "usage.input_tokens"),
                reader.optionalInt(map, "output_tokens", "usage.output_tokens"));
    }

    /** Decodes a successful {@code GET /v1/models} body. */
    public static ListModelsResponse modelsResponse(Transport.RawResponse response, String endpoint) {
        Reader reader = new Reader(response, endpoint);
        Map<?, ?> root = reader.rootObject();
        List<?> modelsJson = reader.list(root, "models", "models");
        List<ModelMetadata> models = new ArrayList<>(modelsJson.size());
        for (int i = 0; i < modelsJson.size(); i++) {
            String path = "models[" + i + "]";
            Map<?, ?> m = reader.asObject(modelsJson.get(i), path);
            models.add(new ModelMetadata(reader.string(m, "name", path + ".name"),
                    reader.optionalString(m, "description", path + ".description"),
                    reader.optionalString(m, "release_date", path + ".release_date")));
        }
        return new ListModelsResponse(models, response.metadata());
    }

    /** Reads typed fields from parsed JSON, raising {@link ResponseValidationException} with a field path. */
    private static final class Reader {
        private final Transport.RawResponse response;
        private final String endpoint;
        private final Object body;

        Reader(Transport.RawResponse response, String endpoint) {
            this.response = response;
            this.endpoint = endpoint;
            this.body = response.parsedBody();
        }

        ResponseValidationException invalid(String path) {
            return new ResponseValidationException(response.statusCode(), body, response.headers(), endpoint, path);
        }

        Map<?, ?> rootObject() {
            if (body instanceof Map<?, ?> map) {
                return map;
            }
            throw invalid("body");
        }

        Map<?, ?> asObject(Object value, String path) {
            if (value instanceof Map<?, ?> map) {
                return map;
            }
            throw invalid(path);
        }

        Map<?, ?> object(Map<?, ?> parent, String key, String path) {
            return asObject(parent.get(key), path);
        }

        List<?> list(Map<?, ?> parent, String key, String path) {
            if (parent.get(key) instanceof List<?> list) {
                return list;
            }
            throw invalid(path);
        }

        String string(Map<?, ?> parent, String key, String path) {
            if (parent.get(key) instanceof String s) {
                return s;
            }
            throw invalid(path);
        }

        String optionalString(Map<?, ?> parent, String key, String path) {
            Object value = parent.get(key);
            if (value == null) {
                return null;
            }
            if (value instanceof String s) {
                return s;
            }
            throw invalid(path);
        }

        double number(Map<?, ?> parent, String key, String path) {
            if (parent.get(key) instanceof Number n) {
                return n.doubleValue();
            }
            throw invalid(path);
        }

        OptionalInt optionalInt(Map<?, ?> parent, String key, String path) {
            Object value = parent.get(key);
            if (value == null) {
                return OptionalInt.empty();
            }
            if (value instanceof Number n && n.longValue() == n.doubleValue() && n.longValue() <= Integer.MAX_VALUE
                    && n.longValue() >= Integer.MIN_VALUE) {
                return OptionalInt.of(n.intValue());
            }
            throw invalid(path);
        }

        Map<String, Double> stringDoubleMap(Map<?, ?> parent, String key, String path) {
            Map<?, ?> raw = object(parent, key, path);
            Map<String, Double> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                String name = String.valueOf(entry.getKey());
                if (!(entry.getValue() instanceof Number n)) {
                    throw invalid(path + "." + name);
                }
                out.put(name, n.doubleValue());
            }
            return out;
        }

        TreeMap<Integer, Object> intKeyedMap(Map<?, ?> parent, String key, String path) {
            Map<?, ?> raw = object(parent, key, path);
            TreeMap<Integer, Object> out = new TreeMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                out.put(intKey(entry.getKey(), path), entry.getValue());
            }
            return out;
        }

        TreeMap<Integer, Double> intKeyedDoubleMap(Map<?, ?> parent, String key, String path) {
            Map<?, ?> raw = object(parent, key, path);
            TreeMap<Integer, Double> out = new TreeMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                int level = intKey(entry.getKey(), path);
                if (!(entry.getValue() instanceof Number n)) {
                    throw invalid(path + "." + level);
                }
                out.put(level, n.doubleValue());
            }
            return out;
        }

        private int intKey(Object key, String path) {
            try {
                return Integer.parseInt(String.valueOf(key));
            } catch (NumberFormatException e) {
                throw invalid(path + "." + key);
            }
        }
    }
}
