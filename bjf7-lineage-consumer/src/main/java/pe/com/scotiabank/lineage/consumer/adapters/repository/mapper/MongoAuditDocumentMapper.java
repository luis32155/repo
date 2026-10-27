package pe.com.scotiabank.lineage.consumer.adapters.repository.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;
import pe.com.scotiabank.lineage.common.domain.Payload;
import pe.com.scotiabank.lineage.common.domain.QueueContract;
import pe.com.scotiabank.lineage.consumer.adapters.config.MongoAuditProperties;
import pe.com.scotiabank.lineage.consumer.domain.error.ConsumerException;

@Component
public class MongoAuditDocumentMapper {

  private static final Map<String, String> CLASSIFICATIONS =
      Map.of(
          "0A",
          "SECURITY_LOG",
          "0B",
          "AUDIT_LOG",
          "0C",
          "ERROR_LOG",
          "0D",
          "ACCESS_LOG",
          "0E",
          "TRANSACTION_LOG",
          "0F",
          "EXTERNAL_LOG");
  private static final Map<String, String> PRIORITIES =
      Map.of("00", "LOW", "10", "MEDIUM", "20", "HIGH");
  private static final Map<String, String> CATEGORIES =
      Map.of(
          "00",
          "INITIATED",
          "10",
          "SUSPENDED",
          "20",
          "INTERRUPTED",
          "30",
          "RECYCLED",
          "40",
          "TERMINATED",
          "50",
          "ASSIGNED",
          "60",
          "RELEASED");
  private static final Map<String, String> ACTIONS =
      Map.of("00", "READ", "10", "WRITE", "20", "EXECUTE", "30", "DELETE", "40", "ENUMERATE");
  private static final List<DateTimeFormatter> DATE_FORMATS =
      List.of(
          DateTimeFormatter.ISO_OFFSET_DATE_TIME,
          new DateTimeFormatterBuilder()
              .parseCaseInsensitive()
              .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
              .appendOffset("+HHMM", "Z")
              .toFormatter(Locale.ROOT)
              .withResolverStyle(ResolverStyle.STRICT),
          new DateTimeFormatterBuilder()
              .parseCaseInsensitive()
              .appendPattern("dd/MMM/uuuu'T'HH:mm:ssXX")
              .toFormatter(Locale.ENGLISH)
              .withResolverStyle(ResolverStyle.STRICT));

  private final ObjectMapper objectMapper;
  private final int retentionDays;
  private final Map<String, String> eventTypes;

  public MongoAuditDocumentMapper(ObjectMapper objectMapper, MongoAuditProperties properties) {
    this.objectMapper = objectMapper;
    this.retentionDays = properties.getRetentionDays();
    this.eventTypes = Map.copyOf(properties.getEventTypeLabels());
  }

  public Document toDocument(QueueContract item) {
    Payload payload = item == null ? null : item.getPayload();
    AuditLogs audit = payload == null ? null : payload.getAuditLogs();
    if (audit == null) {
      throw new ConsumerException("MongoDB audit persistence requires payload.auditLogs");
    }

    String uid = requireValue(audit.getUuid(), "uuid");
    Instant eventTimestamp = parseTimestamp(audit.getEventTimestamp(), "event_timestamp");
    Instant logTimestamp = parseTimestamp(audit.getTimestamp(), "timestamp");
    CodeLabel classification =
        resolve(audit.getClassification(), CLASSIFICATIONS, "classification");
    CodeLabel priority = resolve(audit.getPriority(), PRIORITIES, "priority");
    CodeLabel category = resolve(audit.getCategory(), CATEGORIES, "category");
    CodeLabel action = resolve(audit.getAuditAction(), ACTIONS, "audit_action");
    CodeLabel eventType = resolve(audit.getEventType(), eventTypes, "event_type");

    return new Document("_id", new ObjectId())
        .append("uid", uid)
        .append("account_name", audit.getAccountName())
        .append("audit_action", action.label())
        .append("audit_action_code", action.code())
        .append("destination_host_name", audit.getDestinationHostName())
        .append("destination_ip_address", audit.getDestinationIpAddress())
        .append("event_category", category.label())
        .append("event_category_code", category.code())
        .append("event_log_classification", classification.label())
        .append("event_message", parseMessage(audit.getMessage()))
        .append("event_priority", priority.label())
        .append("event_priority_code", priority.code())
        .append("event_timestamp", Date.from(eventTimestamp))
        .append("event_type", eventType.label())
        .append("event_type_code", eventType.code())
        .append("expires_at", Date.from(eventTimestamp.plus(retentionDays, ChronoUnit.DAYS)))
        .append("log_timestamp", Date.from(logTimestamp))
        .append("session_identifier", audit.getSessionIdentifier())
        .append("source_host_name", audit.getSourceHostName())
        .append("source_ip_address", audit.getSourceIpAddress());
  }

  private Document parseMessage(String value) {
    try {
      JsonNode message = objectMapper.readTree(requireValue(value, "message"));
      if (message == null || !message.isObject()) {
        throw new ConsumerException("MongoDB event_message must contain a JSON object");
      }
      return Document.parse(message.toString());
    } catch (JsonProcessingException exception) {
      throw new ConsumerException("Unable to parse MongoDB event_message as JSON", exception);
    }
  }

  private Instant parseTimestamp(String value, String field) {
    String timestamp = requireValue(value, field);
    for (DateTimeFormatter format : DATE_FORMATS) {
      try {
        return OffsetDateTime.parse(timestamp, format).toInstant();
      } catch (DateTimeParseException exception) {
        // Try the next supported timestamp format; never assume a local timezone.
      }
    }
    throw new ConsumerException("Invalid MongoDB " + field + ": a date with timezone is required");
  }

  private CodeLabel resolve(String value, Map<String, String> labels, String field) {
    String input = requireValue(value, field).trim().toUpperCase(Locale.ROOT);
    if (labels.containsKey(input)) {
      return new CodeLabel(input, labels.get(input));
    }
    for (Map.Entry<String, String> entry : labels.entrySet()) {
      if (entry.getValue().equalsIgnoreCase(input)) {
        return new CodeLabel(entry.getKey(), entry.getValue());
      }
    }
    return new CodeLabel(input, input);
  }

  private String requireValue(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new ConsumerException("MongoDB audit persistence requires payload.auditLogs." + field);
    }
    return value;
  }

  private record CodeLabel(String code, String label) {}
}
