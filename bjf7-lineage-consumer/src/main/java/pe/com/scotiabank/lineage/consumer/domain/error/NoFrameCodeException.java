package pe.com.scotiabank.lineage.consumer.domain.error;

public class NoFrameCodeException extends ConsumerException {

  public static final String MESSAGE = "Unable to get the frame code";

  public NoFrameCodeException() {
    super(MESSAGE);
  }
}
