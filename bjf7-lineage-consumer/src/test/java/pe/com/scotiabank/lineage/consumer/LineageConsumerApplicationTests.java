package pe.com.scotiabank.lineage.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.test.util.ReflectionTestUtils;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;
import pe.com.scotiabank.lineage.common.domain.Payload;
import pe.com.scotiabank.lineage.common.domain.QueueContract;
import pe.com.scotiabank.lineage.consumer.adapters.kafka.KafkaQueueInPort;
import pe.com.scotiabank.lineage.consumer.adapters.repository.SqlServerRepositoryAdapter;
import pe.com.scotiabank.lineage.consumer.application.service.ConsumerApplicationImpl;
import pe.com.scotiabank.lineage.consumer.application.service.MongoAuditService;
import pe.com.scotiabank.lineage.consumer.util.Util;

@ExtendWith(MockitoExtension.class)
class LineageConsumerApplicationTests {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private MongoAuditService mongoAuditService;

  private KafkaQueueInPort kafkaQueueInPort;

  @BeforeEach
  void before() {
    SqlServerRepositoryAdapter sqlServerRepositoryAdapter = new SqlServerRepositoryAdapter();
    ReflectionTestUtils.setField(sqlServerRepositoryAdapter, "jdbcTemplate", jdbcTemplate);
    ReflectionTestUtils.setField(sqlServerRepositoryAdapter, "defaultSchema", "default");
    ReflectionTestUtils.setField(sqlServerRepositoryAdapter, "batchSize", 100);
    ConsumerApplicationImpl consumerApplication =
        new ConsumerApplicationImpl(
            sqlServerRepositoryAdapter, new Util(objectMapper), mongoAuditService);
    kafkaQueueInPort = new KafkaQueueInPort(consumerApplication);
  }

  @Test
  void happyPath() {
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
        .thenReturn(List.of(Map.of("MAP_KEY", "BGGG-DO", "ID", (short) 1)));
    when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(101L);
    List<ConsumerRecord<String, QueueContract>> records =
        List.of(
            getConsumerRecord("QueueContract-BGGG.json"),
            getConsumerRecord("QueueContract-BFNB.json"));

    kafkaQueueInPort.receivedMessage(records);

    ArgumentCaptor<Collection<AuditLogs>> saved = ArgumentCaptor.forClass(Collection.class);
    verify(jdbcTemplate, times(2))
        .batchUpdate(
            anyString(), saved.capture(), eq(100), any(ParameterizedPreparedStatementSetter.class));
    assertEquals(1, saved.getValue().size());
    AuditLogs audit = saved.getValue().iterator().next();
    assertEquals("BGGG", audit.getAppCode());
    assertEquals("DO", audit.getAppOperationCode());
    assertEquals(1, audit.getJourneyId());
    assertEquals(101L, audit.getId());
    verify(mongoAuditService).saveTappEvents(any(List.class));
  }

  @Test
  void emptyPath() {
    ConsumerRecord<String, QueueContract> record =
        new ConsumerRecord<>("mock-topic", 1, 1, "key", null);

    kafkaQueueInPort.receivedMessage(List.of(record));

    verifyNoInteractions(jdbcTemplate, mongoAuditService);
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenEventTimestampIsNull() {
    assertInvalidEventTimestamp(null);
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenEventTimestampHasInvalidFormat() {
    assertInvalidEventTimestamp("2024-99-99");
  }

  private void assertInvalidEventTimestamp(String timestamp) {
    when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
        .thenReturn(List.of(Map.of("MAP_KEY", "BGGG-DO", "ID", (short) 1)));
    QueueContract queueContract = getQueueContract("QueueContract-BGGG.json");
    Payload payload = queueContract.getPayload();
    AuditLogs auditLogs = payload.getAuditLogs();
    auditLogs.setEventTimestamp(timestamp);
    payload.setAuditLogs(auditLogs);
    queueContract.setPayload(payload);
    ConsumerRecord<String, QueueContract> record =
        new ConsumerRecord<>("mock-topic", 1, 1, "key", queueContract);

    assertThrows(
        IllegalArgumentException.class, () -> kafkaQueueInPort.receivedMessage(List.of(record)));

    verify(jdbcTemplate, never())
        .batchUpdate(
            anyString(),
            any(Collection.class),
            eq(100),
            any(ParameterizedPreparedStatementSetter.class));
  }

  private ConsumerRecord<String, QueueContract> getConsumerRecord(String filename) {
    return new ConsumerRecord<>("mock-topic", 1, 1, "key", getQueueContract(filename));
  }

  private QueueContract getQueueContract(String filename) {
    try (InputStream input =
        Objects.requireNonNull(
            getClass().getClassLoader().getResourceAsStream(filename),
            "Missing test resource: " + filename)) {
      return objectMapper.readValue(input, QueueContract.class);
    } catch (IOException exception) {
      throw new UncheckedIOException("Unable to read test resource: " + filename, exception);
    }
  }
}
