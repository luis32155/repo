package pe.com.scotiabank.lineage.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;
import pe.com.scotiabank.lineage.consumer.adapters.repository.SqlServerRepositoryAdapter;

@ExtendWith(MockitoExtension.class)
class SqlServerRepositoryAdapterTest {

  @InjectMocks private SqlServerRepositoryAdapter sqlServerRepositoryAdapter;

  @Test
  void setQueryValues() throws Exception {
    AuditLogs auditLogs = new AuditLogs();
    auditLogs.setJourneyId(1);
    auditLogs.setId(1L);
    PreparedStatement ps = mock(PreparedStatement.class);
    Method method =
        SqlServerRepositoryAdapter.class.getDeclaredMethod(
            "setQueryValues", PreparedStatement.class, AuditLogs.class);
    method.setAccessible(true);

    method.invoke(sqlServerRepositoryAdapter, ps, auditLogs);

    verify(ps).setString(1, null);
    verify(ps).setLong(2, 1L);
    verify(ps).setInt(5, 1);
  }

  @Test
  void setQueryDetailValues() throws Exception {
    AuditLogs auditLogs = new AuditLogs();
    auditLogs.setId(1L);
    PreparedStatement ps = mock(PreparedStatement.class);
    Method method =
        SqlServerRepositoryAdapter.class.getDeclaredMethod(
            "setQueryDetailValues", PreparedStatement.class, AuditLogs.class);
    method.setAccessible(true);

    method.invoke(sqlServerRepositoryAdapter, ps, auditLogs);

    verify(ps).setString(1, null);
    verify(ps).setLong(2, 1L);
    verify(ps).setString(4, null);
  }
}
