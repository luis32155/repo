package pe.com.scotiabank.lineage.consumer.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.springframework.stereotype.Component;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;
import pe.com.scotiabank.lineage.consumer.domain.DocumentType;

@Slf4j
@Component
@AllArgsConstructor
public class Util {
  private final ObjectMapper objectMapper;

  public void initialize(
      AuditLogs auditLog,
      Set<String> apmCodes,
      Set<String> journeyCodes,
      List<AuditLogs> auditLogs) {
    Map<String, Object> map = getMapMessage(auditLog);
    if (MapUtils.isNotEmpty(map)) {
      String appCode = map.get("app_code").toString();
      String appOperationCode = map.get("app_operation_code").toString();
      String typeId = map.get("type_id").toString();
      String documentType = getDocumentType(typeId);

      if (documentType == null) {
        log.error("Unsupported type id: {}. Skipping audit log content: {}", typeId, auditLog);
        return;
      }

      auditLog.setAppCode(appCode);
      auditLog.setAppOperationCode(appOperationCode);
      auditLog.setTypeId(documentType);
      auditLog.setNumberId(map.get("number_id").toString());
      auditLog.setDeviceId(map.get("device_id").toString());

      map.remove("app_code");
      map.remove("app_description");
      map.remove("app_operation_code");
      map.remove("app_operation_description");
      map.remove("type_id");
      map.remove("number_id");
      map.remove("device_id");

      String simpleEventMessage = getSimplifiedEventMessage(map);
      if (simpleEventMessage == null) {
        log.error(
            "Unable to generate simplified event message. Skipping audit log content: {}",
            auditLog);
        return;
      }

      log.info("Audit log received: {}", auditLog);
      auditLog.setMessage(simpleEventMessage);
      auditLogs.add(auditLog);
      apmCodes.add(appCode);
      journeyCodes.add(appOperationCode);
    }
  }

  public String getDocumentType(String typeId) {
    try {
      return DocumentType.valueOf(typeId).getCode();
    } catch (Exception e) {
      return null;
    }
  }

  public String getSimplifiedEventMessage(Map<String, Object> map) {
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  public Map<String, Object> getMapMessage(AuditLogs auditLog) {
    try {
      return objectMapper.readValue(auditLog.getMessage(), Map.class);
    } catch (JsonProcessingException e) {
      log.error("Unable to parse json string. Audit log content: {}", auditLog);
      return null;
    }
  }

  public List<AuditLogs> filterAndInitByJourneyId(
      List<AuditLogs> auditLogs, Map<String, Integer> channelIds) {
    List<AuditLogs> outAuditLogs = new ArrayList<>();

    for (AuditLogs auditLog : auditLogs) {
      Integer journeyId =
          channelIds.get(auditLog.getAppCode() + "-" + auditLog.getAppOperationCode());
      if (journeyId == null) {
        log.warn("Unable to get journeyId for audit log: {}", auditLog);
        continue;
      }

      auditLog.setJourneyId(journeyId);
      outAuditLogs.add(auditLog);
    }

    return outAuditLogs;
  }
}
