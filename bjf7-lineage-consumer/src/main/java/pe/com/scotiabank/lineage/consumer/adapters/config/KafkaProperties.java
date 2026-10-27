package pe.com.scotiabank.lineage.consumer.adapters.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.kafka.properties")
public class KafkaProperties {

  private String topic;
  private String groupId;
  private String bootstrapServers;
  private String autoOffsetReset;
  private String securityProtocol;
  private String saslMechanism;
  private String saslLoginRetryBackoffMaxMs;
  private String saslLoginRetryBackoffMs;
  private String sslEndpointIdentificationAlgorithm;
  private String schemaRegistryUrl;
  private String autoRegisterSchemas;
  private Boolean schemaRegistryUseProxy;
  private String schemaRegistryProxyHost;
  private String schemaRegistryProxyPort;
  private String schemaRegistryBasicAuthCredentialsSource;
  private String schemaRegistryBasicAuthUserInfo;
  private String maxPollRecords;
  private String kafkaApiKey;
  private String kafkaApiSecret;
}
