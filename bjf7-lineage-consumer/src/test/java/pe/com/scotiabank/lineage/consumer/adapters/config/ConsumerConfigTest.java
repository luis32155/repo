package pe.com.scotiabank.lineage.consumer.adapters.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.test.context.support.TestPropertySourceUtils;
import pe.com.scotiabank.lineage.consumer.adapters.repository.MongoDbRepositoryAdapter;
import pe.com.scotiabank.lineage.consumer.adapters.repository.mapper.MongoAuditDocumentMapper;
import pe.com.scotiabank.lineage.consumer.application.ports.output.DatabaseOutPort;
import pe.com.scotiabank.lineage.consumer.application.ports.output.MongoAuditOutPort;
import pe.com.scotiabank.lineage.consumer.application.service.ConsumerApplication;
import pe.com.scotiabank.lineage.consumer.util.Util;

class ConsumerConfigTest {

  @Test
  void bindsMongoSettingsAndWiresConsumerWithBothPorts() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
          context, "app.mongodb.application-code=TAPP", "app.mongodb.collection=local_audit");
      context.registerBean(DatabaseOutPort.class, () -> mock(DatabaseOutPort.class));
      context.registerBean(MongoOperations.class, () -> mock(MongoOperations.class));
      context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
      context.registerBean(Util.class, () -> new Util(context.getBean(ObjectMapper.class)));
      context.register(
          ConsumerConfig.class, MongoDbRepositoryAdapter.class, MongoAuditDocumentMapper.class);
      context.refresh();

      assertNotNull(context.getBean(ConsumerApplication.class));
      assertInstanceOf(MongoDbRepositoryAdapter.class, context.getBean(MongoAuditOutPort.class));
      assertEquals("local_audit", context.getBean(MongoAuditProperties.class).getCollection());
      assertEquals(365, context.getBean(MongoAuditProperties.class).getRetentionDays());
    }
  }
}
