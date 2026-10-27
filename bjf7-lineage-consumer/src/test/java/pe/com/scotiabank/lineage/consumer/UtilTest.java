package pe.com.scotiabank.lineage.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;
import pe.com.scotiabank.lineage.consumer.util.Util;

@ExtendWith(MockitoExtension.class)
class UtilTest {

  @InjectMocks private Util util;
  @Mock private ObjectMapper objectMapper;

  @Test
  void noDocumentType() throws Exception {
    Map<String, Object> map = new HashMap<>();
    map.put("app_code", "app_code");
    map.put("app_operation_code", "app_operation_code");
    map.put("type_id", "type_id");
    when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(map);
    AuditLogs auditLogs = new AuditLogs();
    auditLogs.setMessage("mock");

    util.initialize(auditLogs, null, null, null);

    assertNull(auditLogs.getAppCode());
  }

  @Test
  void noSimplifiedEventMessage() throws Exception {
    Map<String, Object> map = new HashMap<>();
    map.put("app_code", "app_code");
    map.put("app_operation_code", "app_operation_code");
    map.put("type_id", "DNI");
    map.put("number_id", "number_id");
    map.put("device_id", "device_id");
    JsonProcessingException exception = mock(JsonProcessingException.class);
    when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(map);
    when(objectMapper.writeValueAsString(any(Map.class))).thenThrow(exception);
    AuditLogs auditLogs = new AuditLogs();
    auditLogs.setMessage("mock");

    util.initialize(auditLogs, null, null, null);

    assertEquals("mock", auditLogs.getMessage());
  }

  @Test
  void unabletoParseJson() throws Exception {
    JsonProcessingException exception = mock(JsonProcessingException.class);
    when(objectMapper.readValue(anyString(), eq(Map.class))).thenThrow(exception);
    AuditLogs auditLogs = new AuditLogs();
    auditLogs.setMessage("mock");

    Map<String, Object> out = util.getMapMessage(auditLogs);

    assertNull(out);
  }
}
