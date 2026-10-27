# bjf7-lineage-consumer

El consumer identifica TAPP leyendo `app_code` del JSON contenido en
`payload.auditLogs.message`. Los eventos TAPP se guardan en MongoDB y luego
continuan por el flujo existente de SQL Server. Los demas eventos siguen en SQL.

## MongoDB local

Con Docker Desktop iniciado, ejecutar desde esta carpeta:

```powershell
docker compose up -d --wait
docker compose ps
```

La aplicacion Java, ejecutada desde IntelliJ o Gradle en el equipo, se conecta
por defecto a `mongodb://localhost:27017/lineage_audit?retryWrites=false`.
La coleccion es `tapp_audit_events`. El puerto solo se publica en `127.0.0.1`
y los datos se conservan en un volumen de Docker al detener el contenedor.
La base y la coleccion aparecen al guardar el primer evento TAPP.

Para consultar los eventos con MongoDB Compass, usar esa misma URI. Tambien:

```powershell
docker compose exec mongodb mongosh lineage_audit --eval 'db.tapp_audit_events.find().limit(5).toArray()'
docker compose stop
```

Los valores pueden cambiarse con `MONGODB_URI`, `MONGODB_DATABASE`,
`MONGODB_COLLECTION`, `MONGODB_APPLICATION_CODE` y `MONGODB_RETENTION_DAYS`
en el entorno del proceso Java.
Ver `.env.example`. Spring Boot no carga automaticamente el archivo `.env`.
Si el microservicio se ejecuta en otro contenedor de la misma red de Compose,
el host de la URI debe ser `mongodb` en lugar de `localhost`.

SQL Server, Kafka y Schema Registry mantienen su configuracion existente.
El Compose incluye MongoDB; las otras conexiones siguen siendo necesarias para
arrancar el microservicio completo. Las dependencias privadas requieren acceso
y credenciales de Artifactory.

## Persistencia y reintentos

MongoDB guarda el documento de auditoria con los campos en la raiz:

- `_id`: `ObjectId` generado al preparar un documento nuevo.
- `uid`: valor de `payload.auditLogs.uuid`.
- `account_name`, hosts, IP y `session_identifier`: datos del evento recibido.
- `audit_action` y `audit_action_code`: descripcion y codigo de accion.
- `event_category` y `event_category_code`: descripcion y codigo de categoria.
- `event_log_classification`: descripcion de la clasificacion.
- `event_priority` y `event_priority_code`: descripcion y codigo de prioridad.
- `event_type` y `event_type_code`: descripcion y codigo del tipo de evento.
- `event_message`: objeto BSON obtenido del JSON de `payload.auditLogs.message`.
  Conserva los campos dinamicos, objetos y arreglos, incluido `channel_payload_detail`.
- `event_timestamp` y `log_timestamp`: fechas BSON, obtenidas respectivamente
  de `payload.auditLogs.event_timestamp` y `payload.auditLogs.timestamp`.
- `expires_at`: fecha del evento mas **365 dias**, configurable por entorno.

El documento final sigue el formato solicitado y no incluye los envoltorios
`metadata` ni `payload`. Ejemplo completo probado:
[`expected-tapp-document.json`](src/test/resources/mongo/expected-tapp-document.json).
En ese ejemplo el evento del 29 de septiembre de 2026 vence el 29 de septiembre
de 2027, de acuerdo con los 365 dias confirmados.

Las fechas requieren zona horaria. Se admiten ISO 8601 con `Z` o desplazamiento
horario, desplazamientos sin dos puntos y el formato heredado
`dd/MMM/yyyy'T'HH:mm:ss+0000` con mes en ingles. Fechas invalidas o sin zona,
UID vacio y mensajes que no sean objetos JSON producen error antes de escribir.

Los catalogos de prioridad, categoria, accion y clasificacion siguen `api.json`.
Para tipo de evento se configura `20: INFORMATION`, proporcionado en el ejemplo.
Se pueden agregar equivalencias en `app.mongodb.event-type-labels`. Tambien se
admiten las etiquetas conocidas en la entrada. Si un codigo no esta catalogado,
se conserva su valor en lugar de asignarle una descripcion inventada.

Antes de la primera escritura se asegura el indice unico disperso `uid_unique`.
El usuario MongoDB necesita permiso para crear ese indice. Se usa un upsert por
`uid` con `$setOnInsert`: reprocesar el mismo UID conserva la primera copia y su
`ObjectId`. El UID debe identificar un evento unico en esta coleccion.
Los documentos del formato anterior se conservan; este cambio no los migra.

