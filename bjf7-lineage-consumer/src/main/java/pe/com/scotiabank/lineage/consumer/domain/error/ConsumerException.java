package pe.com.scotiabank.lineage.consumer.domain.error;

public class ConsumerException extends RuntimeException {

  public ConsumerException(String message) {
    super(message);
  }

  public ConsumerException(String message, Throwable cause) {
    super(message, cause);
  }
}
