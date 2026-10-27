package pe.com.scotiabank.lineage.consumer.adapters.kafka;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.stereotype.Service;
import pe.com.scotiabank.lineage.common.domain.QueueContract;
import pe.com.scotiabank.lineage.consumer.application.service.ConsumerApplication;

@Slf4j
@Service
public class KafkaQueueInPort {

  private static final LogAccessor DESERIALIZATION_LOGGER = new LogAccessor(KafkaQueueInPort.class);

  private final ConsumerApplication consumerApplication;

  public KafkaQueueInPort(ConsumerApplication consumerApplication) {
    this.consumerApplication = consumerApplication;
  }

  @KafkaListener(id = "${app.kafka.properties.group-id}", topics = "${app.kafka.properties.topic}")
  public void receivedMessage(List<ConsumerRecord<String, QueueContract>> messages) {
    log.info("Registros Kafka recibidos: {}", messages.size());

    List<QueueContract> items = new ArrayList<>();
    for (ConsumerRecord<String, QueueContract> record : messages) {
      if (record.value() == null) {
        DeserializationException exception =
            SerializationUtils.getExceptionFromHeader(
                record,
                SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER,
                DESERIALIZATION_LOGGER);
        if (exception != null) {
          log.error(
              "Error de deserializacion. topic={}, partition={}, offset={}",
              record.topic(),
              record.partition(),
              record.offset(),
              exception);
          throw new KafkaException(
              "No se pudo convertir el registro Kafka a QueueContract", exception);
        }
        log.warn(
            "Registro con valor null y sin error de deserializacion."
                + " topic={}, partition={}, offset={}",
            record.topic(),
            record.partition(),
            record.offset());
        continue;
      }
      items.add(record.value());
    }

    if (items.isEmpty()) {
      log.warn("El lote no contiene eventos de auditoria para procesar");
      return;
    }

    log.info("Procesando {} eventos de auditoria", items.size());
    consumerApplication.process(items);
    log.info("Procesamiento del lote finalizado");
  }
}
