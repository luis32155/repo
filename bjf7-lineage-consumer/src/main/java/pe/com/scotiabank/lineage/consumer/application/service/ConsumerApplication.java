package pe.com.scotiabank.lineage.consumer.application.service;

import pe.com.scotiabank.lineage.consumer.application.ports.input.QueueInPort;
import pe.com.scotiabank.lineage.consumer.application.ports.output.DatabaseOutPort;
import pe.com.scotiabank.lineage.consumer.util.Util;

public abstract class ConsumerApplication implements QueueInPort {

  protected final DatabaseOutPort databaseOutPort;
  protected final Util util;

  public ConsumerApplication(DatabaseOutPort databaseOutPort, Util util) {
    this.databaseOutPort = databaseOutPort;
    this.util = util;
  }
}
