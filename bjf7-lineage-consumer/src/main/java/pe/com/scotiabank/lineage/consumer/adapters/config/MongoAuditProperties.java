package pe.com.scotiabank.lineage.consumer.adapters.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.mongodb")
public class MongoAuditProperties {

  @NotBlank private String applicationCode = "TAPP";
  @NotBlank private String collection = "tapp_audit_events";

  @Min(1)
  private int retentionDays = 365;

  @NotEmpty
  private Map<@NotBlank String, @NotBlank String> eventTypeLabels =
      new LinkedHashMap<>(Map.of("20", "INFORMATION"));
}
