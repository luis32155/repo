package pe.com.scotiabank.lineage.consumer.domain.error;

public class ValidationException extends ConsumerException {

  public ValidationException(Throwable cause, String message) {
    super(message, cause);
  }
}
