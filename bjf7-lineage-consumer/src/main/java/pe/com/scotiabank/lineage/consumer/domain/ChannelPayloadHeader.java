package pe.com.scotiabank.lineage.consumer.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelPayloadHeader {
  private String header;
  private String description;
}
