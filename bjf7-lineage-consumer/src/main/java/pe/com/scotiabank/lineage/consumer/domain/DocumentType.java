package pe.com.scotiabank.lineage.consumer.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DocumentType {
  DNI("1", "DNI"),
  CE("2", "Carné de Extranjería"),
  PASSPORT("7", "Pasaporte"),
  RUC("5", "RUC"),
  DOC_PYRL("14", "P. NAT. Servicio"),
  MIN("10", "Menores de edad"),
  OTHERS("99", "Otros");

  private final String code;
  private final String description;
}
