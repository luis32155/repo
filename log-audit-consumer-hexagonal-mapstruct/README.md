# Microservicio de Log Audit con arquitectura hexagonal

Proyecto independiente con **Java 25, Spring Boot 4.0.9, MapStruct 1.6.3 y Maven**. Recibe tu evento por HTTP y lo envía mediante `RestClient` a la API de auditoría que indicaste. El dominio y los casos de uso utilizan Java sin dependencias de Spring, Jackson, Jakarta Validation o MapStruct.

```text
Tu aplicación o Postman
    POST http://localhost:8080/api/v1/audit/security/log
                    |
                    v
    Adaptador web -> Caso de uso -> Puerto de salida
                    |
                    v
          Adaptador HTTP con RestClient
                    |
                    v
    POST http://localhost:8181/api/v1/audit/security/log
```

El consumidor transmite las cuatro cabeceras y todos los campos del JSON del ejemplo, incluidos los 20 elementos de `channel_payload_detail`. Las fechas conservan su desplazamiento horario. `channel_payload_header` admite propiedades dinámicas, objetos y arreglos.

## 1. Requisitos

- JDK 25.
- Maven 3.6.3 o posterior.
- La API de auditoría ejecutándose en el puerto 8181 para una prueba real. Las pruebas automatizadas utilizan un servidor HTTP simulado y no necesitan esa API.

Comprueba `java -version` y `mvn -version`: ambos deben utilizar Java 25. Si Maven muestra Java 21 u otra versión, corrige `JAVA_HOME` antes de compilar. En PowerShell, para una instalación en la ruta habitual:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -version
```

## 2. Abrir y ejecutar

Abre esta carpeta como proyecto Maven en IntelliJ IDEA y selecciona JDK 25. La clase de inicio es `LogAuditConsumerApplication`.

Desde una terminal ubicada en esta carpeta también puedes ejecutar:

```powershell
mvn clean verify
mvn spring-boot:run
```

Después de empaquetar, puedes iniciarlo sin Maven:

```powershell
java -jar target/log-audit-consumer-1.0.0.jar
```

El microservicio escucha en el puerto **8080**. La API que consume sigue en el **8181**. El endpoint de salud es `GET http://localhost:8080/actuator/health`; indica la salud del consumidor, no verifica la disponibilidad de la API de auditoría.

## 3. Probar con tu JSON

El archivo `examples/audit-request.json` contiene el JSON de tu mensaje, con sus veinte detalles. Solo se limpiaron el formato de texto y los escapes HTML de la petición original.

En PowerShell, ejecuta este comando desde la carpeta del proyecto:

```powershell
curl.exe --request POST "http://localhost:8080/api/v1/audit/security/log" --header "x-api-version: apiversion" --header "x-channel-id: channel" --header "x-originating-appl-code: originating" --header "x-country-code: country" --header "Content-Type: application/json" --data-binary "@examples/audit-request.json"
```

En Linux o macOS, usa `curl` en lugar de `curl.exe`. También puedes abrir `examples/audit.http` con el cliente HTTP de IntelliJ o una extensión compatible.

### Importar la colección y el environment de Postman

La carpeta `postman` incluye dos archivos listos para importar:

- `LogAuditConsumer.postman_collection.json`: colección con las peticiones de salud y envío de auditoría. El POST contiene el JSON completo con los veinte detalles.
- `LogAuditConsumer-Local.postman_environment.json`: environment **Log Audit - Local 8080**, con la URL y las cuatro cabeceras de ejemplo.

1. En Postman, abre **Import** y selecciona los dos archivos JSON de la carpeta `postman`.
2. Selecciona el environment **Log Audit - Local 8080** en el selector de entornos.
3. Inicia el microservicio consumidor y la API de auditoría.
4. Abre la colección **Log Audit Consumer - Hexagonal y MapStruct**.
5. Ejecuta **1 - Salud del consumidor**. Debe responder HTTP 200 y `status: UP`.
6. Ejecuta **2 - Enviar evento de auditoría**.

El environment define `base_url=http://localhost:8080`, `api_version=apiversion`, `channel_id=channel`, `originating_appl_code=originating` y `country_code=country`. Puedes modificar las cabeceras desde ese environment.

La URL del POST se arma como `{{base_url}}/api/v1/audit/security/log`. Postman llama al consumidor del **8080**; el consumidor llama a la API destino del **8181** por defecto. Cambiar el environment de Postman no cambia `AUDIT_API_URL` en el microservicio.

La colección incluye comprobaciones de salud y de la respuesta de auditoría `data[].status = OK`. Si la API destino está detenida, el POST devuelve HTTP 503 y las comprobaciones de éxito fallan.

