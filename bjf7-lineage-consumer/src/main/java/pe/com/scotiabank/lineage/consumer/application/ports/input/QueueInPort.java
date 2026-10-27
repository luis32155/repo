package pe.com.scotiabank.lineage.consumer.application.ports.input;

import java.util.List;
import pe.com.scotiabank.lineage.common.domain.QueueContract;

public interface QueueInPort {
  void process(List<QueueContract> items);
}
