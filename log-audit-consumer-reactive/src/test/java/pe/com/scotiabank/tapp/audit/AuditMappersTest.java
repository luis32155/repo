package pe.com.scotiabank.tapp.audit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogRequest;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogResponse;
import pe.com.scotiabank.tapp.audit.adapter.in.web.mapper.AuditWebMapper;
import pe.com.scotiabank.tapp.audit.adapter.out.http.dto.AuditApiRequest;
import pe.com.scotiabank.tapp.audit.adapter.out.http.dto.AuditApiResponse;
import pe.com.scotiabank.tapp.audit.adapter.out.http.mapper.AuditApiMapper;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import pe.com.scotiabank.tapp.audit.domain.model.AuditResult;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class AuditMappersTest {

    private final AuditWebMapper webMapper = Mappers.getMapper(AuditWebMapper.class);
    private final AuditApiMapper apiMapper = Mappers.getMapper(AuditApiMapper.class);
    private final JsonMapper json = JsonMapper.builder()
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();

    @Test
    void mapsAllFourHeadersWithoutChangingTheirValuesOrPositions() {
        assertThat(webMapper.toContext(" v1 ", "mobile", "TAPP", "PE"))
                .isEqualTo(new AuditContext(" v1 ", "mobile", "TAPP", "PE"));
    }

    @Test
    void preservesRepeatedAndNullableHeaderValuesForCallerValidation() {
        assertThat(webMapper.toContext("same", "same", "same", "same"))
                .isEqualTo(new AuditContext("same", "same", "same", "same"));
        assertThat(webMapper.toContext(null, "channel", "TAPP", "PE"))
                .isEqualTo(new AuditContext(null, "channel", "TAPP", "PE"));
        assertThat(webMapper.toContext("v1", null, "TAPP", "PE"))
                .isEqualTo(new AuditContext("v1", null, "TAPP", "PE"));
        assertThat(webMapper.toContext("v1", "channel", null, "PE"))
                .isEqualTo(new AuditContext("v1", "channel", null, "PE"));
        assertThat(webMapper.toContext("v1", "channel", "TAPP", null))
                .isEqualTo(new AuditContext("v1", "channel", "TAPP", null));
        assertThat(webMapper.toContext(null, null, null, null)).isNull();
    }

    @Test
    void mapsTheCompleteAuditContractWithTwentyDetailsAndOriginalOffsets() throws IOException {
        String fixture = fixture();
        var request = json.readValue(fixture, AuditLogRequest.class);

        var event = webMapper.toDomain(request);
        var forwarded = apiMapper.toRequest(event);

        assertThat(event).isEqualTo(expectedFixtureEvent());
        assertThat(forwarded.eventMessage().channelPayloadDetail()).hasSize(20);
        assertThat(forwarded.logTimestamp()).isEqualTo(OffsetDateTime.parse("2026-07-09T15:19:59-05:00"));
        assertThat(forwarded.eventTimestamp()).isEqualTo(OffsetDateTime.parse("2026-07-09T15:19:58-05:00"));
        assertThat(json.readTree(json.writeValueAsString(forwarded))).isEqualTo(json.readTree(fixture));
        assertThat(json.readTree(json.writeValueAsString(request))).isEqualTo(json.readTree(fixture));
    }

    @Test
    void preservesDynamicHeaderObjectsArraysNumbersBooleansAndNullWithoutMutatingInput() throws IOException {
        ObjectNode input = (ObjectNode) json.readTree(fixture());
        ObjectNode message = (ObjectNode) input.get("event_message");
        var dynamicHeader = json.readTree("""
                {"header":"Pago dinámico","amount":150.75,"enabled":true,"optional":null,
                 "merchant":{"name":"Comercio Perú","labels":["P2M","TAPP"]},
                 "steps":[{"code":"CREDIT","ok":true},7,null,["nested","array"]]}
                """);
        message.set("channel_payload_header", dynamicHeader);
        String originalJson = input.toString();
        var request = json.readValue(originalJson, AuditLogRequest.class);

        var event = webMapper.toDomain(request);
        var forwarded = apiMapper.toRequest(event);

        assertThat(json.readTree(json.writeValueAsString(forwarded))).isEqualTo(json.readTree(originalJson));
        assertThat(json.readTree(json.writeValueAsString(request))).isEqualTo(json.readTree(originalJson));
        var forwardedHeader = json.valueToTree(forwarded.eventMessage().channelPayloadHeader());
        assertThat(forwardedHeader).isEqualTo(dynamicHeader);
    }

    @Test
    void givesEachLayerItsOwnHeaderMapAndDetailListWithoutMutatingTheOtherLayers() throws IOException {
        var request = json.readValue(fixture(), AuditLogRequest.class);
        var event = webMapper.toDomain(request);
        var forwarded = apiMapper.toRequest(event);

        assertThat(event.eventMessage().channelPayloadHeader()).isNotSameAs(request.eventMessage().channelPayloadHeader());
        assertThat(forwarded.eventMessage().channelPayloadHeader()).isNotSameAs(event.eventMessage().channelPayloadHeader());
        assertThat(event.eventMessage().channelPayloadDetail()).isNotSameAs(request.eventMessage().channelPayloadDetail());
        assertThat(forwarded.eventMessage().channelPayloadDetail()).isNotSameAs(event.eventMessage().channelPayloadDetail());

        event.eventMessage().channelPayloadHeader().put("domain_only", "value");
        event.eventMessage().channelPayloadDetail().clear();

        assertThat(request.eventMessage().channelPayloadHeader()).doesNotContainKey("domain_only");
        assertThat(forwarded.eventMessage().channelPayloadHeader()).doesNotContainKey("domain_only");
        assertThat(request.eventMessage().channelPayloadDetail()).hasSize(20);
        assertThat(forwarded.eventMessage().channelPayloadDetail()).hasSize(20);
    }

    @Test
    void returnsNullForAbsentRootMessagesDetailsAndResponses() {
        assertThat(webMapper.toDomain((AuditLogRequest) null)).isNull();
        assertThat(webMapper.toDomain((AuditLogRequest.EventMessage) null)).isNull();
        assertThat(webMapper.toDomain((AuditLogRequest.PayloadDetail) null)).isNull();
        assertThat(apiMapper.toRequest((AuditEvent) null)).isNull();
        assertThat(apiMapper.toRequest((AuditEvent.EventMessage) null)).isNull();
        assertThat(apiMapper.toRequest((AuditEvent.PayloadDetail) null)).isNull();
        assertThat(apiMapper.toDomain((AuditApiResponse) null)).isNull();
        assertThat(apiMapper.toDomain((AuditApiResponse.AuditStatus) null)).isNull();
        assertThat(webMapper.toResponse((AuditResult) null)).isNull();
        assertThat(webMapper.toResponse((AuditResult.AuditStatus) null)).isNull();
    }

    @Test
    void preservesAbsentHeaderAndDetailCollectionsInsteadOfInventingContent() {
        var eventMessage = webMapper.toDomain(message(null, null));
        var forwarded = apiMapper.toRequest(eventMessage);

        assertThat(eventMessage.channelPayloadHeader()).isNull();
        assertThat(eventMessage.channelPayloadDetail()).isNull();
        assertThat(forwarded.channelPayloadHeader()).isNull();
        assertThat(forwarded.channelPayloadDetail()).isNull();
    }

    @Test
    void preservesExplicitlyEmptyHeaderAndDetailCollections() {
        var eventMessage = webMapper.toDomain(message(new LinkedHashMap<>(), new ArrayList<>()));
        var forwarded = apiMapper.toRequest(eventMessage);

        assertThat(eventMessage.channelPayloadHeader()).isEmpty();
        assertThat(eventMessage.channelPayloadDetail()).isEmpty();
        assertThat(forwarded.channelPayloadHeader()).isEmpty();
        assertThat(forwarded.channelPayloadDetail()).isEmpty();
    }

    @Test
    void preservesDetailOrderIncludingNullEntriesSoValidationCanRejectThem() {
        var eventMessage = webMapper.toDomain(message(Map.of("header", "Pago"), Arrays.asList(
                new AuditLogRequest.PayloadDetail("amount", "150.00"), null,
                new AuditLogRequest.PayloadDetail("currency", "PEN"))));

        assertThat(eventMessage.channelPayloadDetail()).containsExactly(
                new AuditEvent.PayloadDetail("amount", "150.00"), null,
                new AuditEvent.PayloadDetail("currency", "PEN"));
        assertThat(apiMapper.toRequest(eventMessage).channelPayloadDetail()).containsExactly(
                new AuditApiRequest.PayloadDetail("amount", "150.00"), null,
                new AuditApiRequest.PayloadDetail("currency", "PEN"));
    }

    @Test
    void mapsAllProviderStatusesInOrderThroughBothBoundaries() {
        var provider = new AuditApiResponse(List.of(new AuditApiResponse.AuditStatus("OK"),
                new AuditApiResponse.AuditStatus("ACCEPTED"), new AuditApiResponse.AuditStatus("QUEUED")));

        var result = apiMapper.toDomain(provider);
        var response = webMapper.toResponse(result);

        assertThat(result).isEqualTo(new AuditResult(List.of(new AuditResult.AuditStatus("OK"),
                new AuditResult.AuditStatus("ACCEPTED"), new AuditResult.AuditStatus("QUEUED"))));
        assertThat(response).isEqualTo(new AuditLogResponse(List.of(new AuditLogResponse.AuditStatus("OK"),
                new AuditLogResponse.AuditStatus("ACCEPTED"), new AuditLogResponse.AuditStatus("QUEUED"))));
        assertThat(json.readTree(json.writeValueAsString(response))).isEqualTo(json.readTree("""
                {"data":[{"status":"OK"},{"status":"ACCEPTED"},{"status":"QUEUED"}]}
                """));
    }

    @Test
    void preservesAbsentStatusListsForProviderContractValidation() {
        var result = apiMapper.toDomain(new AuditApiResponse(null));

        assertThat(result).isEqualTo(new AuditResult(null));
        assertThat(webMapper.toResponse(result)).isEqualTo(new AuditLogResponse(null));
    }

    @Test
    void preservesEmptyStatusListsWithoutInventingSuccessfulResults() {
        var result = apiMapper.toDomain(new AuditApiResponse(List.of()));

        assertThat(result).isEqualTo(new AuditResult(List.of()));
        assertThat(webMapper.toResponse(result)).isEqualTo(new AuditLogResponse(List.of()));
    }

    @Test
    void preservesNullStatusEntriesAndValuesForProviderContractValidation() {
        var result = apiMapper.toDomain(new AuditApiResponse(Arrays.asList(
                new AuditApiResponse.AuditStatus("OK"), null, new AuditApiResponse.AuditStatus(null))));

        assertThat(result.data()).containsExactly(new AuditResult.AuditStatus("OK"), null,
                new AuditResult.AuditStatus(null));
        assertThat(webMapper.toResponse(result).data()).containsExactly(new AuditLogResponse.AuditStatus("OK"),
                null, new AuditLogResponse.AuditStatus(null));
    }

    private static AuditLogRequest.EventMessage message(
            Map<String, Object> header, List<AuditLogRequest.PayloadDetail> details) {
        return new AuditLogRequest.EventMessage("TAPP", "TAPP Mobile", "CREDIT", "Crédito", "DNI", "12345678",
                "device", "operation", "cams", "account", header, details);
    }

    private static AuditEvent expectedFixtureEvent() {
        var details = IntStream.rangeClosed(1, 20)
                .mapToObj(index -> new AuditEvent.PayloadDetail(
                        "Campo Key " + index + " Campo Key " + index + " Campo Key " + index,
                        "Campo Value " + index + " Campo Value " + index + " Campo Value " + index))
                .toList();
        var message = new AuditEvent.EventMessage("TAPP", "Telebanking 504", "B3QXRCR",
                "Registro de credenciales exitosamente :D", "DNI", "33445566", "Campo device id",
                "Campo operation number", "campo-cams-id-7dec-11d0-a765-00a0c91e6bf6", "Campo bt account",
                Map.of("header", "Prueba con Miguel", "description",
                        "Campo de descripción del detalle de la operacion - description - Description aún mas larga"), details);
        return new AuditEvent(OffsetDateTime.parse("2026-07-09T15:19:59-05:00"), "AUDIT_LOG",
                OffsetDateTime.parse("2026-07-09T15:19:58-05:00"), "FATAL", "MEDIUM", "SUSPENDED",
                "Campo nombre de la cuenta", "Campo source host name", "888.888.888.888",
                "Campo destination host name", "999.999.999.999", "Campo session identifier", "ENUMERATE", message);
    }

    private static String fixture() throws IOException {
        try (InputStream input = Objects.requireNonNull(AuditMappersTest.class.getResourceAsStream("/audit-request.json"),
                "Missing audit-request.json test fixture")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
