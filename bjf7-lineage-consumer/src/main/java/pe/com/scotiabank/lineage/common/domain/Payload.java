package pe.com.scotiabank.lineage.common.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"auditLogs", "result"})
public class Payload {

  public Payload() {}

  public Payload(Payload other) {
    this.auditLogs = other.getAuditLogs();
    this.result = other.result;
  }

  @Valid
  @NotNull
  @JsonProperty("auditLogs")
  private AuditLogs auditLogs;

  @JsonProperty("result")
  @JsonPropertyDescription("The result of this transaction")
  private Boolean result;

  @JsonProperty("auditLogs")
  public AuditLogs getAuditLogs() {
    if (auditLogs == null) {
      return null;
    }
    return new AuditLogs(auditLogs);
  }

  @JsonProperty("auditLogs")
  public void setAuditLogs(AuditLogs auditLogs) {
    if (auditLogs == null) {
      this.auditLogs = null;
    } else {
      this.auditLogs = new AuditLogs(auditLogs);
    }
  }

  @JsonProperty("result")
  public Boolean getResult() {
    return result;
  }

  @JsonProperty("result")
  public void setResult(Boolean result) {
    this.result = result;
  }
}
