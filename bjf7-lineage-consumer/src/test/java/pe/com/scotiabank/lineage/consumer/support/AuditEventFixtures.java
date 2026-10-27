package pe.com.scotiabank.lineage.consumer.support;

import pe.com.scotiabank.lineage.common.domain.AuditLogs;
import pe.com.scotiabank.lineage.common.domain.Metadata;
import pe.com.scotiabank.lineage.common.domain.Payload;
import pe.com.scotiabank.lineage.common.domain.QueueContract;

public final class AuditEventFixtures {

  private AuditEventFixtures() {}

  public static QueueContract event(String appCode) {
    return eventWithMessage(
        "event-" + appCode.trim(),
        """
        {"app_code":"%s","app_operation_code":"LOGIN","type_id":"DNI",
         "number_id":"12345678","device_id":"local-device","operation_number":"123"}
        """
            .formatted(appCode));
  }

  public static QueueContract eventWithMessage(String uuid, String message) {
    AuditLogs auditLog = new AuditLogs();
    auditLog.setUuid(uuid);
    auditLog.setMessage(message);
    auditLog.setEventTimestamp("2026-10-21T12:00:00-05:00");
    auditLog.setTimestamp("2026-10-21T12:00:00-05:00");
    auditLog.setClassification("0B");
    auditLog.setPriority("20");
    auditLog.setCategory("40");
    auditLog.setAuditAction("20");
    auditLog.setEventType("20");
    auditLog.setAccountName("local-test");
    Payload payload = new Payload();
    payload.setAuditLogs(auditLog);
    payload.setResult(true);
    Metadata metadata = new Metadata();
    metadata.setSourceId("BJF7");
    metadata.setTrxInd("LOGAUDIT01-" + uuid);
    QueueContract event = new QueueContract();
    event.setMetadata(metadata);
    event.setPayload(payload);
    return event;
  }
}
