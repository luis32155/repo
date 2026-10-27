package pe.com.scotiabank.lineage.common.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "uuid",
  "timestamp",
  "classification",
  "priority",
  "category",
  "source_host_name",
  "source_ip_address",
  "destination_host_name",
  "destination_ip_address",
  "session_identifier",
  "audit_action",
  "legacy_ind",
  "eventTimestamp",
  "eventType",
  "accountName",
  "message"
})
public class AuditLogs {
  public AuditLogs() {}

  public AuditLogs(AuditLogs other) {
    this.uuid = other.uuid;
    this.timestamp = other.timestamp;
    this.classification = other.classification;
    this.priority = other.priority;
    this.category = other.category;
    this.sourceHostName = other.sourceHostName;
    this.sourceIpAddress = other.sourceIpAddress;
    this.destinationHostName = other.destinationHostName;
    this.destinationIpAddress = other.destinationIpAddress;
    this.sessionIdentifier = other.sessionIdentifier;
    this.auditAction = other.auditAction;
    this.legacyInd = other.legacyInd;
    this.message = other.message;
    this.eventTimestamp = other.eventTimestamp;
    this.eventType = other.eventType;
    this.accountName = other.accountName;
  }

  @NotBlank
  @JsonProperty("uuid")
  private String uuid;

  @NotBlank
  @JsonProperty("timestamp")
  private String timestamp;

  @NotBlank
  @JsonProperty("classification")
  private String classification;

  @NotBlank
  @JsonProperty("priority")
  private String priority;

  @NotBlank
  @JsonProperty("category")
  private String category;

  @NotBlank
  @JsonProperty("source_host_name")
  private String sourceHostName;

  @NotBlank
  @JsonProperty("source_ip_address")
  private String sourceIpAddress;

  @NotBlank
  @JsonProperty("destination_host_name")
  private String destinationHostName;

  @NotBlank
  @JsonProperty("destination_ip_address")
  private String destinationIpAddress;

  @JsonProperty("session_identifier")
  private String sessionIdentifier;

  @NotBlank
  @JsonProperty("audit_action")
  private String auditAction;

  @NotBlank
  @JsonProperty("legacy_ind")
  private String legacyInd;

  @NotBlank
  @JsonProperty("message")
  private String message;

  @NotBlank
  @JsonProperty("event_timestamp")
  private String eventTimestamp;

  @NotBlank
  @JsonProperty("event_type")
  private String eventType;

  @NotBlank
  @JsonProperty("account_name")
  private String accountName;

  @Getter @Setter @JsonIgnore private Long id;
  @Getter @Setter @JsonIgnore private String appCode;
  @Getter @Setter @JsonIgnore private String appOperationCode;
  @Getter @Setter @JsonIgnore private String typeId;
  @Getter @Setter @JsonIgnore private String numberId;
  @Getter @Setter @JsonIgnore private Integer journeyId;
  @Getter @Setter @JsonIgnore private String deviceId;

  @JsonProperty("uuid")
  public String getUuid() {
    return uuid;
  }

  @JsonProperty("uuid")
  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  @JsonProperty("timestamp")
  public String getTimestamp() {
    return timestamp;
  }

  @JsonProperty("timestamp")
  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  @JsonProperty("classification")
  public String getClassification() {
    return classification;
  }

  @JsonProperty("classification")
  public void setClassification(String classification) {
    this.classification = classification;
  }

  @JsonProperty("priority")
  public String getPriority() {
    return priority;
  }

  @JsonProperty("priority")
  public void setPriority(String priority) {
    this.priority = priority;
  }

  @JsonProperty("category")
  public String getCategory() {
    return category;
  }

  @JsonProperty("category")
  public void setCategory(String category) {
    this.category = category;
  }

  @JsonProperty("source_host_name")
  public String getSourceHostName() {
    return sourceHostName;
  }

  @JsonProperty("source_host_name")
  public void setSourceHostName(String sourceHostName) {
    this.sourceHostName = sourceHostName;
  }

  @JsonProperty("source_ip_address")
  public String getSourceIpAddress() {
    return sourceIpAddress;
  }

  @JsonProperty("source_ip_address")
  public void setSourceIpAddress(String sourceIpAddress) {
    this.sourceIpAddress = sourceIpAddress;
  }

  @JsonProperty("destination_host_name")
  public String getDestinationHostName() {
    return destinationHostName;
  }

  @JsonProperty("destination_host_name")
  public void setDestinationHostName(String destinationHostName) {
    this.destinationHostName = destinationHostName;
  }

  @JsonProperty("destination_ip_address")
  public String getDestinationIpAddress() {
    return destinationIpAddress;
  }

  @JsonProperty("destination_ip_address")
  public void setDestinationIpAddress(String destinationIpAddress) {
    this.destinationIpAddress = destinationIpAddress;
  }

  @JsonProperty("session_identifier")
  public String getSessionIdentifier() {
    return sessionIdentifier;
  }

  @JsonProperty("session_identifier")
  public void setSessionIdentifier(String sessionIdentifier) {
    this.sessionIdentifier = sessionIdentifier;
  }

  @JsonProperty("audit_action")
  public String getAuditAction() {
    return auditAction;
  }

  @JsonProperty("audit_action")
  public void setAuditAction(String auditAction) {
    this.auditAction = auditAction;
  }

  @JsonProperty("legacy_ind")
  public String getLegacyInd() {
    return legacyInd;
  }

  @JsonProperty("legacy_ind")
  public void setLegacyInd(String legacyInd) {
    this.legacyInd = legacyInd;
  }

  @JsonProperty("message")
  public String getMessage() {
    return message;
  }

  @JsonProperty("message")
  public void setMessage(String message) {
    this.message = message;
  }

  @JsonProperty("event_timestamp")
  public String getEventTimestamp() {
    return eventTimestamp;
  }

  @JsonProperty("event_timestamp")
  public void setEventTimestamp(String eventTimestamp) {
    this.eventTimestamp = eventTimestamp;
  }

  @JsonProperty("event_type")
  public String getEventType() {
    return eventType;
  }

  @JsonProperty("event_type")
  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  @JsonProperty("account_name")
  public String getAccountName() {
    return accountName;
  }

  @JsonProperty("account_name")
  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }
}
