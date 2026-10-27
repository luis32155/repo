package pe.com.scotiabank.lineage.consumer.adapters.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;
import pe.com.scotiabank.lineage.consumer.application.ports.output.DatabaseOutPort;

@Slf4j
@Repository
public class SqlServerRepositoryAdapter implements DatabaseOutPort {
  public static final String SEQUENCE_BASE_NAME = "SEQ_EVENT_LOG_AUDIT_";

  @Value("${spring.jpa.hibernate.default-schema}")
  private String defaultSchema;

  @Value("${spring.jpa.properties.hibernate.jdbc.batch_size}")
  private Integer batchSize;

  private static final DateTimeFormatter SEQUENCE_SUFFIX_FORMAT =
      DateTimeFormatter.ofPattern("yyyy_MM");
  @Autowired private JdbcTemplate jdbcTemplate;

  @Transactional
  public Map<String, Integer> getChannelIdsByCodes(Set<String> apmCodes, Set<String> journeyCodes) {
    StringBuilder sql = new StringBuilder();
    sql.append("SELECT CONCAT(a.APM_CODE, '-', cs.JOURNEY_CODE) AS MAP_KEY, cs.ID ");
    sql.append("FROM ").append(defaultSchema).append(".APPLICATION a ");
    sql.append("INNER JOIN (VALUES ");
    addParamNames(sql, apmCodes.size());
    sql.append(") AS A_CODE(APM_CODE) ON (a.APM_CODE = A_CODE.APM_CODE) ");
    sql.append("INNER JOIN ")
        .append(defaultSchema)
        .append(".CHANNEL_SUSCRIBED cs ON (a.ID = cs.APPLICATION_ID) ");
    sql.append("INNER JOIN (VALUES ");
    addParamNames(sql, journeyCodes.size());
    sql.append(") AS CS_CODE(JOURNEY_CODE) ON (cs.JOURNEY_CODE = CS_CODE.JOURNEY_CODE) ");
    sql.append("WHERE a.STATUS = 1 ");
    sql.append("AND cs.STATUS = 1");
    List<String> paramList = new ArrayList<>();
    paramList.addAll(apmCodes);
    paramList.addAll(journeyCodes);
    List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), paramList.toArray());
    Map<String, Integer> out = new HashMap<>();
    for (Map<String, Object> row : rows) {
      out.put(row.get("MAP_KEY").toString(), ((Short) row.get("ID")).intValue());
    }
    return out;
  }

  public void addParamNames(StringBuilder sql, int size) {
    for (int i = 0; i < size; i++) {
      sql.append("(?)");
      if (i < size - 1) {
        sql.append(",");
      }
    }
  }

  @Transactional
  public void initIds(List<AuditLogs> auditLogs) {
    for (AuditLogs auditLog : auditLogs) {
      String sequenceName = getSequenceName(auditLog);
      String sql = "SELECT NEXT VALUE FOR " + defaultSchema + "." + sequenceName;
      Long id = jdbcTemplate.queryForObject(sql, Long.class);
      auditLog.setId(id);
    }
  }

  @Transactional
  public void save(List<AuditLogs> auditLogs) {
    jdbcTemplate.batchUpdate(getInsertQuery(), auditLogs, batchSize, this::setQueryValues);
    jdbcTemplate.batchUpdate(
        getInsertDetailQuery(), auditLogs, batchSize, this::setQueryDetailValues);
    log.debug("Saved {} items", auditLogs.size());
  }

  private void setQueryValues(PreparedStatement ps, AuditLogs auditLog) throws SQLException {
    ps.setString(1, auditLog.getEventTimestamp());
    ps.setLong(2, auditLog.getId());
    ps.setString(3, auditLog.getTypeId());
    ps.setString(4, auditLog.getNumberId());
    ps.setInt(5, auditLog.getJourneyId());
    ps.setString(6, auditLog.getAccountName());
  }

  private void setQueryDetailValues(PreparedStatement ps, AuditLogs auditLog) throws SQLException {
    ps.setString(1, auditLog.getEventTimestamp());
    ps.setLong(2, auditLog.getId());
    ps.setString(3, auditLog.getDeviceId());
    ps.setString(4, auditLog.getUuid());
    ps.setString(5, auditLog.getTimestamp());
    ps.setString(6, auditLog.getClassification());
    ps.setString(7, auditLog.getEventType());
    ps.setString(8, auditLog.getPriority());
    ps.setString(9, auditLog.getCategory());
    ps.setString(10, auditLog.getSourceHostName());
    ps.setString(11, auditLog.getSourceIpAddress());
    ps.setString(12, auditLog.getDestinationHostName());
    ps.setString(13, auditLog.getDestinationIpAddress());
    ps.setString(14, auditLog.getSessionIdentifier());
    ps.setString(15, auditLog.getAuditAction());
    ps.setObject(16, auditLog.getMessage(), Types.VARCHAR);
  }

  private String getInsertQuery() {
    StringBuilder sql = new StringBuilder();
    sql.append("INSERT INTO ").append(defaultSchema).append(".EVENT_LOG_AUDIT (");
    sql.append("EVENT_TIMESTAMP, ");
    sql.append("ID, ");
    sql.append("TYPE_ID, ");
    sql.append("NUMBER_ID, ");
    sql.append("JOURNEY_ID, ");
    sql.append("ACCOUNT_NAME ");
    sql.append(") VALUES (");
    sql.append("?, "); // :EVENT_TIMESTAMP
    sql.append("?, "); // :ID
    sql.append("?, "); // :TYPE_ID
    sql.append("?, "); // :NUMBER_ID
    sql.append("?, "); // :JOURNEY_ID
    sql.append("? "); // :ACCOUNT_NAME
    sql.append(")");
    return sql.toString();
  }

  private String getInsertDetailQuery() {
    StringBuilder sql = new StringBuilder();
    sql.append("INSERT INTO ").append(defaultSchema).append(".EVENT_LOG_AUDIT_DETAIL (");
    sql.append("EVENT_TIMESTAMP, ");
    sql.append("ID, ");
    sql.append("DEVICE_ID, ");
    sql.append("UID, ");
    sql.append("LOG_TIMESTAMP, ");
    sql.append("EVENT_LOG_CLASSIFICATION, ");
    sql.append("EVENT_TYPE, ");
    sql.append("EVENT_PRIORITY, ");
    sql.append("EVENT_CATEGORY, ");
    sql.append("SOURCE_HOST_NAME, ");
    sql.append("SOURCE_IP_ADDRESS, ");
    sql.append("DESTINATION_HOST_NAME, ");
    sql.append("DESTINATION_IP_ADDRESS, ");
    sql.append("SESSION_IDENTIFIER, ");
    sql.append("AUDIT_ACTION, ");
    sql.append("EVENT_MESSAGE, ");
    sql.append("CREATED_BY, ");
    sql.append("CREATED_DATE");
    sql.append(") VALUES (");
    sql.append("?, "); // :EVENT_TIMESTAMP
    sql.append("?, "); // :ID
    sql.append("?, "); // :DEVICE_ID
    sql.append("CAST(? AS UNIQUEIDENTIFIER), ");
    sql.append("?, "); // :LOG_TIMESTAMP
    sql.append("?, "); // :EVENT_LOG_CLASSIFICATION
    sql.append("?, "); // :EVENT_TYPE
    sql.append("?, "); // :EVENT_PRIORITY
    sql.append("?, "); // :EVENT_CATEGORY
    sql.append("?, "); // :SOURCE_HOST_NAME
    sql.append("?, "); // :SOURCE_IP_ADDRESS
    sql.append("?, "); // :DESTINATION_HOST_NAME
    sql.append("?, "); // :DESTINATION_IP_ADDRESS
    sql.append("?, "); // :SESSION_IDENTIFIER
    sql.append("?, "); // :AUDIT_ACTION
    sql.append("COMPRESS(CAST(? AS VARCHAR(MAX))), "); // :EVENT_MESSAGE
    sql.append("USER_NAME(), ");
    sql.append("GETDATE()");
    sql.append(")");
    return sql.toString();
  }

  private String getSequenceSuffix(AuditLogs auditLog) {
    String timestamp = auditLog.getEventTimestamp();
    if (timestamp == null || timestamp.isBlank()) {
      throw new IllegalArgumentException("eventTimestamp is required.");
    }
    try {
      LocalDateTime dateTime =
          LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
      return dateTime.format(SEQUENCE_SUFFIX_FORMAT);
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("Invalid eventTimestamp format.", exception);
    }
  }

  private String getSequenceName(AuditLogs auditLog) {
    return SEQUENCE_BASE_NAME + getSequenceSuffix(auditLog);
  }
}
