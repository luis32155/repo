package pe.com.scotiabank.lineage.consumer.domain.error;

public class ConvertingToJsonException extends ConsumerException {

  public ConvertingToJsonException(Throwable cause, Object object) {
    super("Unable to convert to json. Class " + object.getClass().getName(), cause);
  }
}
