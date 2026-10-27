package pe.com.scotiabank.lineage.common.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "event_name",
  "event_action",
  "country",
  "source_id",
  "schema_name",
  "origination_timestamp",
  "trx_ind"
})
public class Metadata {

  public Metadata() {}

  public Metadata(Metadata other) {
    this.eventName = other.eventName;
    this.eventAction = other.eventAction;
    this.country = other.country;
    this.sourceId = other.sourceId;
    this.schemaName = other.schemaName;
    this.originationTimestamp = other.getOriginationTimestamp();
    this.trxInd = other.trxInd;
  }

  @JsonProperty("event_name")
  private String eventName;

  @JsonProperty("event_action")
  private String eventAction;

  @JsonProperty("country")
  private String country;

  @JsonProperty("source_id")
  private String sourceId;

  @JsonProperty("schema_name")
  private String schemaName;

  @JsonProperty("origination_timestamp")
  private Date originationTimestamp;

  @JsonProperty("trx_ind")
  private String trxInd;

  @JsonProperty("event_name")
  public String getEventName() {
    return eventName;
  }

  @JsonProperty("event_name")
  public void setEventName(String eventName) {
    this.eventName = eventName;
  }

  @JsonProperty("event_action")
  public String getEventAction() {
    return eventAction;
  }

  @JsonProperty("event_action")
  public void setEventAction(String eventAction) {
    this.eventAction = eventAction;
  }

  @JsonProperty("country")
  public String getCountry() {
    return country;
  }

  @JsonProperty("country")
  public void setCountry(String country) {
    this.country = country;
  }

  @JsonProperty("source_id")
  public String getSourceId() {
    return sourceId;
  }

  @JsonProperty("source_id")
  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }

  @JsonProperty("schema_name")
  public String getSchemaName() {
    return schemaName;
  }

  @JsonProperty("schema_name")
  public void setSchemaName(String schemaName) {
    this.schemaName = schemaName;
  }

  @JsonProperty("origination_timestamp")
  public Date getOriginationTimestamp() {
    if (originationTimestamp == null) {
      return null;
    }

    return new Date(originationTimestamp.getTime());
  }

  @JsonProperty("origination_timestamp")
  public void setOriginationTimestamp(Date originationTimestamp) {
    if (originationTimestamp == null) {
      this.originationTimestamp = null;
    } else {
      this.originationTimestamp = new Date(originationTimestamp.getTime());
    }
  }

  @JsonProperty("trx_ind")
  public String getTrxInd() {
    return trxInd;
  }

  @JsonProperty("trx_ind")
  public void setTrxInd(String trxInd) {
    this.trxInd = trxInd;
  }
}
