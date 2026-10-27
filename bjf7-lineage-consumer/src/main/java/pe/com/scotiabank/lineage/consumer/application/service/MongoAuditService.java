package pe.com.scotiabank.lineage.consumer.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;
import pe.com.scotiabank.lineage.common.domain.Payload;
import pe.com.scotiabank.lineage.common.domain.QueueContract;
import pe.com.scotiabank.lineage.consumer.application.ports.output.MongoAuditOutPort;

@Slf4j
public class MongoAuditService {

  private final MongoAuditOutPort mongoAuditOutPort;
  private final ObjectMapper objectMapper;
  private final String applicationCode;

  public MongoAuditService(
      MongoAuditOutPort mongoAuditOutPort, ObjectMapper objectMapper, String applicationCode) {
    this.mongoAuditOutPort = mongoAuditOutPort;
    this.objectMapper = objectMapper;
    this.applicationCode = applicationCode.trim();
  }

  public void saveTappEvents(List<QueueContract> items) {
    List<QueueContract> tappEvents = items.stream().filter(this::isTappEvent).toList();
    if (!tappEvents.isEmpty()) {
      mongoAuditOutPort.save(tappEvents);
    }
  }

  private boolean isTappEvent(QueueContract item) {
    Payload payload = item == null ? null : item.getPayload();
    AuditLogs auditLog = payload == null ? null : payload.getAuditLogs();
    if (auditLog == null || auditLog.getMessage() == null) {
      return false;
    }

    try {
      JsonNode message = objectMapper.readTree(auditLog.getMessage());
      JsonNode appCode = message == null ? null : message.get("app_code");
      return appCode != null
          && appCode.isTextual()
          && applicationCode.equalsIgnoreCase(appCode.textValue().trim());
    } catch (JsonProcessingException exception) {
      log.warn("Unable to identify application code for audit event {}", auditLog.getUuid());
      return false;
    }
  }
}
