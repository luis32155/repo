package pe.com.scotiabank.lineage.common.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"metadata", "payload"})
public class QueueContract {

  @Valid
  @NotNull
  @JsonProperty("metadata")
  private Metadata metadata;

  @Valid
  @NotNull
  @JsonProperty("payload")
  private Payload payload;

  @JsonProperty("metadata")
  public Metadata getMetadata() {
    if (metadata == null) {
      return null;
    }
    return new Metadata(metadata);
  }

  @JsonProperty("metadata")
  public void setMetadata(Metadata metadata) {
    if (metadata == null) {
      this.metadata = null;
    } else {
      this.metadata = new Metadata(metadata);
    }
  }

  @JsonProperty("payload")
  public Payload getPayload() {
    if (payload == null) {
      return null;
    }
    return new Payload(payload);
  }

  @JsonProperty("payload")
  public void setPayload(Payload payload) {
    if (payload == null) {
      this.payload = null;
    } else {
      this.payload = new Payload(payload);
    }
  }
}
