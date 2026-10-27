package pe.com.scotiabank.tapp.audit.adapter.out.http.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AuditApiResponse(@NotEmpty List<@NotNull @Valid AuditStatus> data) {

    public record AuditStatus(@NotBlank String status) {
    }
}
