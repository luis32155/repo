package pe.com.scotiabank.tapp.audit.adapter.out.http;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pe.com.scotiabank.tapp.audit.adapter.common.http.AuditHttpHeaders;
import pe.com.scotiabank.tapp.audit.adapter.out.http.config.AuditApiProperties;
import pe.com.scotiabank.tapp.audit.adapter.out.http.dto.AuditApiResponse;
import pe.com.scotiabank.tapp.audit.adapter.out.http.mapper.AuditApiMapper;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException.FailureKind;
import pe.com.scotiabank.tapp.audit.application.model.AuditSubmission;
import pe.com.scotiabank.tapp.audit.application.port.out.SendAuditPort;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;

@Component
public class AuditApiClient implements SendAuditPort {

    private final RestClient restClient;
    private final AuditApiProperties properties;
    private final Validator validator;
    private final AuditApiMapper mapper;

    public AuditApiClient(RestClient restClient, AuditApiProperties properties, Validator validator, AuditApiMapper mapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.validator = validator;
        this.mapper = mapper;
    }

    @Override
    public AuditSubmission send(AuditEvent event, AuditContext context) {
        try {
            var response = restClient.post()
                    .uri(properties.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(AuditHttpHeaders.API_VERSION, context.apiVersion())
                    .header(AuditHttpHeaders.CHANNEL_ID, context.channelId())
                    .header(AuditHttpHeaders.ORIGINATING_APPL_CODE, context.originatingApplCode())
                    .header(AuditHttpHeaders.COUNTRY_CODE, context.countryCode())
                    .body(mapper.toRequest(event))
                    .retrieve()
                    .toEntity(AuditApiResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                    || !validator.validate(response.getBody()).isEmpty()) {
                throw new AuditDeliveryException(FailureKind.INVALID_RESPONSE,
                        "La API de auditoría devolvió una respuesta incompatible con el contrato.",
                        response.getStatusCode().value(), null);
            }
            return new AuditSubmission(mapper.toDomain(response.getBody()), response.getStatusCode().value());
        } catch (RestClientResponseException exception) {
            var upstreamStatus = exception.getStatusCode();
            var kind = upstreamStatus.is4xxClientError() ? FailureKind.REJECTED : FailureKind.PROVIDER_FAILURE;
            throw new AuditDeliveryException(kind, "La API de auditoría rechazó la solicitud.",
                    upstreamStatus.value(), exception);
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw new AuditDeliveryException(FailureKind.TIMEOUT,
                        "La API de auditoría excedió el tiempo de espera.", null, exception);
            }
            throw new AuditDeliveryException(FailureKind.UNAVAILABLE,
                    "No se pudo conectar con la API de auditoría.", null, exception);
        } catch (RestClientException exception) {
            throw new AuditDeliveryException(FailureKind.INVALID_RESPONSE,
                    "No se pudo interpretar la respuesta de la API de auditoría.", null, exception);
        }
    }

    private static boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
