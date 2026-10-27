package pe.com.scotiabank.lineage.common.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QueueContractTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void preservesProducerJsonContractAndIgnoresUnknownEnvelopeFields() throws Exception {
    ObjectNode json = mapper.valueToTree(event("TAPP"));
    ObjectNode metadata = (ObjectNode) json.get("metadata");
    metadata.put("event_name", "AUDIT");
    metadata.put("event_action", "CREATE");
    metadata.put("country", "PE");
    metadata.put("schema_name", "audit-event");
    metadata.put("origination_timestamp", 1792558800000L);
    ObjectNode audit = (ObjectNode) json.get("payload").get("auditLogs");
    audit.put("legacy_ind", "N");
    json.put("future_envelope_field", "ignored");

    QueueContract contract = mapper.treeToValue(json, QueueContract.class);

    JsonNode result = mapper.valueToTree(contract);
    json.remove("future_envelope_field");
    assertEquals(json, result);
    assertEquals("AUDIT", contract.getMetadata().getEventName());
    assertEquals("CREATE", contract.getMetadata().getEventAction());
    assertEquals("PE", contract.getMetadata().getCountry());
    assertEquals("audit-event", contract.getMetadata().getSchemaName());
    assertEquals("N", contract.getPayload().getAuditLogs().getLegacyInd());
    assertTrue(result.get("payload").has("auditLogs"));
    assertFalse(result.get("payload").has("audit_logs"));
  }

  @Test
  void sourceObjectsCannotMutateStoredMetadataOrPayload() {
    Metadata metadata = new Metadata();
    metadata.setSourceId("TAPP");
    AuditLogs audit = new AuditLogs();
    audit.setMessage("{\"app_code\":\"TAPP\"}");
    Payload payload = new Payload();
    payload.setAuditLogs(audit);
    payload.setResult(true);
    QueueContract contract = new QueueContract();
    contract.setMetadata(metadata);
    contract.setPayload(payload);

    metadata.setSourceId("OTHER");
    audit.setMessage("{}");
    payload.setResult(false);
    payload.setAuditLogs(null);

    assertEquals("TAPP", contract.getMetadata().getSourceId());
    assertEquals("{\"app_code\":\"TAPP\"}", contract.getPayload().getAuditLogs().getMessage());
    assertTrue(contract.getPayload().getResult());
  }

  @Test
  void returnedCopiesCannotMutateOriginalEnvelope() {
    QueueContract contract = event("TAPP");
    Metadata returnedMetadata = contract.getMetadata();
    Payload returnedPayload = contract.getPayload();
    AuditLogs returnedAudit = returnedPayload.getAuditLogs();
    final String originalMessage = returnedAudit.getMessage();

    returnedMetadata.setSourceId("OTHER");
    returnedPayload.setResult(false);
    returnedAudit.setMessage("{}");
    returnedPayload.setAuditLogs(returnedAudit);

    assertEquals("BJF7", contract.getMetadata().getSourceId());
    assertTrue(contract.getPayload().getResult());
    assertEquals(originalMessage, contract.getPayload().getAuditLogs().getMessage());
  }

  @Test
  void nullableEnvelopeFieldsCanBeClearedAndAreOmittedFromJson() {
    QueueContract contract = event("TAPP");
    contract.setMetadata(null);
    contract.setPayload(null);

    assertNull(contract.getMetadata());
    assertNull(contract.getPayload());
    assertEquals(mapper.createObjectNode(), mapper.valueToTree(contract));
  }

  @Test
  void payloadCanClearAuditWithoutChangingTransactionResult() {
    Payload payload = event("TAPP").getPayload();

    payload.setAuditLogs(null);

    assertNull(payload.getAuditLogs());
    assertTrue(payload.getResult());
    JsonNode json = mapper.valueToTree(payload);
    assertFalse(json.has("auditLogs"));
    assertTrue(json.get("result").booleanValue());
  }

  @Test
  void metadataProtectsMutableDatesOnInputOutputAndCopy() {
    Date date = Date.from(Instant.parse("2026-10-21T12:00:00Z"));
    final long originalTime = date.getTime();
    Metadata metadata = new Metadata();
    metadata.setOriginationTimestamp(date);
    Metadata copy = new Metadata(metadata);

    date.setTime(0);
    metadata.getOriginationTimestamp().setTime(1);
    copy.getOriginationTimestamp().setTime(2);

    assertEquals(originalTime, metadata.getOriginationTimestamp().getTime());
    assertEquals(originalTime, copy.getOriginationTimestamp().getTime());
    metadata.setOriginationTimestamp(null);
    assertNull(metadata.getOriginationTimestamp());
    assertEquals(originalTime, copy.getOriginationTimestamp().getTime());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"metadata\":\"invalid\"}",
        "{\"payload\":\"invalid\"}",
        "{\"payload\":{\"auditLogs\":{\"message\":{\"app_code\":\"TAPP\"}}}}"
      })
  void rejectsInvalidProducerEnvelopeTypes(String json) {
    assertThrows(JsonProcessingException.class, () -> mapper.readValue(json, QueueContract.class));
  }
}
