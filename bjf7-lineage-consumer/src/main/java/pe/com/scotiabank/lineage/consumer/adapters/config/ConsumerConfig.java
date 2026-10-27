package pe.com.scotiabank.lineage.consumer.adapters.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.com.scotiabank.lineage.consumer.application.ports.output.DatabaseOutPort;
import pe.com.scotiabank.lineage.consumer.application.ports.output.MongoAuditOutPort;
import pe.com.scotiabank.lineage.consumer.application.service.ConsumerApplication;
import pe.com.scotiabank.lineage.consumer.application.service.ConsumerApplicationImpl;
import pe.com.scotiabank.lineage.consumer.application.service.MongoAuditService;
import pe.com.scotiabank.lineage.consumer.util.Util;

@Configuration
@EnableConfigurationProperties(MongoAuditProperties.class)
public class ConsumerConfig {

  @Bean
  public ConsumerApplication createConsumerApplication(
      DatabaseOutPort databaseOutPort, Util util, MongoAuditService mongoAuditService) {
    return new ConsumerApplicationImpl(databaseOutPort, util, mongoAuditService);
  }

  @Bean
  public MongoAuditService createMongoAuditService(
      MongoAuditOutPort mongoAuditOutPort,
      ObjectMapper objectMapper,
      MongoAuditProperties properties) {
    return new MongoAuditService(mongoAuditOutPort, objectMapper, properties.getApplicationCode());
  }
}
