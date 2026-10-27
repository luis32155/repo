package pe.com.scotiabank.lineage.consumer.application.ports.output;

import java.util.List;
import pe.com.scotiabank.lineage.common.domain.QueueContract;

public interface MongoAuditOutPort {

  void save(List<QueueContract> items);
}
