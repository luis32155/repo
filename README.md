# Proyectos de auditoría

El repositorio contiene los dos proyectos independientes, sus pruebas, configuración, ejemplos y archivos de Postman.

| Proyecto | Contenido | Requisitos |
| --- | --- | --- |
| [bjf7-lineage-consumer](bjf7-lineage-consumer/) | Consumer Kafka con SQL Server y persistencia adicional en MongoDB para eventos TAPP. | Java 17, Gradle y acceso al Artifactory corporativo. |
| [log-audit-consumer-hexagonal-mapstruct](log-audit-consumer-hexagonal-mapstruct/) | Servicio HTTP con arquitectura hexagonal, puertos, adaptadores y mappers generados con MapStruct. | Java 25, Spring Boot 4.0.9 y Maven. |

Cada proyecto incluye su propio README con las variables de entorno y los pasos de ejecución. Se ejecutan y compilan por separado.

## Postman

Los archivos están en [log-audit-consumer-hexagonal-mapstruct/postman](log-audit-consumer-hexagonal-mapstruct/postman/):

- [Colección de peticiones](log-audit-consumer-hexagonal-mapstruct/postman/LogAuditConsumer.postman_collection.json).
- [Environment local](log-audit-consumer-hexagonal-mapstruct/postman/LogAuditConsumer-Local.postman_environment.json).

Importa ambos archivos en Postman y selecciona el environment **Log Audit - Local 8080**. Incluyen la consulta de salud y el envío de un evento con sus veinte detalles. Para el envío real, el servicio HTTP necesita la API de auditoría configurada en `AUDIT_API_URL`, cuyo valor local por defecto usa el puerto 8181.

## Pruebas

Para el consumer Kafka, desde su carpeta:

```powershell
.\gradlew.bat test jacocoTestCoverageVerification checkstyleMain checkstyleTest
```

La configuración de JaCoCo exige 90 % de cobertura de líneas y ramas. La última verificación aislada de todas sus clases de producción, usando dependencias públicas, ejecutó 108 casos: 95,83 % de líneas, 94 % de ramas y cero infracciones de Checkstyle. El build corporativo completo requiere las dependencias y credenciales de Artifactory.

Para el servicio hexagonal, desde su carpeta y usando Java 25:

```powershell
mvn clean verify
```

Sus pruebas usan una API HTTP simulada. MapStruct genera las implementaciones de los mappers durante la compilación.
