package pe.com.scotiabank.tapp.audit.application.port.in;

import pe.com.scotiabank.tapp.audit.application.model.AuditSubmission;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;

public interface SubmitAuditUseCase {

    AuditSubmission submit(AuditEvent event, AuditContext context);
}
