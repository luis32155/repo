package pe.com.scotiabank.lineage.consumer.domain.error;

public class ParseFrameException extends ConsumerException {

  public ParseFrameException(Throwable cause, String message, Class clazz) {
    super("Unable to parse to " + clazz.getName() + ". Message: " + message, cause);
  }
}
