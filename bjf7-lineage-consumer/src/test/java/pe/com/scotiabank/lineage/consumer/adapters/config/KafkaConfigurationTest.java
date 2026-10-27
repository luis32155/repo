package pe.com.scotiabank.lineage.consumer.adapters.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializerConfig;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import pe.com.scotiabank.lineage.common.domain.QueueContract;

class KafkaConfigurationTest {

  @Test
  void configuresKafkaAndSchemaRegistryCredentialsForTheirRespectiveClients() {
    KafkaConfiguration configuration = new KafkaConfiguration(properties(false));

    Map<String, Object> settings = configuration.consumerFactory().getConfigurationProperties();

    assertEquals("localhost:9092", settings.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
    assertEquals("unit-group", settings.get(ConsumerConfig.GROUP_ID_CONFIG));
    assertEquals("earliest", settings.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
    assertEquals("500", settings.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
    assertEquals(
        StringDeserializer.class, settings.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
    assertEquals(
        ErrorHandlingDeserializer.class,
        settings.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
    assertEquals(
        KafkaJsonSchemaDeserializer.class,
        settings.get(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS));
    assertEquals(QueueContract.class, settings.get("json.value.type"));
    assertEquals("SASL_SSL", settings.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
    assertEquals("PLAIN", settings.get(SaslConfigs.SASL_MECHANISM));
    assertEquals(
        "org.apache.kafka.common.security.plain.PlainLoginModule required "
            + "username=\"unit-kafka-key\" password=\"unit-kafka-secret\";",
        settings.get(SaslConfigs.SASL_JAAS_CONFIG));
    assertEquals("USER_INFO", settings.get("basic.auth.credentials.source"));
    assertEquals("unit-registry-key:unit-registry-secret", settings.get("basic.auth.user.info"));
    assertFalse(settings.containsKey("schema.registry.basic.auth.credentials.source"));
    assertFalse(settings.containsKey("schema.registry.basic.auth.user.info"));
    assertEquals("https", settings.get("ssl.endpoint.identification.algorithm"));
    assertEquals("400000", settings.get("sasl.login.retry.backoff.max.ms"));
    assertEquals("200000", settings.get("sasl.login.retry.backoff.ms"));

    KafkaJsonSchemaDeserializerConfig registryConfig =
        new KafkaJsonSchemaDeserializerConfig(settings);
    assertEquals("USER_INFO", registryConfig.getString("basic.auth.credentials.source"));
    assertEquals(
        "unit-registry-key:unit-registry-secret",
        registryConfig.getPassword("basic.auth.user.info").value());
    assertEquals(
        List.of("https://registry.example.test"), registryConfig.getList("schema.registry.url"));
  }

  @ParameterizedTest
  @MethodSource("proxySettings")
  void onlyEnablesSchemaRegistryProxyWhenExplicitlyRequested(Boolean enabled) {
    Map<String, Object> settings =
        new KafkaConfiguration(properties(enabled)).consumerFactory().getConfigurationProperties();

    if (Boolean.TRUE.equals(enabled)) {
      assertEquals("proxy.example.test", settings.get("proxy.host"));
      assertEquals("8080", settings.get("proxy.port"));
    } else {
      assertFalse(settings.containsKey("proxy.host"));
      assertFalse(settings.containsKey("proxy.port"));
    }
  }

  @Test
  void createsBatchListenerWithStoppingErrorHandlerWithoutStartingKafka() {
    KafkaConfiguration configuration = new KafkaConfiguration(properties(false));
    var factory = configuration.kafkaListenerContainerFactory();

    ConcurrentMessageListenerContainer<String, QueueContract> container =
        factory.createContainer("unit-audit");

    assertTrue(factory.isBatchListener());
    assertFalse(container.isRunning());
    assertInstanceOf(CommonContainerStoppingErrorHandler.class, container.getCommonErrorHandler());
  }

  @Test
  void failedBatchStopsContainerWithoutRecoveringOrCommittingItsRecords() {
    KafkaConfiguration configuration = new KafkaConfiguration(properties(false));
    var handler =
        configuration
            .kafkaListenerContainerFactory()
            .createContainer("unit-audit")
            .getCommonErrorHandler();
    Consumer<?, ?> consumer = mock(Consumer.class);
    MessageListenerContainer container = mock(MessageListenerContainer.class);
    Runnable invokeListener = mock(Runnable.class);
    ConsumerRecords<String, QueueContract> records =
        new ConsumerRecords<>(
            Map.of(
                new TopicPartition("unit-audit", 2),
                List.of(new ConsumerRecord<>("unit-audit", 2, 612194L, "key", null))));

    assertThrows(
        KafkaException.class,
        () ->
            handler.handleBatch(
                new ListenerExecutionFailedException("deserialization"),
                records,
                consumer,
                container,
                invokeListener));

    verify(container, timeout(2000)).stopAbnormally(any(Runnable.class));
    verifyNoInteractions(consumer, invokeListener);
  }

  private static Stream<Boolean> proxySettings() {
    return Stream.of(true, false, null);
  }

  private KafkaProperties properties(Boolean proxy) {
    KafkaProperties settings = new KafkaProperties();
    settings.setBootstrapServers("localhost:9092");
    settings.setGroupId("unit-group");
    settings.setAutoOffsetReset("earliest");
    settings.setMaxPollRecords("500");
    settings.setSecurityProtocol("SASL_SSL");
    settings.setSaslMechanism("PLAIN");
    settings.setKafkaApiKey("unit-kafka-key");
    settings.setKafkaApiSecret("unit-kafka-secret");
    settings.setSchemaRegistryUrl("https://registry.example.test");
    settings.setAutoRegisterSchemas("false");
    settings.setSchemaRegistryUseProxy(proxy);
    settings.setSchemaRegistryProxyHost("proxy.example.test");
    settings.setSchemaRegistryProxyPort("8080");
    settings.setSchemaRegistryBasicAuthCredentialsSource("USER_INFO");
    settings.setSchemaRegistryBasicAuthUserInfo("unit-registry-key:unit-registry-secret");
    settings.setSslEndpointIdentificationAlgorithm("https");
    settings.setSaslLoginRetryBackoffMaxMs("400000");
    settings.setSaslLoginRetryBackoffMs("200000");
    return settings;
  }
}
