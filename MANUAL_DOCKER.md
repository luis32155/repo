# Manual de Docker local — CVD

## 1. Alcance

Ejecuta Config Server, API Gateway, ocho microservicios y Redis en Docker, sin
mantenerlos abiertos en IntelliJ. Java 17 y Maven se ejecutan dentro de las imagenes.

SQL Server, Keycloak, LDAP y las APIs corporativas siguen siendo las de desarrollo.
Necesitas VPN/red corporativa y DNS accesible desde Docker para probar esos flujos.
Este repositorio no incluye las bases, datos ni realms para replicarlos offline.

El entorno es portable: la ubicacion del repositorio y de los secretos puede cambiar
en cada maquina. Puertos y recursos se configuran en `.env`. Las conexiones internas
usan nombres de servicio Docker, sin depender de la IP del equipo.

## 2. Requisitos en la otra maquina

- Docker Desktop iniciado con contenedores Linux y BuildKit.
- Docker Compose v2.26 o posterior y PowerShell. Tambien puedes usar PowerShell 7
  (`pwsh`) en Linux/macOS con Docker.
- Internet durante la primera compilacion para descargar imagenes y dependencias.
- Acceso a los sistemas corporativos cuando pruebes operaciones de negocio.
- Memoria disponible para los contenedores, Docker/WSL y el sistema operativo.

Comprueba la instalacion:

```powershell
docker version
docker compose version
```

No necesitas instalar Java ni Maven en Windows.

## 3. Archivos que debes llevar

Copia o clona el repositorio completo en cualquier carpeta, conservando:

```text
carpeta-del-proyecto/
  compose.yaml
  .env.example
  .dockerignore
  .gitignore
  docker/
  config-repo/
  config-server/
  api-gateway/
  ms-auth-token-generator/
  ms-auth-token-validator/
  ms-authorizer/
  ms-login/
  ms-maintenance-window/
  ms-cards/
  ms-products/
  ms-publications/
  environments_variables_and_secrets_develop/
```

Cada servicio necesita su `pom.xml` y `src/main`. No necesitas copiar `target` ni
`.idea`. Docker construye las imagenes en el destino.

Los manifiestos `*-secrets.yml` deben estar disponibles en la ultima carpeta o en
otra que indiques con `-ManifestsDirectory`. Si no estan en la copia, llevalos por
un canal privado junto con las credenciales.

La carpeta de certificados debe contener los archivos de tu captura:

```text
config-server-keystore.p12
keycloak_public_key.pem
rsa_private_key_frontend.pem
rsa_private_key_with_session.pem
rsa_private_key_without_session.pem
rsa_public_key_frontend.pem
rsa_public_key_with_session.pem
rsa_public_key_without_session.pem
truststore.jks
```

Tambien se acepta `truststore.p12`. Windows puede ocultar la extension `.p12` del
archivo `config-server-keystore`. Las aplicaciones detectan JKS o PKCS12 aunque
la extension del archivo montado sea `.p12`.

## 4. Inicializacion en la maquina destino

Abre PowerShell **en la carpeta del repositorio**, donde se encuentra `compose.yaml`.
Indica la ruta real de tus certificados; esta ruta es un parametro, no esta fijada
en el script:

```powershell
.\docker\local.ps1 init -SecretsDirectory 'C:\Desarrollo\repositorio\secrets'
```

Si tambien tienes los manifiestos en otra ubicacion:

```powershell
.\docker\local.ps1 init `
  -SecretsDirectory 'E:\credenciales\cvd' `
  -ManifestsDirectory 'E:\credenciales\manifiestos-cvd'
```

Sin `-SecretsDirectory`, se usa la variable de entorno `CVD_SECRETS_DIR`, si existe,
o la carpeta `secrets/` dentro del repositorio. Asi puedes mover todo el directorio
a otro disco sin modificar codigo.

El comando genera:

