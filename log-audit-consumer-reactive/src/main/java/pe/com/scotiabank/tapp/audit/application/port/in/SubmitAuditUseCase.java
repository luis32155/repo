package pe.com.scotiabank.tapp.audit.application.port.in;

import reactor.core.publisher.Mono;
import pe.com.scotiabank.tapp.audit.application.model.AuditSubmission;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;

public interface SubmitAuditUseCase {

    Mono<AuditSubmission> submit(AuditEvent event, AuditContext context);
}
