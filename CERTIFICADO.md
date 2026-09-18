# Certificado corporativo para Maven

En Windows, puedes intentar exportar automaticamente la CA que tu equipo ya
confia para Maven Central, desde la raiz del proyecto:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\docker\export-maven-ca.ps1
```

Requiere Windows PowerShell 5.1. Valida HTTPS, la cadena y que la raiz pertenezca
al almacen de confianza de Windows; si falla, no escribe el archivo. No genera
una CA nueva ni exporta claves privadas. Usa `-Force` para reemplazar una exportacion
previa. Si Docker usa un proxy distinto de Windows, esta CA puede no ser suficiente:
TI debe confirmar la cadena de esa conexion.

Coloca el certificado publico real de la CA de tu empresa en la raiz del proyecto:

```text
compose.yaml
corporate-root-ca.cer
docker/
```

`docker/local.ps1 build` detecta ese archivo automaticamente. No depende de la letra
del disco ni del nombre de la carpeta. Una variable de entorno `BUILD_CA_FILE`
definida explicitamente tiene prioridad.

Para usar `docker compose build` directamente, agrega a `.env`:

```dotenv
BUILD_CA_FILE=./corporate-root-ca.cer
```

El archivo debe ser un certificado CA X.509 PEM o DER autorizado por tu empresa.
No es una clave RSA privada ni el certificado de Keycloak. No se incluye un
certificado inventado: no resolveria el error PKIX de tu red.

El certificado real aun no fue proporcionado. Solicitalo a TI o exporta la CA
correcta desde el almacen de confianza de tu equipo, tras verificar su identidad.
El `.gitignore` lo excluye del repositorio publico junto con todos los secretos.
