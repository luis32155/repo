package pe.com.scotiabank.tapp.audit.domain.model;

import java.util.List;

public record AuditResult(List<AuditStatus> data) {

    public record AuditStatus(String status) {
    }
}
