package pe.com.scotiabank.lineage.consumer.adapters.config;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import pe.com.scotiabank.lineage.common.domain.QueueContract;

@Slf4j
@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
@EnableKafka
public class KafkaConfiguration {
  private final KafkaProperties kafkaProperties;

  @SuppressFBWarnings("EI_EXPOSE_REP2")
  public KafkaConfiguration(KafkaProperties kafkaProperties) {
    this.kafkaProperties = kafkaProperties;
  }

  @Bean
  public ConsumerFactory<String, QueueContract> consumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(
        ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaJsonSchemaDeserializer.class);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.getAutoOffsetReset());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getGroupId());
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaProperties.getMaxPollRecords());
    props.put("json.value.type", QueueContract.class);
    props.put(SaslConfigs.SASL_MECHANISM, kafkaProperties.getSaslMechanism());
    props.put(SaslConfigs.SASL_JAAS_CONFIG, getSaslJaasConfig());
    props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, kafkaProperties.getSecurityProtocol());
    if (Boolean.TRUE.equals(kafkaProperties.getSchemaRegistryUseProxy())) {
      props.put("proxy.host", kafkaProperties.getSchemaRegistryProxyHost());
      props.put("proxy.port", kafkaProperties.getSchemaRegistryProxyPort());
    }
    props.put("auto.register.schemas", kafkaProperties.getAutoRegisterSchemas());
    props.put("schema.registry.url", kafkaProperties.getSchemaRegistryUrl());
    props.put(
        "basic.auth.credentials.source",
        kafkaProperties.getSchemaRegistryBasicAuthCredentialsSource());
    props.put("basic.auth.user.info", kafkaProperties.getSchemaRegistryBasicAuthUserInfo());
    props.put(
        "ssl.endpoint.identification.algorithm",
        kafkaProperties.getSslEndpointIdentificationAlgorithm());
    props.put("sasl.login.retry.backoff.max.ms", kafkaProperties.getSaslLoginRetryBackoffMaxMs());
    props.put("sasl.login.retry.backoff.ms", kafkaProperties.getSaslLoginRetryBackoffMs());
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, QueueContract>
      kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, QueueContract> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    factory.setBatchListener(true);
    factory.setCommonErrorHandler(new CommonContainerStoppingErrorHandler());
    return factory;
  }

  private String getSaslJaasConfig() {
    StringBuilder builder = new StringBuilder();
    builder.append("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"");
    builder.append(kafkaProperties.getKafkaApiKey());
    builder.append("\" password=\"");
    builder.append(kafkaProperties.getKafkaApiSecret());
    builder.append("\";");
    return builder.toString();
  }
}