| Ubicacion | Contenido |
| --- | --- |
| `.env` | Puertos y recursos, copiados de `.env.example` si no existe |
| `.local/env/*.env` | Variables de cada servicio importadas de los manifiestos |
| `.local/secrets/` | Copias de las claves y certificados |
| `.local/config-repo/` | Configuracion de desarrollo ajustada para Docker |

Config Server usa el perfil `native`, lee la copia local y descifra las propiedades
`{cipher}` con el keystore existente. No necesita clonar Git remoto. No se activa
el perfil `local` de IntelliJ que contiene rutas absolutas de Windows.

El password del truststore se toma del `application-local.yml` de `ms-cards`. Si tu
truststore tiene otro password, establece `CVD_TRUSTSTORE_PASSWORD` antes de `init`
o actualiza `LOCAL_TRUSTSTORE_PASSWORD` en los `.local/env/*.env` generados.
Las credenciales del Config Server proceden de `config-server-secrets.yml`.

Si ya inicializaste, `init` evita sobrescribir tus ajustes. Para regenerarlos:

```powershell
.\docker\local.ps1 init -Force -SecretsDirectory 'C:\Desarrollo\repositorio\secrets'
```

`-Force` sobrescribe las variables, certificados y configuracion generada de `.local/`.
Guarda antes cualquier ajuste manual. El `.env` existente se conserva.

## 5. Configurar puertos y memoria

Edita `.env`. Ejemplo para una maquina cuyos puertos 8080 y 8888 ya estan ocupados:

```dotenv
COMPOSE_PROJECT_NAME=cvd-local
BIND_ADDRESS=127.0.0.1
GATEWAY_PORT=18080
CONFIG_SERVER_PORT=18888
JAVA_MEMORY_LIMIT=512m
JAVA_HEAP_MAX=256m
JAVA_CPU_LIMIT=1.0
```

El gateway se abrira en `http://localhost:18080`. Dentro de Docker conserva el puerto
8080, por lo que las conexiones internas no necesitan cambios. Todos los puertos
del host tienen variables equivalentes en `.env.example`.

`127.0.0.1` permite acceso solo desde esa maquina. Si necesitas acceso desde otro
equipo, cambia `BIND_ADDRESS` y configura el firewall considerando que se usa HTTP.

Por defecto, cada JVM tiene heap de hasta 256 MiB y un limite total de 512 MiB.
Redis tiene un limite total de 128 MiB y 96 MiB para datos. Los limites suman unos
5.1 GiB, mas la memoria que requieren Docker/WSL, Windows y otros programas.
Los pools SQL se limitan a cuatro conexiones por micro.

Si aparece `OOMKilled`, levanta menos servicios o aumenta, por ejemplo:

```dotenv
JAVA_MEMORY_LIMIT=640m
JAVA_HEAP_MAX=320m
```

El heap debe ser menor que el limite total: Java tambien necesita memoria nativa,
metaspace, pilas y buffers. Estos valores aplican a todas las JVM. Ejecuta `up` para
recrear los contenedores afectados. Consulta el consumo real con `docker stats`.

## 6. Compilar y levantar todo

```powershell
.\docker\local.ps1 build
.\docker\local.ps1 up
.\docker\local.ps1 ps
```

`build` compila secuencialmente y reutiliza la cache Maven para reducir picos de RAM.
La primera compilacion tarda mas por las descargas. Omite los tests durante el
empaquetado; los healthchecks validan el arranque. Los Dockerfile corporativos
de cada micro no se modifican.

`up` espera que Config Server y Redis esten saludables antes de iniciar los clientes.
Espera hasta 240 segundos por la salud del entorno y devuelve error si falla.

Puertos predeterminados:

| Servicio | Puerto del host |
| --- | --- |
| API Gateway | 8080 |
| Config Server | 8888 |
| Login | 8084 |
| Generador de token | 8086 |
| Validador de token | 8087 |
| Authorizer | 8088 |
| Maintenance window | 8089 |
| Cards | 8090 |
| Publications | 8091 |
| Products | 8092 |