Si la API de auditoría devuelve tu respuesta de ejemplo, recibirás:

```json
{
  "data": [
    { "status": "OK" }
  ]
}
```

La respuesta procede de la API real. El microservicio no genera `OK` si esa API no responde correctamente. Conserva el estado HTTP exitoso que devuelva, por ejemplo 200 o 201.

## 4. Cambiar la URL o los tiempos de espera

Los valores están en `src/main/resources/application.yml` y pueden cambiarse mediante variables de entorno:

| Variable | Valor predeterminado |
|---|---|
| `SERVER_PORT` | `8080` |
| `AUDIT_API_URL` | `http://localhost:8181/api/v1/audit/security/log` |
| `AUDIT_API_CONNECT_TIMEOUT` | `3s` |
| `AUDIT_API_READ_TIMEOUT` | `10s` |

Ejemplo en PowerShell:

```powershell
$env:AUDIT_API_URL = 'http://localhost:8181/api/v1/audit/security/log'
$env:AUDIT_API_READ_TIMEOUT = '15s'
mvn spring-boot:run
```

`.env.example` es una referencia: Spring Boot no lo carga automáticamente. Define las variables en la terminal o en el IDE. Si ejecutas el consumidor en un contenedor, `localhost` será ese contenedor; configura la URL de acuerdo con la red donde esté la API.

Las cabeceras se reciben en cada petición y se transmiten a la API:

| Cabecera | Valor usado en el ejemplo |
|---|---|
| `x-api-version` | `apiversion` |
| `x-channel-id` | `channel` |
| `x-originating-appl-code` | `originating` |
| `x-country-code` | `country` |

Reemplaza esos valores de muestra por los que exija la API en tu ambiente.

## 5. Validación y errores

La validación local exige las cuatro cabeceras sin blancos y los campos definidos en los DTO a partir de tu ejemplo. Las fechas deben incluir zona horaria, como `2026-07-09T15:19:59-05:00`. El detalle es una lista, sin un límite de veinte elementos impuesto por este consumidor.

No se recibió un OpenAPI con las reglas oficiales de obligatoriedad, catálogos o tamaños. Por eso las clasificaciones y acciones se manejan como texto. Ajusta las anotaciones de `adapter/in/web/dto/AuditLogRequest` si el contrato oficial permite campos opcionales o impone otras reglas. Se rechazan los campos desconocidos fuera de `channel_payload_header` para evitar perderlos durante el mapeo.

Las direcciones `888.888.888.888` y `999.999.999.999` son valores de muestra, no IPv4 válidas. Se conservaron para reproducir tu JSON; cámbialas por direcciones reales cuando lo exija la API destino.

| Situación | HTTP del consumidor |
|---|---|
| JSON inválido, fecha inválida, cabecera ausente o validación local | `400` |
| La API responde un error `4xx` | Conserva ese código |
| La API responde un error `5xx` | `502` |
| Respuesta vacía, JSON incompatible o `data` sin estados válidos | `502` |
| No es posible conectar con la API | `503` |
| Se agota el tiempo de espera | `504` |

Los errores usan `application/problem+json`. Cuando corresponde, incluyen `upstream_status`. No se devuelve el cuerpo de error de la API destino ni se registran los documentos de identidad o el JSON de auditoría en logs de la aplicación.

No se agregan reintentos automáticos del POST ni se siguen redirecciones. Ante un timeout, la API podría haber recibido el evento; verifica el resultado antes de reenviarlo. Si necesitas reintentos, primero acuerda una clave de idempotencia con la API destino.

## 6. Arquitectura hexagonal

El núcleo se divide en dominio y aplicación. Los adaptadores dependen de sus puertos y modelos; el núcleo desconoce los adaptadores concretos.

| Componente | Responsabilidad |
|---|---|
| `domain/model` | Modelos de Java para el evento, contexto y resultado. No contiene anotaciones de frameworks. |
| `application/port/in/SubmitAuditUseCase` | Puerto de entrada que expresa el caso de uso de envío. |
| `application/service/SubmitAuditService` | Implementa el caso de uso. Recibe exclusivamente el puerto de salida. |
| `application/port/out/SendAuditPort` | Contrato para entregar el evento al proveedor de auditoría. |
| `adapter/in/web` | Recibe HTTP, valida el DTO y utiliza el puerto de entrada. Convierte el resultado y los errores a HTTP. |
| `adapter/out/http` | Implementa el puerto de salida mediante `RestClient`. Convierte los modelos del dominio al contrato de la API. |
| `bootstrap/ApplicationConfiguration` | Conecta el caso de uso y el puerto de salida mediante configuración de Spring. |

