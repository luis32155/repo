package pe.com.scotiabank.tapp.audit;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import pe.com.scotiabank.tapp.audit.application.model.AuditSubmission;
import pe.com.scotiabank.tapp.audit.application.service.SubmitAuditService;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import pe.com.scotiabank.tapp.audit.domain.model.AuditResult;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubmitAuditServiceTest {

    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(1);
    private static final AuditEvent EVENT = event();
    private static final AuditContext CONTEXT = new AuditContext("1", "mobile", "TAPP", "PE");
    private static final AuditSubmission SUBMISSION =
            new AuditSubmission(new AuditResult(List.of(new AuditResult.AuditStatus("OK"))), 201);

    @Test
    void doesNotCallOutputPortBeforeSubscription() {
        var calls = new AtomicInteger();
        var service = new SubmitAuditService((event, context) -> {
            calls.incrementAndGet();
            return Mono.just(SUBMISSION);
        });

        Mono<AuditSubmission> pending = service.submit(EVENT, CONTEXT);

        assertThat(pending).isNotNull();
        assertThat(calls.get()).isZero();
    }

    @Test
    void passesSameEventAndContextToPortAndReturnsExactSubmission() {
        var sentEvent = new AtomicReference<AuditEvent>();
        var sentContext = new AtomicReference<AuditContext>();
        var calls = new AtomicInteger();
        var service = new SubmitAuditService((event, context) -> {
            sentEvent.set(event);
            sentContext.set(context);
            calls.incrementAndGet();
            return Mono.just(SUBMISSION);
        });

        StepVerifier.create(service.submit(EVENT, CONTEXT))
                .assertNext(result -> assertThat(result).isSameAs(SUBMISSION))
                .expectComplete()
                .verify(VERIFY_TIMEOUT);

        assertThat(sentEvent.get()).isSameAs(EVENT);
        assertThat(sentContext.get()).isSameAs(CONTEXT);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void propagatesReactivePortErrorWithoutSwallowingOrRetrying() {
        var failure = new IllegalStateException("outbound-failed");
        var calls = new AtomicInteger();
        var service = new SubmitAuditService((event, context) -> {
            calls.incrementAndGet();
            return Mono.error(failure);
        });

        StepVerifier.create(service.submit(EVENT, CONTEXT))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify(VERIFY_TIMEOUT);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void convertsSynchronousPortFailureIntoReactiveErrorWithoutRetrying() {
        var failure = new IllegalStateException("port-failed-before-returning");
        var calls = new AtomicInteger();
        var service = new SubmitAuditService((event, context) -> {
            calls.incrementAndGet();
            throw failure;
        });

        Mono<AuditSubmission> pending = service.submit(EVENT, CONTEXT);
        assertThat(calls.get()).isZero();
        StepVerifier.create(pending)
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify(VERIFY_TIMEOUT);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rejectsNullEventOnSubscriptionWithoutCallingPort() {
        var calls = new AtomicInteger();
        var service = new SubmitAuditService((event, context) -> {
            calls.incrementAndGet();
            return Mono.just(SUBMISSION);
        });

        Mono<AuditSubmission> pending = service.submit(null, CONTEXT);

        assertThat(calls.get()).isZero();
        StepVerifier.create(pending)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("El evento es requerido"))
                .verify(VERIFY_TIMEOUT);
        assertThat(calls.get()).isZero();
    }

    @Test
    void rejectsNullContextOnSubscriptionWithoutCallingPort() {
        var calls = new AtomicInteger();
        var service = new SubmitAuditService((event, context) -> {
            calls.incrementAndGet();
            return Mono.just(SUBMISSION);
        });

        Mono<AuditSubmission> pending = service.submit(EVENT, null);

        assertThat(calls.get()).isZero();
        StepVerifier.create(pending)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(NullPointerException.class)
                        .hasMessage("El contexto es requerido"))
                .verify(VERIFY_TIMEOUT);
        assertThat(calls.get()).isZero();
    }

    @Test
    void rejectsNullPortWhenConstructingService() {
        assertThrows(NullPointerException.class, () -> new SubmitAuditService(null));
    }

    @Test
    void propagatesCancellationToPendingOutputPortPublisher() {
        var cancelled = new AtomicBoolean();
        var calls = new AtomicInteger();
        var service = new SubmitAuditService((event, context) -> {
            calls.incrementAndGet();
            return Mono.<AuditSubmission>never().doOnCancel(() -> cancelled.set(true));
        });

        StepVerifier.create(service.submit(EVENT, CONTEXT))
                .expectSubscription()
                .then(() -> assertThat(calls.get()).isEqualTo(1))
                .thenCancel()
                .verify(VERIFY_TIMEOUT);

        assertThat(cancelled.get()).isTrue();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void processesEachNewSubscriptionExactlyOnce() {
        var calls = new AtomicInteger();
        var service = new SubmitAuditService((event, context) -> {
            calls.incrementAndGet();
            return Mono.just(SUBMISSION);
        });
        Mono<AuditSubmission> pending = service.submit(EVENT, CONTEXT);

        StepVerifier.create(pending)
                .expectNext(SUBMISSION)
                .expectComplete()
                .verify(VERIFY_TIMEOUT);
        assertThat(calls.get()).isEqualTo(1);

        StepVerifier.create(pending)
                .expectNext(SUBMISSION)
                .expectComplete()
                .verify(VERIFY_TIMEOUT);
        assertThat(calls.get()).isEqualTo(2);
    }

    private static AuditEvent event() {
        var timestamp = OffsetDateTime.parse("2026-07-09T15:19:58-05:00");
        var message = new AuditEvent.EventMessage("TAPP", "TAPP", "LOGIN", "Login", "DNI", "00000000",
                "device", "operation", "cams", "account", Map.of("header", "Login"),
                List.of(new AuditEvent.PayloadDetail("result", "SUCCESS")));
        return new AuditEvent(timestamp, "AUDIT_LOG", timestamp, "INFORMATION", "MEDIUM", "TERMINATED",
                "demo", "source-service", "127.0.0.1", "destination-service", "127.0.0.1",
                "session", "EXECUTE", message);
    }
}
