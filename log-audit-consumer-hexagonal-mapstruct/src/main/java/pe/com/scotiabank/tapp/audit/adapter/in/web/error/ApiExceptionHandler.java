package pe.com.scotiabank.tapp.audit.adapter.in.web.error;

import java.util.Map;
import java.util.stream.Stream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.validation.method.ParameterErrors;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AuditDeliveryException.class)
    public ProblemDetail handleAuditApi(AuditDeliveryException exception) {
        HttpStatusCode status = switch (exception.kind()) {
            case REJECTED -> exception.providerStatusCode() == null
                    ? HttpStatus.BAD_GATEWAY : HttpStatusCode.valueOf(exception.providerStatusCode());
            case PROVIDER_FAILURE, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
        };
        var problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setTitle("Error al consumir la API de auditoría");
        if (exception.providerStatusCode() != null) {
            problem.setProperty("upstream_status", exception.providerStatusCode());
        }
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La solicitud contiene campos inválidos.");
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", "Campo requerido o inválido"))
                .toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, "La solicitud contiene campos inválidos.");
        problem.setProperty("errors", exception.getParameterValidationResults().stream()
                .flatMap(result -> {
                    if (result instanceof ParameterErrors errors) {
                        return errors.getFieldErrors().stream()
                                .map(error -> Map.of("field", error.getField(), "message", "Campo requerido o inválido"));
                    }
                    var name = result.getMethodParameter().getParameterName();
                    return Stream.of(Map.of("field", name == null ? "header" : name,
                            "message", "Cabecera requerida o inválida"));
                }).toList());
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "JSON inválido. Las fechas deben usar ISO 8601 con zona horaria.");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }
}