`SubmitAuditService` no tiene `@Service` ni devuelve `ResponseEntity`. Su constructor recibe `SendAuditPort`; el adaptador web recibe `SubmitAuditUseCase`. Puedes reemplazar el cliente HTTP por otra implementación del puerto sin modificar el caso de uso.

`AuditSubmission` transporta el resultado y `providerStatusCode` como metadato simple de integración para conservar el código de éxito original del proveedor. El caso de uso no interpreta ese número. La conversión a estado HTTP pertenece al adaptador web. Los fallos se representan mediante `AuditDeliveryException` y sus categorías, sin excepciones de Spring en las firmas del núcleo.

### Mapeos con MapStruct

Los DTO de entrada y de la API externa son clases diferentes. Cada adaptador tiene su mapper:

```text
AuditLogRequest --AuditWebMapper--> AuditEvent
AuditEvent      --AuditApiMapper--> AuditApiRequest
AuditApiResponse --AuditApiMapper--> AuditResult
AuditResult     --AuditWebMapper--> AuditLogResponse
```

`AuditWebMapper` también convierte las cuatro cabeceras en `AuditContext`. Ambos mappers convierten los objetos anidados y las listas de detalles o estados.

La configuración común `StrictMapperConfig` registra los mappers como beans de Spring y utiliza `ReportingPolicy.ERROR` para propiedades de origen o destino sin mapear y conversiones con pérdida. Si agregas un campo a un modelo y falta su conversión, la compilación falla.

El procesador `mapstruct-processor` está configurado explícitamente en `maven-compiler-plugin`, necesario para generar los mappers al compilar con este JDK. Maven crea `AuditWebMapperImpl` y `AuditApiMapperImpl` en `target/generated-sources/annotations`. No edites esas implementaciones. En IntelliJ, recarga el proyecto Maven y habilita el procesamiento de anotaciones si el IDE lo requiere.

### Estructura del proyecto

```text
src/main/java/pe/com/scotiabank/tapp/audit/
  LogAuditConsumerApplication.java
  domain/model/
    AuditEvent.java
    AuditContext.java
    AuditResult.java
  application/
    port/in/SubmitAuditUseCase.java
    port/out/SendAuditPort.java
    service/SubmitAuditService.java
    model/AuditSubmission.java
    exception/AuditDeliveryException.java
  adapter/
    common/http/AuditHttpHeaders.java
    common/mapper/StrictMapperConfig.java
    in/web/
      AuditLogController.java
      dto/AuditLogRequest.java
      dto/AuditLogResponse.java
      mapper/AuditWebMapper.java
      error/ApiExceptionHandler.java
    out/http/
      AuditApiClient.java
      dto/AuditApiRequest.java
      dto/AuditApiResponse.java
      mapper/AuditApiMapper.java
      config/AuditApiProperties.java
      config/AuditClientConfiguration.java
  bootstrap/ApplicationConfiguration.java
src/main/resources/application.yml
src/test/java/pe/com/scotiabank/tapp/audit/
  AuditConsumerIntegrationTest.java
  HexagonalArchitectureTest.java
examples/audit-request.json
examples/audit.http
postman/LogAuditConsumer.postman_collection.json
postman/LogAuditConsumer-Local.postman_environment.json
```

## 7. Pruebas automatizadas

```powershell
mvn test
```

Las pruebas levantan el microservicio en un puerto aleatorio y una API simulada en otro puerto aleatorio. Verifican la transmisión de las cabeceras, el JSON completo, los veinte detalles, el desplazamiento horario, la validación anidada, los errores de la API, las respuestas incompatibles, la conexión fallida y los tiempos de espera.

Las reglas de `HexagonalArchitectureTest`, con ArchUnit, comprueban además que el dominio dependa solo de Java y de sí mismo; que la aplicación dependa solo de Java, dominio y aplicación; que los adaptadores de entrada y salida no se acoplen entre sí; y que los puertos sean interfaces. Los mappers generados también forman parte de las clases comprobadas.

## Referencias

- [Clientes REST de Spring Boot 4](https://docs.spring.io/spring-boot/4.0/reference/io/rest-client.html).
- [Requisitos de Spring Boot 4](https://docs.spring.io/spring-boot/4.0/system-requirements.html).
- [Jackson 3 en Spring Boot 4](https://docs.spring.io/spring-boot/4.0/reference/features/json.html).
- [Configuración y generación de mappers con MapStruct](https://mapstruct.org/documentation/stable/reference/html/).
- [Pruebas de arquitectura con ArchUnit](https://www.archunit.org/userguide/html/000_Index.html).
