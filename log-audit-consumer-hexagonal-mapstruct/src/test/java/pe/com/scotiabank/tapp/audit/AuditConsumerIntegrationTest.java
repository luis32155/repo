package pe.com.scotiabank.tapp.audit;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import pe.com.scotiabank.tapp.audit.adapter.common.http.AuditHttpHeaders;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogRequest;
import pe.com.scotiabank.tapp.audit.adapter.in.web.mapper.AuditWebMapper;
import pe.com.scotiabank.tapp.audit.adapter.out.http.AuditApiClient;
import pe.com.scotiabank.tapp.audit.adapter.out.http.config.AuditApiProperties;
import pe.com.scotiabank.tapp.audit.adapter.out.http.mapper.AuditApiMapper;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException.FailureKind;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditConsumerIntegrationTest {

    private static final String PATH = "/api/v1/audit/security/log";
    private static final String SUCCESS = "{\"data\":[{\"status\":\"OK\"}]}";
    private static final Map<String, String> HEADERS = Map.of(
            AuditHttpHeaders.API_VERSION, "apiversion",
            AuditHttpHeaders.CHANNEL_ID, "channel",
            AuditHttpHeaders.ORIGINATING_APPL_CODE, "originating",
            AuditHttpHeaders.COUNTRY_CODE, "country");
    private static final BlockingQueue<StubResponse> RESPONSES = new LinkedBlockingQueue<>();
    private static final AtomicReference<RecordedRequest> RECORDED = new AtomicReference<>();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final HttpServer UPSTREAM = startUpstream();
    private static final String FIXTURE = loadFixture();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private Validator validator;

    @Autowired
    private RestClient.Builder restClientBuilder;

    @Autowired
    private AuditWebMapper webMapper;

    @Autowired
    private AuditApiMapper apiMapper;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("audit.api.url", () -> "http://127.0.0.1:" + UPSTREAM.getAddress().getPort() + PATH);
        registry.add("audit.api.connect-timeout", () -> "1s");
        registry.add("audit.api.read-timeout", () -> "1s");
    }

    @BeforeEach
    void reset() {
        RESPONSES.clear();
        RECORDED.set(null);
        CALLS.set(0);
    }

    @AfterAll
    static void shutdown() {
        UPSTREAM.stop(0);
        EXECUTOR.shutdownNow();
    }

    @Test
    void forwardsFullPayloadAllTwentyDetailsHeadersAndOriginalTimezone() throws Exception {
        var response = send(FIXTURE, HEADERS);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body())).isEqualTo(mapper.readTree(SUCCESS));
        assertThat(CALLS.get()).isEqualTo(1);
        assertThat(RECORDED.get().method()).isEqualTo("POST");
        assertThat(RECORDED.get().path()).isEqualTo(PATH);
        HEADERS.forEach((name, value) -> assertThat(RECORDED.get().headers().getFirst(name)).isEqualTo(value));
        assertThat(RECORDED.get().headers().getFirst("Content-Type")).startsWith("application/json");
        var forwarded = mapper.readTree(RECORDED.get().body());
        assertThat(forwarded).isEqualTo(mapper.readTree(FIXTURE));
        assertThat(forwarded.get("event_message").get("channel_payload_detail").size()).isEqualTo(20);
        assertThat(forwarded.get("log_timestamp").stringValue()).isEqualTo("2026-07-09T15:19:59-05:00");
    }

    @Test
    void preservesSuccessfulUpstreamStatus() throws Exception {
        RESPONSES.add(new StubResponse(201, SUCCESS, "application/json", 0));
        assertThat(send(FIXTURE, HEADERS).statusCode()).isEqualTo(201);
    }

    @Test
    void preservesDynamicHeaderObjectsAndArrays() throws Exception {
        var json = (ObjectNode) mapper.readTree(FIXTURE);
        ((ObjectNode) json.get("event_message").get("channel_payload_header"))
                .set("extra", mapper.readTree("{\"count\":7,\"enabled\":true,\"items\":[1,2]}"));
        assertThat(send(mapper.writeValueAsString(json), HEADERS).statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(RECORDED.get().body())).isEqualTo(json);
    }

    @Test
    void rejectsMissingHeaderWithoutCallingUpstream() throws Exception {
        var headers = new java.util.HashMap<>(HEADERS);
        headers.remove(AuditHttpHeaders.CHANNEL_ID);
        assertThat(send(FIXTURE, headers).statusCode()).isEqualTo(400);
        assertThat(CALLS.get()).isZero();
    }

    @Test
    void rejectsBlankHeaderWithoutCallingUpstream() throws Exception {
        var headers = new java.util.HashMap<>(HEADERS);
        headers.put(AuditHttpHeaders.CHANNEL_ID, " ");
        assertThat(send(FIXTURE, headers).statusCode()).isEqualTo(400);
        assertThat(CALLS.get()).isZero();
    }

    @Test
    void validatesNestedRequiredFields() throws Exception {
        var json = (ObjectNode) mapper.readTree(FIXTURE);
        ((ObjectNode) json.get("event_message")).remove("app_code");
        var response = send(mapper.writeValueAsString(json), HEADERS);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("eventMessage.appCode");
        assertThat(CALLS.get()).isZero();
    }

    @Test
    void rejectsTimestampWithoutTimezone() throws Exception {
        var json = (ObjectNode) mapper.readTree(FIXTURE);
        json.put("event_timestamp", "2026-07-09T15:19:58");
        assertThat(send(mapper.writeValueAsString(json), HEADERS).statusCode()).isEqualTo(400);
        assertThat(CALLS.get()).isZero();
    }

    @Test
    void rejectsUnknownFieldsInsteadOfSilentlyDiscardingThem() throws Exception {
        var json = (ObjectNode) mapper.readTree(FIXTURE);
        json.put("unexpected_field", "must not disappear");
        assertThat(send(mapper.writeValueAsString(json), HEADERS).statusCode()).isEqualTo(400);
        assertThat(CALLS.get()).isZero();
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        var response = send("{\"account_name\":", HEADERS);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(CALLS.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 422, 429, 499})
    void preservesClientErrorsWithoutExposingUpstreamBody(int upstreamStatus) throws Exception {
        RESPONSES.add(new StubResponse(upstreamStatus, "{\"secret\":\"private-upstream-data\"}", "application/json", 0));
        var response = send(FIXTURE, HEADERS);
        assertThat(response.statusCode()).isEqualTo(upstreamStatus);
        assertThat(mapper.readTree(response.body()).get("upstream_status").intValue()).isEqualTo(upstreamStatus);
        assertThat(response.body()).doesNotContain("private-upstream-data");
        assertThat(CALLS.get()).isEqualTo(1);
    }

    @Test
    void mapsUpstreamServerErrorsToBadGatewayWithoutRetrying() throws Exception {
        RESPONSES.add(new StubResponse(503, "{\"message\":\"down\"}", "application/json", 0));
        var response = send(FIXTURE, HEADERS);
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(mapper.readTree(response.body()).get("upstream_status").intValue()).isEqualTo(503);
        assertThat(CALLS.get()).isEqualTo(1);
    }

    @Test
    void mapsReadTimeoutToGatewayTimeoutWithoutRetrying() throws Exception {
        RESPONSES.add(new StubResponse(200, SUCCESS, "application/json", 1600));
        assertThat(send(FIXTURE, HEADERS).statusCode()).isEqualTo(504);
        assertThat(CALLS.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"data\":[]}", "{\"data\":[null]}", "{\"data\":[{\"status\":\"\"}]}", "not-json"})
    void mapsInvalidSuccessfulResponsesToBadGateway(String body) throws Exception {
        RESPONSES.add(new StubResponse(200, body, "application/json", 0));
        assertThat(send(FIXTURE, HEADERS).statusCode()).isEqualTo(502);
    }

    @Test
    void mapsConnectionFailureToServiceUnavailable() throws Exception {
        int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var properties = new AuditApiProperties(URI.create("http://127.0.0.1:" + closedPort + PATH),
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout()).build());
        factory.setReadTimeout(properties.readTimeout());
        var client = new AuditApiClient(restClientBuilder.clone().requestFactory(factory).build(),
                properties, validator, apiMapper);

        assertThatThrownBy(() -> client.send(webMapper.toDomain(mapper.readValue(FIXTURE, AuditLogRequest.class)),
                new AuditContext("apiversion", "channel", "originating", "country")))
                .isInstanceOfSatisfying(AuditDeliveryException.class,
                        exception -> assertThat(exception.kind()).isEqualTo(FailureKind.UNAVAILABLE));
    }

    private HttpResponse<String> send(String body, Map<String, String> headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + PATH))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(request::header);
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpServer startUpstream() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(EXECUTOR);
            server.createContext(PATH, AuditConsumerIntegrationTest::handleUpstream);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void handleUpstream(HttpExchange exchange) throws IOException {
        CALLS.incrementAndGet();
        var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        RECORDED.set(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders(), body));
        var plan = RESPONSES.poll();
        if (plan == null) {
            plan = new StubResponse(200, SUCCESS, "application/json", 0);
        }
        try {
            if (plan.delayMillis() > 0) {
                Thread.sleep(plan.delayMillis());
            }
            var bytes = plan.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", plan.contentType());
            exchange.sendResponseHeaders(plan.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            // The caller closes its connection when the timeout test expires.
        } finally {
            exchange.close();
        }
    }

    private static String loadFixture() {
        try (var input = AuditConsumerIntegrationTest.class.getResourceAsStream("/audit-request.json")) {
            if (input == null) {
                throw new IllegalStateException("Missing audit-request.json fixture");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record StubResponse(int status, String body, String contentType, long delayMillis) {
    }

    private record RecordedRequest(String method, String path, com.sun.net.httpserver.Headers headers, String body) {
    }
}
