package pe.com.scotiabank.lineage.consumer.application.ports.output;

import java.util.List;
import java.util.Map;
import java.util.Set;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;

public interface DatabaseOutPort {
  Map<String, Integer> getChannelIdsByCodes(Set<String> apmCodes, Set<String> journeyCodes);

  void initIds(List<AuditLogs> auditLogs);

  void save(List<AuditLogs> auditLogs);
}
