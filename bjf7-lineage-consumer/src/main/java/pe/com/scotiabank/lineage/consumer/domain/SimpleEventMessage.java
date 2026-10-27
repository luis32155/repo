package pe.com.scotiabank.lineage.consumer.domain;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimpleEventMessage {
  private String operationNumber;
  private String camsId;
  private String btAccount;
  private ChannelPayloadHeader channelPayloadHeader;
  private List<ChannelPayloadDetail> channelPayloadDetail;
}
