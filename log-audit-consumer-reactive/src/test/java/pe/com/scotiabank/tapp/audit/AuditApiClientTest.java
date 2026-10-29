package pe.com.scotiabank.tapp.audit;

import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import pe.com.scotiabank.tapp.audit.adapter.common.http.AuditHttpHeaders;
import pe.com.scotiabank.tapp.audit.adapter.out.http.AuditApiClient;
import pe.com.scotiabank.tapp.audit.adapter.out.http.config.AuditApiProperties;
import pe.com.scotiabank.tapp.audit.adapter.out.http.mapper.AuditApiMapper;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException.FailureKind;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class AuditApiClientTest {

    private static final URI URL = URI.create("https://audit.invalid/api/v1/audit/security/log");
    private static final String SUCCESS = "{\"data\":[{\"status\":\"OK\"}]}";
    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(3);
    private static final AuditContext CONTEXT = new AuditContext("v1", "channel", "TAPP", "PE");
    private static final AuditEvent EVENT = event();
    private static ValidatorFactory validatorFactory;

    @BeforeAll
    static void initializeValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void defersEveryExchangeUntilSubscriptionAndCreatesAColdPublisher() {
        var calls = new AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            return Mono.just(response(200, SUCCESS));
        });

        var pending = client.send(EVENT, CONTEXT);

        assertThat(calls).hasValue(0);
        StepVerifier.create(pending)
                .assertNext(submission -> assertThat(submission.providerStatusCode()).isEqualTo(200))
                .expectComplete().verify(VERIFY_TIMEOUT);
        assertThat(calls).hasValue(1);
        StepVerifier.create(pending)
                .assertNext(submission -> assertThat(submission.providerStatusCode()).isEqualTo(200))
                .expectComplete().verify(VERIFY_TIMEOUT);
        assertThat(calls).hasValue(2);
    }

    @Test
    void sendsPostToConfiguredUrlWithAllFourHeadersAndJsonMediaTypes() {
        var client = client(request -> {
            assertThat(request.method()).isEqualTo(HttpMethod.POST);
            assertThat(request.url()).isEqualTo(URL);
            assertThat(request.headers().getFirst(AuditHttpHeaders.API_VERSION)).isEqualTo("v1");
            assertThat(request.headers().getFirst(AuditHttpHeaders.CHANNEL_ID)).isEqualTo("channel");
            assertThat(request.headers().getFirst(AuditHttpHeaders.ORIGINATING_APPL_CODE)).isEqualTo("TAPP");
            assertThat(request.headers().getFirst(AuditHttpHeaders.COUNTRY_CODE)).isEqualTo("PE");
            assertThat(request.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(request.headers().getAccept()).containsExactly(MediaType.APPLICATION_JSON);
            return Mono.just(response(200, SUCCESS));
        });

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .assertNext(submission -> assertThat(submission.result().data().getFirst().status()).isEqualTo("OK"))
                .expectComplete().verify(VERIFY_TIMEOUT);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201})
    void mapsSuccessfulResponseAndPreservesProviderStatus(int status) {
        var client = client(request -> Mono.just(response(status, SUCCESS)));

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .assertNext(submission -> {
                    assertThat(submission.providerStatusCode()).isEqualTo(status);
                    assertThat(submission.result().data()).hasSize(1);
                    assertThat(submission.result().data().getFirst().status()).isEqualTo("OK");
                })
                .expectComplete().verify(VERIFY_TIMEOUT);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 503})
    void preservesHttpRejectionCauseAndBodyForLocalDiagnosisWithoutRetrying(int status) {
        String providerBody = "{\"detail\":\"private-provider-data\"}";
        var calls = new AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            return Mono.just(response(status, providerBody));
        });

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(AuditDeliveryException.class, failure -> {
                            assertThat(failure.kind()).isEqualTo(status < 500
                                    ? FailureKind.REJECTED : FailureKind.PROVIDER_FAILURE);
                            assertThat(failure.providerStatusCode()).isEqualTo(status);
                            assertThat(failure.getMessage()).doesNotContain("private-provider-data");
                            assertThat(failure.getCause()).isInstanceOfSatisfying(
                                    WebClientResponseException.class, cause -> {
                                        assertThat(cause.getStatusCode().value()).isEqualTo(status);
                                        assertThat(cause.getResponseBodyAsString()).isEqualTo(providerBody);
                                    });
                        }))
                .verify(VERIFY_TIMEOUT);
        assertThat(calls).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"data\":[]}", "{\"data\":[null]}",
            "{\"data\":[{\"status\":\" \"}]}", "not-json", "null"})
    void rejectsMalformedOrContractInvalidSuccessfulResponses(String body) {
        var client = client(request -> Mono.just(response(200, body)));

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .expectErrorSatisfies(error -> assertFailure(error, FailureKind.INVALID_RESPONSE, 200))
                .verify(VERIFY_TIMEOUT);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204})
    void rejectsEmptySuccessfulResponse(int status) {
        var client = client(request -> Mono.just(response(status, "")));

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .expectErrorSatisfies(error -> assertFailure(error, FailureKind.INVALID_RESPONSE, status))
                .verify(VERIFY_TIMEOUT);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void rejectsRedirectResponseAndDoesNotPerformAnotherExchange(int status) {
        var calls = new AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(status))
                    .header(HttpHeaders.LOCATION, "https://redirect.invalid/other")
                    .body("").build());
        });

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .expectErrorSatisfies(error -> assertFailure(error, FailureKind.INVALID_RESPONSE, status))
                .verify(VERIFY_TIMEOUT);
        assertThat(calls).hasValue(1);
    }

    @Test
    void mapsConnectionFailureToUnavailableAndRetainsCause() {
        var cause = new WebClientRequestException(new ConnectException("connection refused"),
                HttpMethod.POST, URL, new HttpHeaders());
        var calls = new AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            return Mono.error(cause);
        });

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .expectErrorSatisfies(error -> {
                    assertFailure(error, FailureKind.UNAVAILABLE, null);
                    assertThat(error).hasCauseReference(cause);
                }).verify(VERIFY_TIMEOUT);
        assertThat(calls).hasValue(1);
    }

    @Test
    void mapsRawTimeoutToTimeoutAndRetainsCause() {
        var cause = new TimeoutException("exchange deadline");
        var client = client(request -> Mono.error(cause));

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .expectErrorSatisfies(error -> {
                    assertFailure(error, FailureKind.TIMEOUT, null);
                    assertThat(error).hasCauseReference(cause);
                }).verify(VERIFY_TIMEOUT);
    }

    @Test
    void mapsWrappedReadTimeoutToTimeoutInsteadOfUnavailable() {
        var cause = new WebClientRequestException(ReadTimeoutException.INSTANCE,
                HttpMethod.POST, URL, new HttpHeaders());
        var client = client(request -> Mono.error(cause));

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .expectErrorSatisfies(error -> {
                    assertFailure(error, FailureKind.TIMEOUT, null);
                    assertThat(error).hasCauseReference(cause);
                }).verify(VERIFY_TIMEOUT);
    }

    @Test
    void mapsWrappedConnectTimeoutToTimeoutInsteadOfUnavailable() {
        var cause = new WebClientRequestException(new ConnectTimeoutException("connect deadline"),
                HttpMethod.POST, URL, new HttpHeaders());
        var client = client(request -> Mono.error(cause));

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .expectErrorSatisfies(error -> {
                    assertFailure(error, FailureKind.TIMEOUT, null);
                    assertThat(error).hasCauseReference(cause);
                }).verify(VERIFY_TIMEOUT);
    }

    @Test
    void mapsTimeoutWhileReadingSuccessfulBodyToTimeout() {
        var client = client(request -> Mono.just(ClientResponse.create(HttpStatusCode.valueOf(200))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Flux.error(ReadTimeoutException.INSTANCE)).build()));

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .expectErrorSatisfies(error -> assertFailure(error, FailureKind.TIMEOUT, null))
                .verify(VERIFY_TIMEOUT);
    }

    @Test
    void mapsTimeoutWhileReadingRejectedBodyToTimeout() {
        var client = client(request -> Mono.just(ClientResponse.create(HttpStatusCode.valueOf(400))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Flux.error(ReadTimeoutException.INSTANCE)).build()));

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .expectErrorSatisfies(error -> assertFailure(error, FailureKind.TIMEOUT, null))
                .verify(VERIFY_TIMEOUT);
    }

    @Test
    void discardsUnreadableRejectionBodyAndRetainsStatusAndBufferLimitCause() {
        var readFailure = new DataBufferLimitException("response limit");
        var client = client(request -> Mono.just(ClientResponse.create(HttpStatusCode.valueOf(400))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(Flux.error(readFailure)).build()));

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .expectErrorSatisfies(error -> {
                    assertFailure(error, FailureKind.REJECTED, 400);
                    assertThat(error.getCause()).isInstanceOfSatisfying(WebClientResponseException.class, cause -> {
                        assertThat(cause.getResponseBodyAsByteArray()).isEmpty();
                        assertThat(cause).hasCauseReference(readFailure);
                    });
                }).verify(VERIFY_TIMEOUT);
    }

    @Test
    void propagatesCancellationToPendingOutboundExchange() {
        var cancelled = new AtomicBoolean();
        var calls = new AtomicInteger();
        var client = client(request -> {
            calls.incrementAndGet();
            return Mono.<ClientResponse>never().doOnCancel(() -> cancelled.set(true));
        });

        StepVerifier.create(client.send(EVENT, CONTEXT))
                .thenAwait(Duration.ofMillis(10)).thenCancel().verify(VERIFY_TIMEOUT);

        assertThat(calls).hasValue(1);
        assertThat(cancelled).isTrue();
    }

    private static AuditApiClient client(ExchangeFunction exchange) {
        var properties = new AuditApiProperties(URL, Duration.ofSeconds(1), Duration.ofSeconds(2));
        return new AuditApiClient(WebClient.builder().exchangeFunction(exchange).build(), properties,
                validatorFactory.getValidator(), Mappers.getMapper(AuditApiMapper.class));
    }

    private static ClientResponse response(int status, String body) {
        return ClientResponse.create(HttpStatusCode.valueOf(status))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body).build();
    }

    private static void assertFailure(Throwable error, FailureKind kind, Integer upstreamStatus) {
        assertThat(error).isInstanceOfSatisfying(AuditDeliveryException.class, failure -> {
            assertThat(failure.kind()).isEqualTo(kind);
            assertThat(failure.providerStatusCode()).isEqualTo(upstreamStatus);
        });
    }

    private static AuditEvent event() {
        var timestamp = OffsetDateTime.parse("2026-07-09T15:19:58-05:00");
        var message = new AuditEvent.EventMessage("TAPP", "TAPP App", "LOGIN", "Login",
                "DNI", "12345678", "device", "operation", "cams", "account",
                Map.of("header", "Login"), List.of(new AuditEvent.PayloadDetail("result", "SUCCESS")));
        return new AuditEvent(timestamp, "AUDIT_LOG", timestamp, "INFORMATION", "MEDIUM", "SUCCESS",
                "test-account", "source", "127.0.0.1", "destination", "127.0.0.2", "session", "EXECUTE", message);
    }
}
