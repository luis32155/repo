package pe.com.scotiabank.lineage.consumer.adapters.repository.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.event;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.eventWithMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;
import pe.com.scotiabank.lineage.common.domain.Payload;
import pe.com.scotiabank.lineage.common.domain.QueueContract;
import pe.com.scotiabank.lineage.consumer.adapters.config.MongoAuditProperties;
import pe.com.scotiabank.lineage.consumer.domain.error.ConsumerException;

class MongoAuditDocumentMapperTest {

  private final MongoAuditProperties properties = new MongoAuditProperties();
  private final MongoAuditDocumentMapper mapper =
      new MongoAuditDocumentMapper(new ObjectMapper(), properties);

  @Test
  void matchesRequestedDocumentIncludingAllNestedDetails() throws Exception {
    Document expected;
    try (InputStream input = getClass().getResourceAsStream("/mongo/expected-tapp-document.json")) {
      expected = Document.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }
    QueueContract item =
        eventWithMessage(
            expected.getString("uid"), expected.get("event_message", Document.class).toJson());
    AuditLogs audit = item.getPayload().getAuditLogs();
    audit.setTimestamp("2026-09-29T19:52:48.794Z");
    audit.setEventTimestamp("2026-09-29T19:52:48.794Z");
    audit.setAccountName("maria@tapp");
    audit.setDestinationHostName("tapp-credit-service");
    audit.setDestinationIpAddress("10.40.52.15");
    audit.setSourceHostName("tapp-payment-service");
    audit.setSourceIpAddress("10.40.52.14");
    audit.setSessionIdentifier("8a1e89d9-e288-4f75-a00c-7c33901e8ff7");

    Document actual = mapper.toDocument(withAudit(item, audit));

    assertInstanceOf(ObjectId.class, actual.remove("_id"));
    expected.remove("_id");
    assertEquals(expected, actual);
  }

  @Test
  void storesMessageAsDocumentAndPreservesDynamicFieldsAndTypes() {
    String message =
        """
        {"app_code":"TAPP","extra":{"active":true,"attempts":3},
         "channel_payload_header":{"channel_payload_detail":[{"key":"amount","value":"150.00"}]}}
        """;

    Document result = mapper.toDocument(eventWithMessage("dynamic-event", message));

    Document parsed = result.get("event_message", Document.class);
    assertEquals(Document.parse(message), parsed);
    assertEquals(true, parsed.get("extra", Document.class).getBoolean("active"));
    assertEquals(3, parsed.get("extra", Document.class).getInteger("attempts"));
  }

  @Test
  void convertsOffsetDatesToUtcAndAddsExactly365Days() {
    QueueContract item = event("TAPP");
    AuditLogs audit = item.getPayload().getAuditLogs();
    audit.setEventTimestamp("2026-09-29T14:52:48.794-05:00");
    audit.setTimestamp("2026-09-29T15:00:00-05:00");

    Document result = mapper.toDocument(withAudit(item, audit));

    assertEquals(
        Date.from(Instant.parse("2026-09-29T19:52:48.794Z")), result.getDate("event_timestamp"));
    assertEquals(
        Date.from(Instant.parse("2027-09-29T19:52:48.794Z")), result.getDate("expires_at"));
    assertEquals(Date.from(Instant.parse("2026-09-29T20:00:00Z")), result.getDate("log_timestamp"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-09-29T14:52:48.794-0500", "29/Sep/2026T19:52:48+0000"})
  void acceptsLegacyTimestampFormatsWithTimezone(String value) {
    QueueContract item = event("TAPP");
    AuditLogs audit = item.getPayload().getAuditLogs();
    audit.setTimestamp(value);

    Document result = mapper.toDocument(withAudit(item, audit));

    assertEquals(
        Instant.parse(
            value.startsWith("29/") ? "2026-09-29T19:52:48Z" : "2026-09-29T19:52:48.794Z"),
        result.getDate("log_timestamp").toInstant());
  }

  @Test
  void acceptsKnownLabelsAsWellAsTheirCodes() {
    QueueContract item = event("TAPP");
    AuditLogs audit = item.getPayload().getAuditLogs();
    audit.setClassification("AUDIT_LOG");
    audit.setPriority("HIGH");
    audit.setCategory("TERMINATED");
    audit.setAuditAction("EXECUTE");
    audit.setEventType("INFORMATION");

    Document result = mapper.toDocument(withAudit(item, audit));

    assertEquals("20", result.getString("event_priority_code"));
    assertEquals("40", result.getString("event_category_code"));
    assertEquals("20", result.getString("audit_action_code"));
    assertEquals("20", result.getString("event_type_code"));
    assertEquals("AUDIT_LOG", result.getString("event_log_classification"));
  }

  @Test
  void keepsUnmappedCodesWithoutInventingLabels() {
    QueueContract item = event("TAPP");
    AuditLogs audit = item.getPayload().getAuditLogs();
    audit.setEventType("99");

    Document result = mapper.toDocument(withAudit(item, audit));

    assertEquals("99", result.getString("event_type_code"));
    assertEquals("99", result.getString("event_type"));
  }

  @Test
  void supportsConfiguredRetentionAndEventTypeCatalog() {
    properties.setRetentionDays(30);
    properties.setEventTypeLabels(Map.of("20", "INFORMATION", "90", "CUSTOM"));
    MongoAuditDocumentMapper configured =
        new MongoAuditDocumentMapper(new ObjectMapper(), properties);
    QueueContract item = event("TAPP");
    AuditLogs audit = item.getPayload().getAuditLogs();
    audit.setEventType("90");

    Document result = configured.toDocument(withAudit(item, audit));

    assertEquals("CUSTOM", result.getString("event_type"));
    assertEquals(
        30,
        ChronoUnit.DAYS.between(
            result.getDate("event_timestamp").toInstant(),
            result.getDate("expires_at").toInstant()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-date", "2026-02-30T12:00:00Z", "2026-09-29T19:52:48"})
  void rejectsInvalidDatesAndDatesWithoutTimezone(String value) {
    QueueContract item = event("TAPP");
    AuditLogs audit = item.getPayload().getAuditLogs();
    audit.setEventTimestamp(value);

    assertThrows(ConsumerException.class, () -> mapper.toDocument(withAudit(item, audit)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid-json", "[]", "null", "123", ""})
  void rejectsMissingOrNonObjectMessages(String message) {
    assertThrows(
        ConsumerException.class,
        () -> mapper.toDocument(eventWithMessage("invalid-message", message)));
  }

  @Test
  void requiresBothTimestampsAndUid() {
    for (String field : List.of("timestamp", "event_timestamp", "uuid")) {
      QueueContract item = event("TAPP");
      AuditLogs audit = item.getPayload().getAuditLogs();
      switch (field) {
        case "timestamp" -> audit.setTimestamp(null);
        case "event_timestamp" -> audit.setEventTimestamp(null);
        case "uuid" -> audit.setUuid(null);
        default -> throw new IllegalArgumentException(field);
      }
      assertThrows(ConsumerException.class, () -> mapper.toDocument(withAudit(item, audit)));
    }
  }

  private QueueContract withAudit(QueueContract item, AuditLogs audit) {
    Payload payload = item.getPayload();
    payload.setAuditLogs(audit);
    item.setPayload(payload);
    return item;
  }
}
