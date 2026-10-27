package pe.com.scotiabank.lineage.consumer.application.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import pe.com.scotiabank.lineage.common.domain.AuditLogs;
import pe.com.scotiabank.lineage.common.domain.Payload;
import pe.com.scotiabank.lineage.common.domain.QueueContract;
import pe.com.scotiabank.lineage.consumer.application.ports.output.DatabaseOutPort;
import pe.com.scotiabank.lineage.consumer.util.Util;

public class ConsumerApplicationImpl extends ConsumerApplication {

  private final MongoAuditService mongoAuditService;

  public ConsumerApplicationImpl(
      DatabaseOutPort databaseOutPort, Util util, MongoAuditService mongoAuditService) {
    super(databaseOutPort, util);
    this.mongoAuditService = mongoAuditService;
  }

  public void process(List<QueueContract> items) {
    mongoAuditService.saveTappEvents(items);

    Set<String> apmCodes = new HashSet<>();
    Set<String> journeyCodes = new HashSet<>();
    List<AuditLogs> auditLogs = new ArrayList<>();

    items.stream()
        .filter(Objects::nonNull)
        .map(QueueContract::getPayload)
        .filter(Objects::nonNull)
        .map(Payload::getAuditLogs)
        .filter(Objects::nonNull)
        .forEach(auditLog -> util.initialize(auditLog, apmCodes, journeyCodes, auditLogs));

    if (auditLogs.isEmpty()) {
      return;
    }

    Map<String, Integer> channelIds = databaseOutPort.getChannelIdsByCodes(apmCodes, journeyCodes);
    List<AuditLogs> filteredAuditLogs = util.filterAndInitByJourneyId(auditLogs, channelIds);
    if (filteredAuditLogs.isEmpty()) {
      return;
    }

    databaseOutPort.initIds(filteredAuditLogs);
    databaseOutPort.save(filteredAuditLogs);
  }
}
