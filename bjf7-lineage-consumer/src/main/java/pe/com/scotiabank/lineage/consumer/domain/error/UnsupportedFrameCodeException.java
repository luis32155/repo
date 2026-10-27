package pe.com.scotiabank.lineage.consumer.domain.error;

public class UnsupportedFrameCodeException extends ConsumerException {

  public static final String MESSAGE = "The frame code is not supported: ";

  public UnsupportedFrameCodeException(String frameCode) {
    super(MESSAGE + frameCode);
  }
}
