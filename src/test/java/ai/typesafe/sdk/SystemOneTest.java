package ai.typesafe.sdk;

import ai.typesafe.sdk.errors.ResponseValidationException;
import ai.typesafe.sdk.errors.TypeSafeException;
import ai.typesafe.sdk.http.RequestOptions;
import ai.typesafe.sdk.internal.json.Json;
import ai.typesafe.sdk.systemone.Answer;
import ai.typesafe.sdk.systemone.Question;
import ai.typesafe.sdk.systemone.SystemOneRequest;
import ai.typesafe.sdk.systemone.SystemOneResponse;
import ai.typesafe.sdk.systemone.Usage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemOneTest {

    private static final String FULL_RESPONSE = """
            {
              "model": "jev-1.13.0",
              "answers": {
                "billing": {"type": "noul", "noul": 0.92},
                "tone": {"type": "choice", "choice": "frustrated",
                         "probabilities": {"calm": 0.1, "frustrated": 0.7, "angry": 0.2}, "confidence": 0.65},
                "urgency": {"type": "score", "score": 1.6,
                            "legend": {"2": "today", "0": "can wait", "1": "this week"},
                            "probabilities": {"0": 0.05, "1": 0.3, "2": 0.65}, "confidence": 0.78, "extra": true},
                "future": {"type": "ranking", "order": ["a", "b"]}
              },
              "usage": {"input_tokens": 312, "output_tokens": 48},
              "new_field": 1
            }
            """;

    record Ticket(String subject, String body) {
    }

    private MockServer server;
    private TypeSafeClient client;

    @BeforeEach
    void start() {
        server = MockServer.start();
        client = server.clientBuilder().build();
    }

    @AfterEach
    void stop() {
        client.close();
        server.close();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bodyOf(MockServer.Recorded request) {
        return (Map<String, Object>) Json.parse(request.body());
    }

    @Test
    void sendsAuthAndIdentifyingHeaders() {
        client.systemOne("text", Map.of("q", Question.noul("Q?")));
        MockServer.Recorded request = server.lastRequest();
        assertEquals("POST", request.method());
        assertEquals("/v1/systemone", request.path());
        assertEquals("Bearer test-key-1234567890", request.header("Authorization"));
        assertEquals("application/json", request.header("Accept"));
        assertEquals("application/json", request.header("Content-Type"));
        assertEquals(SdkVersion.USER_AGENT, request.header("User-Agent"));
        assertEquals(SdkVersion.USER_AGENT, request.header("X-TypeSafe-SDK"));
        assertTrue(request.header("X-TypeSafe-Runtime").matches("java/\\d+\\.\\d+\\.\\d+ \\([^;]+; [^)]+\\)"),
                request.header("X-TypeSafe-Runtime"));
        assertNull(request.header("X-TypeSafe-Retry-Count"));
    }

    @Test
    void encodesEveryQuestionType() {
        Map<String, Question> questions = new LinkedHashMap<>();
        questions.put("n1", Question.noul("Yes?"));
        questions.put("n2", Question.noul(Map.of("q", "Yes?"), "means yes", null));
        questions.put("n3", new Question.Noul(null, new Question.Noul.Criteria(null, null)));
        questions.put("c1", Question.choice("Which?", "a", "b"));
        questions.put("c2", Question.Choice.builder("Which?").option("a").option("b", "described").build());
        questions.put("s1", Question.score("How?", "low", "high"));
        questions.put("s2", Question.score(null, List.of("low", Map.of("level", "mid"), "high")));
        questions.put("r1", Question.raw(Map.of("type", "noul", "instructions", "Raw?", "weight", 2)));
        client.systemOne(new Ticket("Hi", "Body"), questions);

        Map<String, Object> body = bodyOf(server.lastRequest());
        assertEquals(List.of("state", "model", "questions"), List.copyOf(body.keySet()));
        assertEquals(Map.of("subject", "Hi", "body", "Body"), body.get("state"));
        assertEquals("jev-latest", body.get("model"));
        @SuppressWarnings("unchecked")
        Map<String, Object> q = (Map<String, Object>) body.get("questions");
        assertEquals(List.copyOf(questions.keySet()), List.copyOf(q.keySet()));
        assertEquals(Map.of("type", "noul", "instructions", "Yes?"), q.get("n1"));
        assertEquals(Map.of("type", "noul", "instructions", Map.of("q", "Yes?"), "criteria", Map.of("true", "means yes")),
                q.get("n2"));
        assertEquals(Map.of("type", "noul"), q.get("n3"));
        Map<String, Object> c1 = new LinkedHashMap<>();
        c1.put("a", null);
        c1.put("b", null);
        assertEquals(Map.of("type", "choice", "instructions", "Which?", "criteria", c1), q.get("c1"));
        Map<String, Object> c2 = new LinkedHashMap<>();
        c2.put("a", null);
        c2.put("b", "described");
        assertEquals(Map.of("type", "choice", "instructions", "Which?", "criteria", c2), q.get("c2"));
        assertEquals(Map.of("type", "score", "instructions", "How?", "criteria", List.of("low", "high")), q.get("s1"));
        assertEquals(Map.of("type", "score", "criteria", List.of("low", Map.of("level", "mid"), "high")), q.get("s2"));
        assertEquals(Map.of("type", "noul", "instructions", "Raw?", "weight", 2L), q.get("r1"));
    }

    @Test
    void appliesModelOverrideAndExtraBody() {
        SystemOneRequest request = SystemOneRequest.builder().state("s").question("q", Question.noul("Q?"))
                .model("jev-preview").extraBodyField("beam_width", 4).extraBodyField("model", "override-wins").build();
        client.systemOne(request);
        Map<String, Object> body = bodyOf(server.lastRequest());
        assertEquals("override-wins", body.get("model"));
        assertEquals(4L, body.get("beam_width"));

        client.systemOne(SystemOneRequest.builder().state("s").question("q", Question.noul("Q?")).model("jev-1.13.0").build());
        assertEquals("jev-1.13.0", bodyOf(server.lastRequest()).get("model"));
    }

    @Test
    void usesClientDefaultModel() {
        try (TypeSafeClient custom = server.clientBuilder().defaultModel("jev-custom").build()) {
            custom.systemOne("s", Map.of("q", Question.noul("Q?")));
        }
        assertEquals("jev-custom", bodyOf(server.lastRequest()).get("model"));
    }

    @Test
    void mergesDefaultAndPerCallHeadersWithoutClobberingProtectedOnes() {
        try (TypeSafeClient custom = server.clientBuilder().defaultHeader("X-Trace", "default")
                .defaultHeader("X-Only-Default", "1").build()) {
            RequestOptions options = RequestOptions.builder().header("x-trace", "call").header("Authorization", "Bearer evil")
                    .header("X-TypeSafe-Retry-Count", "9").header("content-type", "text/plain").build();
            custom.systemOne(SystemOneRequest.of("s", Map.of("q", Question.noul("Q?"))), options);
        }
        MockServer.Recorded request = server.lastRequest();
        assertEquals("call", request.header("X-Trace"));
        assertEquals("1", request.header("X-Only-Default"));
        assertEquals("Bearer test-key-1234567890", request.header("Authorization"));
        assertEquals("application/json", request.header("Content-Type"));
        assertNull(request.header("X-TypeSafe-Retry-Count"));
    }

    @Test
    void decodesEveryAnswerType() {
        server.enqueue(MockServer.Reply.json(FULL_RESPONSE).header("x-typesafe-request-id", "req-123"));
        SystemOneResponse response = client.systemOne("s", Map.of("q", Question.noul("Q?")));

        assertEquals("jev-1.13.0", response.model());
        assertEquals(List.of("billing", "tone", "urgency", "future"), List.copyOf(response.answers().keySet()));
        assertEquals(Optional.of("req-123"), response.requestId());
        assertEquals(200, response.metadata().statusCode());
        assertTrue(response.metadata().rawBody().contains("\"new_field\""));

        Answer.Noul billing = response.noul("billing");
        assertEquals(0.92, billing.noul());
        assertTrue(billing.isYes(0.9));
        assertFalse(billing.isYes(0.95));
        assertEquals("noul", billing.type());

        Answer.Choice tone = response.choice("tone");
        assertEquals("frustrated", tone.choice());
        assertEquals(0.65, tone.confidence());
        assertEquals(List.of("calm", "frustrated", "angry"), List.copyOf(tone.probabilities().keySet()));
        assertEquals(0.7, tone.probabilityOf("frustrated"));
        assertEquals(0.0, tone.probabilityOf("missing"));

        Answer.Score urgency = response.score("urgency");
        assertEquals(1.6, urgency.score());
        assertEquals(0.78, urgency.confidence());
        assertEquals(List.of(0, 1, 2), List.copyOf(urgency.legend().keySet()));
        assertEquals("can wait", urgency.legend().get(0));
        assertEquals(0.65, urgency.probabilityOf(2));
        assertEquals(2, urgency.nearestLevel());
        assertEquals("today", urgency.nearestLevelDescription());

        Answer.Unknown future = assertInstanceOf(Answer.Unknown.class, response.answer("future"));
        assertEquals("ranking", future.type());
        assertEquals(List.of("a", "b"), future.fields().get("order"));
        assertEquals(Map.of("future", future), response.unknownAnswers());

        assertEquals(Map.of("billing", billing), response.nouls());
        assertEquals(Map.of("tone", tone), response.choices());
        assertEquals(Map.of("urgency", urgency), response.scores());
        assertEquals(Usage.of(312, 48), response.usage());
    }

    @Test
    void switchesOverTheSealedAnswerHierarchy() {
        server.enqueue(MockServer.Reply.json(FULL_RESPONSE));
        SystemOneResponse response = client.systemOne("s", Map.of("q", Question.noul("Q?")));
        List<String> kinds = response.answers().values().stream().map(answer -> switch (answer) {
            case Answer.Noul n -> "noul";
            case Answer.Choice c -> "choice";
            case Answer.Score s -> "score";
            case Answer.Unknown u -> "unknown";
        }).toList();
        assertEquals(List.of("noul", "choice", "score", "unknown"), kinds);
    }

    @Test
    void typedAccessorsExplainMismatches() {
        server.enqueue(MockServer.Reply.json(FULL_RESPONSE));
        SystemOneResponse response = client.systemOne("s", Map.of("q", Question.noul("Q?")));
        NoSuchElementException missing = assertThrows(NoSuchElementException.class, () -> response.noul("nope"));
        assertTrue(missing.getMessage().contains("billing"), missing.getMessage());
        NoSuchElementException wrong = assertThrows(NoSuchElementException.class, () -> response.choice("billing"));
        assertTrue(wrong.getMessage().contains("noul answer, not a choice"), wrong.getMessage());
    }

    @Test
    void toleratesMissingUsage() {
        server.enqueue(MockServer.Reply.json("{\"model\":\"m\",\"answers\":{\"q\":{\"type\":\"noul\",\"noul\":1}}}"));
        SystemOneResponse response = client.systemOne("s", Map.of("q", Question.noul("Q?")));
        assertEquals(new Usage(OptionalInt.empty(), OptionalInt.empty()), response.usage());
        assertEquals(1.0, response.noul("q").noul());
    }

    @Test
    void rejectsInvalidQuestionsBeforeSending() {
        assertThrows(TypeSafeException.class, () -> client.systemOne("s", Map.of()));
        assertThrows(TypeSafeException.class, () -> Question.score("How?", "only one"));
        assertThrows(TypeSafeException.class, () -> Question.choice("Which?"));
        assertThrows(TypeSafeException.class, () -> Question.raw(Map.of("instructions", "no type")));
        assertThrows(TypeSafeException.class, () -> SystemOneRequest.builder().state("s").question(" ", Question.noul("Q?")).build());
        assertThrows(NullPointerException.class, () -> SystemOneRequest.of(null, Map.of("q", Question.noul("Q?"))));
        assertThrows(TypeSafeException.class, () -> client.systemOne(new Object(), Map.of("q", Question.noul("Q?"))));
        assertTrue(server.requests().isEmpty());
    }

    @Test
    void raisesResponseValidationErrorsWithFieldPaths() {
        server.enqueue(MockServer.Reply.json("{\"model\":\"m\",\"answers\":{\"tone\":{\"type\":\"choice\",\"choice\":\"a\",\"probabilities\":{\"a\":1}}}}"));
        ResponseValidationException e = assertThrows(ResponseValidationException.class,
                () -> client.systemOne("s", Map.of("q", Question.noul("Q?"))));
        assertEquals("answers.tone.confidence", e.fieldPath());
        assertEquals(200, e.statusCode());
        assertTrue(e.getMessage().contains("POST /v1/systemone: 200 Invalid response data at 'answers.tone.confidence'."),
                e.getMessage());

        server.enqueue(MockServer.Reply.text(200, "<html>oops</html>"));
        assertEquals("body", assertThrows(ResponseValidationException.class,
                () -> client.systemOne("s", Map.of("q", Question.noul("Q?")))).fieldPath());

        server.enqueue(MockServer.Reply.json("{\"model\":\"m\",\"answers\":{\"s\":{\"type\":\"score\",\"score\":1,\"confidence\":1,\"legend\":{\"x\":\"a\"},\"probabilities\":{}}}}"));
        assertEquals("answers.s.legend.x", assertThrows(ResponseValidationException.class,
                () -> client.systemOne("s", Map.of("q", Question.noul("Q?")))).fieldPath());
    }

    @Test
    void questionRecordsAreImmutableAndValidated() {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("a", null);
        Question.Choice choice = Question.Choice.of("Which?", criteria);
        criteria.put("b", null);
        assertEquals(1, choice.criteria().size());
        assertThrows(UnsupportedOperationException.class, () -> choice.criteria().put("c", null));
        assertThrows(TypeSafeException.class, () -> Question.Choice.of("Which?", Map.of(" ", "blank name")));
        assertEquals(List.of("a", "b"), Question.Score.of("How?", "a", "b").criteria());
        assertEquals("noul", Question.raw(Map.of("type", "noul")).type());
    }
}
