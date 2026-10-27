package pe.com.scotiabank.tapp.audit.adapter.in.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AuditLogResponse(@NotEmpty List<@NotNull @Valid AuditStatus> data) {

    public record AuditStatus(@NotBlank String status) {
    }
}
