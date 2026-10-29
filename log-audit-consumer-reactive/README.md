# Log Audit Consumer reactivo

POC con Java 25, Spring Boot 4.0.7, Gradle Wrapper 9.1.0 y MapStruct 1.6.3.
El servidor usa Spring WebFlux sobre Reactor Netty y el cliente de salida usa
WebClient. El controlador, los puertos y el caso de uso retornan `Mono`.

```text
POST /api/v1/audit/security/log
  -> AuditLogController
  -> SubmitAuditUseCase / SubmitAuditService
  -> SendAuditPort / AuditApiClient
  -> WebClient
  -> API configurada en AUDIT_API_URL
```

El dominio utiliza exclusivamente Java. La aplicación utiliza Java, modelos
propios y Reactor para expresar el contrato asíncrono. Sus clases no dependen de
Spring, Jakarta Validation, Jackson ni MapStruct. Los mappers y los clientes de
Spring están en los adaptadores. MapStruct genera sus implementaciones al compilar.

## Ejecutar

Instala JDK 25 y abre `build.gradle` como proyecto Gradle en IntelliJ. Selecciona
JDK 25 en el SDK y en Gradle JVM, y usa el Wrapper incluido.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\gradlew.bat --version
.\gradlew.bat clean build
$env:AUDIT_API_URL = 'http://localhost:8181/api/v1/audit/security/log'
.\gradlew.bat bootRun
```

En Linux/macOS, usa `./gradlew clean build` y `./gradlew bootRun`.
El Wrapper descarga Gradle en la primera ejecución. Para ejecutar el paquete:

```powershell
java -jar build/libs/log-audit-consumer-reactive-1.0.0.jar
```

El consumidor escucha en `http://localhost:8080`. Comprueba su estado con
`GET http://localhost:8080/actuator/health`. La disponibilidad de la API destino
se comprueba al enviar el POST.

## Configuración

| Variable | Valor por defecto |
| --- | --- |
| `SERVER_PORT` | `8080` |
| `AUDIT_API_URL` | `http://localhost:8181/api/v1/audit/security/log` |
| `AUDIT_API_CONNECT_TIMEOUT` | `3s` |
| `AUDIT_API_READ_TIMEOUT` | `10s` |

Define las variables en la terminal o en la configuración de ejecución de
IntelliJ. `.env.example` es una referencia y Spring Boot no lo carga por sí solo.
Configura `AUDIT_API_URL` con la URL del producer real. El puerto del consumidor
y el de la API destino deben corresponder a los servicios que estás ejecutando.

El cliente limita la respuesta en memoria a 256 KiB. Usa timeout de conexión,
timeout de lectura y un límite total de conexión más lectura. El flujo no usa
`block()`, `subscribe()` manual, reintentos del POST ni seguimiento de redirects.
También está desactivado el reintento de conexión de Reactor Netty.

## Probar con Postman

Importa los dos archivos de `postman` y selecciona el environment
**Log Audit Reactive - Local 8080**. La colección incluye salud y POST de auditoría.
El POST utiliza el mismo JSON de `examples/audit-request.json`, con sus veinte
detalles. También puedes usar el archivo `examples/audit.http`.

Las cuatro cabeceras se reciben en cada petición y se transmiten a la API destino:
`x-api-version`, `x-channel-id`, `x-originating-appl-code` y `x-country-code`.
Los valores del environment son ejemplos; cámbialos por los exigidos por tu API.
Los campos del JSON también son de muestra: las IP `888.888.888.888` y
`999.999.999.999` no son IPv4 válidas. Usa datos admitidos por el contrato real.

Las fechas requieren zona horaria y conservan el desplazamiento recibido.
Los objetos y arreglos dinámicos de `channel_payload_header` se mantienen.
Los campos desconocidos fuera de esa sección se rechazan para evitar perderlos
durante el mapeo.

## Respuestas y diagnóstico del HTTP 400

| Situación | Respuesta del consumidor |
| --- | --- |
| Éxito con `data` y estados válidos | Conserva el HTTP 2xx y el JSON de respuesta |
| Cabeceras ausentes/vacías, JSON o validación inválidos | `400` |
| La API destino responde `4xx` | Conserva ese estado e incluye `upstream_status` |
| La API destino responde `5xx` | `502`, con `upstream_status` |
| Redirección, respuesta vacía, incompatible o demasiado grande | `502` |
| No se puede conectar con la API destino | `503` |
| Se agota el timeout | `504` |

Si recibes `"upstream_status": 400`, la API destino o su gateway devolvió ese
estado. Migrar a WebClient no cambia sus reglas de validación. Revisa la URL,
las cuatro cabeceras y los valores exigidos por el contrato de la API.

El texto exacto del rechazo se conserva en la causa `WebClientResponseException`
de `AuditDeliveryException`, dentro del límite de memoria. Para inspeccionarlo
en IntelliJ, pon un breakpoint en `AuditApiClient.providerFailure` y evalúa
`cause.getResponseBodyAsString()` después de crear `cause`.
Los cuerpos excesivos o ilegibles se descartan y se conserva la causa del fallo.
El cuerpo del error del proveedor y los datos de auditoría no se exponen en la
respuesta pública ni se escriben en logs de la aplicación.

## Pruebas

```powershell
.\gradlew.bat test
```

Las unitarias `SubmitAuditServiceTest`, `AuditApiClientTest` y `AuditMappersTest`
no levantan Spring ni servidores. Usan `StepVerifier`, implementaciones locales
de los puertos y un `ExchangeFunction` en memoria para comprobar resultados,
propagación de errores, suscripción diferida, cancelación y mapeos completos.

Para ejecutar sólo las pruebas unitarias:

```powershell
.\gradlew.bat test --tests '*SubmitAuditServiceTest' --tests '*AuditApiClientTest' --tests '*AuditMappersTest'
```

Las pruebas de integración levantan el consumidor y una API HTTP simulada en
puertos aleatorios. Verifican el documento completo, cabeceras, fechas, errores,
respuestas vacías o excesivas, redirecciones, timeouts y contexto WebFlux con Netty.
ArchUnit comprueba las dependencias de las capas y prohíbe clientes bloqueantes,
dependencias Servlet y llamadas a `Mono.block()` en producción.

El reporte HTML queda en `build/reports/tests/test/index.html`; los XML están
en `build/test-results/test`. Las pruebas no necesitan una API corporativa real.

Verificación local con Java 25: `clean build` completado, con 50 pruebas
unitarias, 39 de integración y 7 de arquitectura: 96 ejecuciones sin fallos,
errores ni pruebas omitidas.

## Referencias

- [Aplicaciones reactivas de Spring Boot](https://docs.spring.io/spring-boot/4.0/reference/web/reactive.html).
- [WebClient y sus recursos HTTP](https://docs.spring.io/spring-boot/4.0/reference/io/rest-client.html).
- [Starters de Spring Boot 4](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
- [Manejo de respuestas con exchangeToMono](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-exchange.html).
- [MapStruct](https://mapstruct.org/documentation/stable/reference/html/).
