package pe.com.scotiabank.lineage.consumer.domain.error;

public class NoTrxIndException extends RuntimeException {

  public static final String MESSAGE = "Unable to get the trxInd value";

  public NoTrxIndException() {
    super(MESSAGE);
  }

  public NoTrxIndException(String message) {
    super(message);
  }

  public NoTrxIndException(String message, Throwable cause) {
    super(message, cause);
  }
}
