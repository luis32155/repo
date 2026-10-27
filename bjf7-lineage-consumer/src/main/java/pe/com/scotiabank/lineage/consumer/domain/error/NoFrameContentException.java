package pe.com.scotiabank.lineage.consumer.domain.error;

public class NoFrameContentException extends ConsumerException {

  public static final String MESSAGE = "Unable to get the frame content from message field";

  public NoFrameContentException() {
    super(MESSAGE);
  }
}
