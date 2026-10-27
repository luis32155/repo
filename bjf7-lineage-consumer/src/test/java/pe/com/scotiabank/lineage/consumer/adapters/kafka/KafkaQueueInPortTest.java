package pe.com.scotiabank.lineage.consumer.adapters.kafka;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.event;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import pe.com.scotiabank.lineage.common.domain.QueueContract;
import pe.com.scotiabank.lineage.consumer.application.service.ConsumerApplication;

class KafkaQueueInPortTest {

  private static final String TOPIC = "unit-audit";

  private final ConsumerApplication application = mock(ConsumerApplication.class);
  private final KafkaQueueInPort listener = new KafkaQueueInPort(application);

  @Test
  void sendsValidEventsInTheirOriginalOrderToApplication() {
    QueueContract tapp = event("TAPP");
    QueueContract other = event("OTHER");

    listener.receivedMessage(List.of(record(1, tapp), record(2, other)));

    verify(application).process(List.of(tapp, other));
  }

  @Test
  void emptyBatchDoesNotCallApplication() {
    listener.receivedMessage(List.of());

    verifyNoInteractions(application);
  }

  @Test
  void skipsNullValuesWithoutDeserializationErrorsAndKeepsValidEvents() {
    QueueContract audit = event("TAPP");

    listener.receivedMessage(List.of(record(1, null), record(2, audit)));

    verify(application).process(List.of(audit));
  }

  @Test
  void batchContainingOnlyNullValuesDoesNotCallApplication() {
    assertDoesNotThrow(() -> listener.receivedMessage(List.of(record(1, null))));

    verifyNoInteractions(application);
  }

  @Test
  void exposesSchemaRegistryAuthenticationFailureStoredInKafkaHeaders() {
    ConsumerRecord<String, QueueContract> invalid = authenticationFailureRecord(612194);

    KafkaException failure =
        assertThrows(KafkaException.class, () -> listener.receivedMessage(List.of(invalid)));

    DeserializationException deserialization =
        assertInstanceOf(DeserializationException.class, failure.getCause());
    AuthenticationException authentication =
        assertInstanceOf(AuthenticationException.class, deserialization.getCause());
    assertTrue(authentication.getMessage().contains("401"));
    verifyNoInteractions(application);
  }

  @Test
  void validatesWholeBatchBeforeSendingAnyValidEventToPersistence() {
    List<ConsumerRecord<String, QueueContract>> batch =
        List.of(
            record(1, event("TAPP")), authenticationFailureRecord(2), record(3, event("OTHER")));

    assertThrows(KafkaException.class, () -> listener.receivedMessage(batch));

    verifyNoInteractions(application);
  }

  @Test
  void propagatesApplicationFailureToContainerErrorHandler() {
    QueueContract audit = event("TAPP");
    IllegalStateException failure = new IllegalStateException("database offline");
    doThrow(failure).when(application).process(List.of(audit));

    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () -> listener.receivedMessage(List.of(record(1, audit)))));
  }

  private ConsumerRecord<String, QueueContract> record(long offset, QueueContract value) {
    return new ConsumerRecord<>(TOPIC, 2, offset, "unit-key", value);
  }

  private ConsumerRecord<String, QueueContract> authenticationFailureRecord(long offset) {
    Headers headers = new RecordHeaders();
    Deserializer<QueueContract> delegate =
        new Deserializer<>() {
          @Override
          public QueueContract deserialize(String topic, byte[] data) {
            throw new AuthenticationException("Schema Registry Unauthorized; error code: 401");
          }
        };
    byte[] data = "unit-record".getBytes(StandardCharsets.UTF_8);
    QueueContract value;
    try (ErrorHandlingDeserializer<QueueContract> deserializer =
        new ErrorHandlingDeserializer<>(delegate)) {
      value = deserializer.deserialize(TOPIC, headers, data);
    }
    return new ConsumerRecord<>(
        TOPIC,
        2,
        offset,
        ConsumerRecord.NO_TIMESTAMP,
        TimestampType.NO_TIMESTAMP_TYPE,
        0,
        data.length,
        "unit-key",
        value,
        headers,
        Optional.empty());
  }
}