`expires_at` es una fecha calculada. Este cambio **no crea un indice TTL** ni
activa eliminacion automatica de documentos.

MongoDB se escribe antes de las transformaciones y filtros de SQL. Por eso un
TAPP puede quedar en MongoDB aunque SQL lo descarte por tipo de documento o
por no tener canal registrado. Para persistir tambien en SQL debe cumplir las
validaciones y el catalogo de canales existentes.

Los errores de escritura en MongoDB se propagan al listener y detienen ese
procesamiento antes de escribir en SQL. El listener tambien consulta las
cabeceras de `ErrorHandlingDeserializer` para propagar errores de conversion,
incluidos los fallos de autenticacion de Schema Registry. Un valor nulo sin esa
cabecera se omite; un lote con error se rechaza antes de enviar sus eventos al
servicio de persistencia.

`CommonContainerStoppingErrorHandler` detiene el contenedor del listener ante
una excepcion. Tras corregir la causa, hay que reiniciarlo para releer el lote
fallido desde los offsets confirmados. Las credenciales de Schema Registry se
configuran con `basic.auth.credentials.source` y `basic.auth.user.info`; para
API key y secret, usar `SCHEMA_REGISTRY_BASIC_AUTH_CREDENTIALS_SOURCE=USER_INFO`.

No hay una transaccion distribuida entre las dos bases: si SQL falla despues
de MongoDB, la copia Mongo permanece.
Los reintentos de SQL conservan el comportamiento anterior, incluida la
posibilidad de duplicados si un lote ya confirmado en SQL se vuelve a consumir.

## Pruebas

```powershell
.\gradlew.bat test
```

Para exigir al menos **90 % de cobertura de lineas y 90 % de ramas**:

```powershell
.\gradlew.bat clean test jacocoTestCoverageVerification
```

La tarea `check` tambien exige estos limites. La medicion incluye todo
`src/main/java`, incluidos Kafka, configuracion, contratos y excepciones.
JaCoCo filtra automaticamente el codigo generado por Lombok.
Sonar usa el mismo reporte XML.

Reportes generados:

- HTML: `build/reports/jacoco/test/html/index.html`.
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`.
- Resultados JUnit: `build/test-results/test`.

Para validar el estilo de produccion y pruebas con las reglas entregadas:

```powershell
.\gradlew.bat checkstyleMain checkstyleTest
```

Para aplicar el formato Google configurado en Spotless:

```powershell
.\gradlew.bat spotlessApply
```

Checkstyle usa `config/checkstyle/google_checks.xml` y sus supresiones, con
cero errores y cero advertencias permitidos. Gradle lo ejecuta tambien como
parte de `check`. Los reportes se generan en `build/reports/checkstyle`.
El proyecto compila para Java 17 y usa UTF-8.

Las pruebas cubren el documento completo del ejemplo, detalles dinamicos,
fechas y retencion, catalogos, lotes mixtos, configuracion de beans, filtros de
SQL, errores de Mongo, indice unico y operaciones de upsert para reintentos.
Tambien comprueban los parametros de las consultas SQL, secuencias mensuales,
inserciones en ambas tablas, propagacion de fallos JDBC, transformaciones de
`Util`, contrato JSON del producer y copias defensivas de sus datos.

Las pruebas de Kafka simulan una respuesta `401` del deserializador para
comprobar que su causa se conserva y que el lote no llega parcialmente a
persistencia. Se verifica la configuracion efectiva de autenticacion con las
clases Confluent y la detencion del listener ante un fallo. No requieren
credenciales, conexiones de red ni bases de datos reales durante su ejecucion.

Se conservan los nueve escenarios originales de `LineageConsumerApplicationTests`,
`pe.com.scotiabank.lineage.consumer.SqlServerRepositoryAdapterTest` y
`pe.com.scotiabank.lineage.consumer.UtilTest`. El montaje del flujo SQL incorpora
un mock de `MongoAuditService`, y el camino exitoso verifica las dos escrituras
y el identificador asignado. Los archivos `QueueContract-BGGG.json` y
`QueueContract-BFNB.json` contienen datos sinteticos locales para esas pruebas;
los recursos JSON originales no fueron incluidos en el texto compartido.

Referencias: [configuracion MongoDB de Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html),
[operaciones de persistencia de Spring Data MongoDB](https://docs.spring.io/spring-data/mongodb/reference/4.5/mongodb/template-crud-operations.html),
[`$setOnInsert`](https://www.mongodb.com/docs/manual/reference/operator/update/setoninsert/)
y [expiracion con indices TTL](https://www.mongodb.com/docs/manual/core/index-ttl/).
