package pe.com.scotiabank.lineage.consumer.adapters.repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pe.com.scotiabank.lineage.consumer.support.AuditEventFixtures.event;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.test.util.ReflectionTestUtils;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;

class SqlServerRepositoryAdapterTest {

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final SqlServerRepositoryAdapter adapter = new SqlServerRepositoryAdapter();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(adapter, "jdbcTemplate", jdbc);
    ReflectionTestUtils.setField(adapter, "defaultSchema", "audit");
    ReflectionTestUtils.setField(adapter, "batchSize", 2);
  }

  @Test
  void bindsApplicationAndJourneyCodesAsParametersAndReturnsChannelIds() {
    Set<String> apps = new LinkedHashSet<>(List.of("TAPP", "OTHER"));
    Set<String> journeys = new LinkedHashSet<>(List.of("LOGIN", "PAYMENT"));
    when(jdbc.queryForList(anyString(), any(Object[].class)))
        .thenReturn(
            List.of(
                Map.of("MAP_KEY", "TAPP-LOGIN", "ID", (short) 7),
                Map.of("MAP_KEY", "OTHER-PAYMENT", "ID", (short) 8)));

    final Map<String, Integer> result = adapter.getChannelIdsByCodes(apps, journeys);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
    verify(jdbc).queryForList(sql.capture(), parameters.capture());
    assertTrue(sql.getValue().contains("FROM audit.APPLICATION a"));
    assertTrue(sql.getValue().contains("audit.CHANNEL_SUSCRIBED"));
    assertTrue(sql.getValue().contains("INNER JOIN (VALUES (?),(?)) AS A_CODE"));
    assertTrue(sql.getValue().contains("a.STATUS = 1 AND cs.STATUS = 1"));
    assertArrayEquals(new Object[] {"TAPP", "OTHER", "LOGIN", "PAYMENT"}, parameters.getValue());
    assertEquals(Map.of("TAPP-LOGIN", 7, "OTHER-PAYMENT", 8), result);
  }

  @Test
  void returnsEmptyCatalogWhenNoActiveChannelsAreFound() {
    when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

    assertTrue(adapter.getChannelIdsByCodes(Set.of("TAPP"), Set.of("UNKNOWN")).isEmpty());
  }

  @Test
  void propagatesCatalogQueryFailures() {
    DataAccessResourceFailureException failure = new DataAccessResourceFailureException("offline");
    when(jdbc.queryForList(anyString(), any(Object[].class))).thenThrow(failure);

    assertSame(
        failure,
        assertThrows(
            DataAccessResourceFailureException.class,
            () -> adapter.getChannelIdsByCodes(Set.of("TAPP"), Set.of("LOGIN"))));
  }

  @ParameterizedTest
  @CsvSource({
    "2026-10-21T12:00:00-05:00,2026_10",
    "2026-01-01T00:15:00+14:00,2026_01",
    "2026-12-31T23:30:00-05:00,2026_12"
  })
  void selectsSequenceForEventMonthAndAssignsId(String timestamp, String suffix) {
    AuditLogs audit = event("TAPP").getPayload().getAuditLogs();
    audit.setEventTimestamp(timestamp);
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);

    adapter.initIds(List.of(audit));

    verify(jdbc)
        .queryForObject("SELECT NEXT VALUE FOR audit.SEQ_EVENT_LOG_AUDIT_" + suffix, Long.class);
    assertEquals(42L, audit.getId());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "invalid-date", "2026-10-21T12:00:00"})
  void rejectsMissingOrInvalidEventTimestampBeforeRequestingSequence(String timestamp) {
    AuditLogs audit = event("TAPP").getPayload().getAuditLogs();
    audit.setEventTimestamp(timestamp);

    assertThrows(IllegalArgumentException.class, () -> adapter.initIds(List.of(audit)));
    verifyNoInteractions(jdbc);
  }

  @Test
  void doesNotRequestSequenceForEmptyBatch() {
    adapter.initIds(List.of());

    verifyNoInteractions(jdbc);
  }

  @Test
  void propagatesSequenceFailuresWithoutInventingAnId() {
    AuditLogs audit = event("TAPP").getPayload().getAuditLogs();
    DataAccessResourceFailureException failure = new DataAccessResourceFailureException("sequence");
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenThrow(failure);

    assertSame(
        failure,
        assertThrows(
            DataAccessResourceFailureException.class, () -> adapter.initIds(List.of(audit))));
    assertNull(audit.getId());
  }

  @Test
  void writesBothTablesWithConfiguredBatchSizeAndCorrectBoundValues() throws Exception {
    AuditLogs audit = sqlAudit();
    PreparedStatement header = mock(PreparedStatement.class);
    PreparedStatement detail = mock(PreparedStatement.class);
    List<String> statements = new ArrayList<>();
    doAnswer(
            invocation -> {
              String sql = invocation.getArgument(0);
              List<AuditLogs> audits = invocation.getArgument(1);
              statements.add(sql);
              assertEquals(List.of(audit), audits);
              assertEquals(2, (Integer) invocation.getArgument(2));
              ParameterizedPreparedStatementSetter<AuditLogs> setter = invocation.getArgument(3);
              setter.setValues(sql.contains("EVENT_LOG_AUDIT_DETAIL") ? detail : header, audit);
              return new int[][] {{1}};
            })
        .when(jdbc)
        .batchUpdate(
            anyString(),
            any(List.class),
            anyInt(),
            any(ParameterizedPreparedStatementSetter.class));

    adapter.save(List.of(audit));

    assertEquals(2, statements.size());
    assertTrue(statements.get(0).startsWith("INSERT INTO audit.EVENT_LOG_AUDIT ("));
    assertTrue(statements.get(1).startsWith("INSERT INTO audit.EVENT_LOG_AUDIT_DETAIL ("));
    assertTrue(statements.get(1).contains("CAST(? AS UNIQUEIDENTIFIER)"));
    assertTrue(statements.get(1).contains("COMPRESS(CAST(? AS VARCHAR(MAX)))"));
    assertTrue(statements.get(1).contains("USER_NAME(), GETDATE()"));
    verify(header).setString(1, audit.getEventTimestamp());
    verify(header).setLong(2, 42L);
    verify(header).setString(3, "1");
    verify(header).setString(4, "12345678");
    verify(header).setInt(5, 7);
    verify(header).setString(6, audit.getAccountName());
    verify(detail).setString(1, audit.getEventTimestamp());
    verify(detail).setLong(2, 42L);
    verify(detail).setString(3, "test-device");
    verify(detail).setString(4, audit.getUuid());
    verify(detail).setString(5, audit.getTimestamp());
    verify(detail).setString(6, audit.getClassification());
    verify(detail).setString(7, audit.getEventType());
    verify(detail).setString(8, audit.getPriority());
    verify(detail).setString(9, audit.getCategory());
    verify(detail).setString(10, "source");
    verify(detail).setString(11, "10.0.0.1");
    verify(detail).setString(12, "destination");
    verify(detail).setString(13, "10.0.0.2");
    verify(detail).setString(14, "session");
    verify(detail).setString(15, audit.getAuditAction());
    verify(detail).setObject(16, audit.getMessage(), Types.VARCHAR);
  }

  @Test
  void headerInsertFailurePreventsDetailInsert() {
    DataAccessResourceFailureException failure = new DataAccessResourceFailureException("header");
    doThrow(failure)
        .when(jdbc)
        .batchUpdate(
            anyString(),
            any(List.class),
            anyInt(),
            any(ParameterizedPreparedStatementSetter.class));

    assertSame(
        failure,
        assertThrows(
            DataAccessResourceFailureException.class, () -> adapter.save(List.of(sqlAudit()))));
    verify(jdbc, times(1))
        .batchUpdate(
            anyString(),
            any(List.class),
            anyInt(),
            any(ParameterizedPreparedStatementSetter.class));
  }

  @Test
  void detailInsertFailureIsPropagated() {
    DataAccessResourceFailureException failure = new DataAccessResourceFailureException("detail");
    doAnswer(
            invocation -> {
              String sql = invocation.getArgument(0);
              if (sql.contains("EVENT_LOG_AUDIT_DETAIL")) {
                throw failure;
              }
              return new int[][] {{1}};
            })
        .when(jdbc)
        .batchUpdate(
            anyString(),
            any(List.class),
            anyInt(),
            any(ParameterizedPreparedStatementSetter.class));

    assertSame(
        failure,
        assertThrows(
            DataAccessResourceFailureException.class, () -> adapter.save(List.of(sqlAudit()))));
    verify(jdbc, times(2))
        .batchUpdate(
            anyString(),
            any(List.class),
            anyInt(),
            any(ParameterizedPreparedStatementSetter.class));
  }

  @ParameterizedTest
  @CsvSource(
      value = {"0|", "1|(?)", "3|(?),(?),(?)"},
      delimiter = '|')
  void generatesOnlyTheRequestedNumberOfPlaceholders(int size, String expected) {
    StringBuilder sql = new StringBuilder();

    adapter.addParamNames(sql, size);

    assertEquals(expected == null ? "" : expected, sql.toString());
  }

  private AuditLogs sqlAudit() {
    AuditLogs audit = event("TAPP").getPayload().getAuditLogs();
    audit.setId(42L);
    audit.setTypeId("1");
    audit.setNumberId("12345678");
    audit.setJourneyId(7);
    audit.setDeviceId("test-device");
    audit.setSourceHostName("source");
    audit.setSourceIpAddress("10.0.0.1");
    audit.setDestinationHostName("destination");
    audit.setDestinationIpAddress("10.0.0.2");
    audit.setSessionIdentifier("session");
    return audit;
  }
}