Redis solo esta disponible dentro de Docker. Verifica el gateway en
[actuator/health](http://localhost:8080/actuator/health), usando tu puerto configurado.
El gateway local usa los endpoints BIAN del codigo actual.

## 7. Comandos de uso diario

```powershell
# Arrancar imagenes existentes:
.\docker\local.ps1 up

# Ver estado:
.\docker\local.ps1 ps

# Seguir logs; Ctrl+C sale del seguimiento:
.\docker\local.ps1 logs -Services ms-cards

# Reconstruir y recrear solo el micro modificado:
.\docker\local.ps1 build -Services ms-cards
.\docker\local.ps1 up -Services ms-cards

# Detener y eliminar los contenedores de este proyecto:
.\docker\local.ps1 down
```

`down` conserva imagenes, `.env` y `.local/`. No toca otros proyectos Docker.
Redis es efimero: reiniciarlo o eliminarlo pierde sesiones y tokens locales;
necesitaras volver a autenticarte.

Tambien funcionan `docker compose up -d`, `docker compose ps`, `docker compose logs`
y `docker compose down`. Para compilar, prefiere el script o
`docker compose --parallel 1 build` y evita construir todos los micros en paralelo.

## 8. Levantar solo los servicios que necesitas

Ejemplo para trabajar tarjetas con sus dependencias internas:

```powershell
.\docker\local.ps1 down
.\docker\local.ps1 up -Services ms-auth-token-generator,ms-auth-token-validator,ms-authorizer,ms-cards,api-gateway
```

Config Server y Redis se agregan automaticamente. Incluye `ms-login` si probaras
el inicio de sesion. Las rutas hacia micros apagados fallaran.
Seleccionar un subconjunto no detiene servicios que ya estaban activos: por eso
el ejemplo comienza con `down`.

## 9. Modificar configuracion y certificados

| Cambio | Procedimiento |
| --- | --- |
| Puertos o memoria | Edita `.env` y ejecuta `up` |
| Variables de un micro | Edita `.local/env/<servicio>.env` y ejecuta `up` |
| Propiedades del Config Server | Edita `.local/config-repo/*.properties` y ejecuta `restart` para los clientes |
| Ajustes reproducibles de Docker | Edita `docker/common.properties` o `docker/gateway.properties`, regenera con `init -Force` y reinicia |
| Certificados | Ejecuta `down`, actualiza el origen, ejecuta `init -Force` con esa carpeta y despues `up` |

No hay refresco automatico de propiedades. Si los endpoints corporativos cambian
entre redes, actualiza las propiedades correspondientes en `.local/config-repo/`.
Para llamar desde Docker a un servicio que corre directamente en Windows, utiliza
`host.docker.internal`. `localhost` dentro de un contenedor apunta a ese contenedor.

## 10. Solucion de problemas

| Problema | Que hacer |
| --- | --- |
| Docker no responde | Inicia Docker Desktop y comprueba `docker version` y modo Linux |
| PowerShell bloquea el script | Si tu organizacion lo permite, usa `powershell -ExecutionPolicy Bypass -File .\docker\local.ps1 ...` para esa invocacion |
| No existe la carpeta de secretos | Indica la ruta real con `-SecretsDirectory` |
| Falta un certificado | Verifica nombres y extensiones con la lista del paso 3 |
| Puerto ocupado | Cambia el puerto correspondiente en `.env` |
| Error de truststore | Verifica archivo y `LOCAL_TRUSTSTORE_PASSWORD` |
| Error al descifrar `{cipher}` | Keystore y password deben corresponder al config-repo de desarrollo |
| Timeout o DNS corporativo | Revisa VPN, DNS y conectividad desde Docker Desktop |
| `unhealthy` o contenedor terminado | Ejecuta `docker compose ps -a` y consulta los logs del servicio |
| `OOMKilled` | Usa menos micros simultaneos o aumenta limite y heap |
| Gateway responde 502/503 | Comprueba que el micro de esa ruta esta levantado |
| Cambios Java no aparecen | Ejecuta `build` y luego `up` para ese micro |

Valida Compose sin imprimir secretos:

```powershell
docker compose config --quiet
```

Comprueba si Docker termino un proceso por memoria:

```powershell
docker inspect --format '{{.State.OOMKilled}}' (docker compose ps -a -q ms-cards)
```

Los healthchecks de los micros usan liveness. `healthy` confirma el arranque del
proceso, no el acceso a SQL/LDAP/Keycloak. Prueba los flujos con datos de desarrollo
y tu VPN conectada.

### Error PKIX al descargar dependencias Maven

Puedes colocar la CA real como `corporate-root-ca.cer` en la raiz del repositorio.
El comando `local.ps1 build` la detecta automaticamente sin rutas absolutas.
Consulta `CERTIFICADO.md`. El archivo real no se incluye en la distribucion.

Si `build` muestra `PKIX path building failed` al acceder a Maven Central,
la JVM de compilacion no confia en la cadena TLS recibida. En una red corporativa
esto puede deberse a inspeccion HTTPS del proxy. Los certificados de Windows no
se heredan automaticamente dentro del JDK del contenedor.

Solicita a TI el certificado publico de la CA raiz autorizada del proxy, en formato
X.509 PEM o DER (`.cer`/`.crt`). No uses una clave privada, el certificado de Keycloak
ni un keystore de aplicacion como sustituto de esa CA.

Actualiza `docker/Dockerfile`, `compose.yaml` y `docker/empty-ca.crt` en el equipo
destino con la version corregida de este repositorio. Agrega en tu `.env`:

```dotenv
BUILD_CA_FILE=C:/Desarrollo/repositorio/secrets/corporate-root-ca.cer
```

La ruta puede ser distinta en cada maquina. Debe apuntar a un unico certificado
CA proporcionado/verificado por TI. Se agrega a una copia de las CAs del JDK
solo durante Maven mediante un montaje de compilacion. No desactiva TLS ni
cambia los truststores de los microservicios en ejecucion.

Vuelve a ejecutar `build`. Maven usa `-U` para reintentar dependencias que antes
fallaron. Si cambias el certificado despues de una compilacion exitosa, fuerza
la reconstruccion con `docker compose --parallel 1 build --no-cache`: cambiar el
contenido de un secreto no invalida por si solo la cache de Docker.
Sin CA adicional, deja `BUILD_CA_FILE=./docker/empty-ca.crt`; ese archivo vacio
forma parte del repositorio y debe conservarse al copiarlo.

Si persiste el error, pide a TI revisar la cadena de certificados que entrega el
proxy. No utilices opciones Maven para aceptar certificados sin validacion.
Referencia: [secretos durante la compilacion](https://docs.docker.com/build/building/secrets/).

## 11. Secretos y versionado

`.local/`, `.env` y `secrets/` estan ignorados por Git y excluidos del contexto Docker.
Las claves y certificados se montan en lectura mediante secretos de Compose,
sin incluirlos en las capas de las imagenes. Los `application-local.yml` de IntelliJ
tambien se excluyen de la imagen.

Las variables se cargan desde archivos locales y son visibles para quien tenga
acceso al motor Docker. No compartas `.local/` ni la salida completa de
`docker compose config`. Usa `--quiet` para validar sin imprimir valores.
El `.gitignore` no elimina los secretos que ya estaban versionados ni su historial.

Referencia: [documentacion oficial de Docker Compose](https://docs.docker.com/reference/compose-file/services/).

## 12. Validacion realizada

Se compilaron las diez imagenes con Java 17 y se verifico el arranque saludable de
los once contenedores, incluido Redis. Config Server sirvio propiedades descifradas
y se verifico la apertura del truststore.

La medicion puntual en reposo fue aproximadamente 2 GiB para los contenedores,
sin incluir Docker/WSL. El consumo variara con la carga. Los flujos corporativos
completos no se validaron.
Se usaron las copias de secretos disponibles en el repositorio: la carpeta
`C:\Desarrollo\repositorio\secrets` de tu otra maquina no estaba accesible desde
el entorno de pruebas. Debes inicializar con tus archivos en el equipo destino.
