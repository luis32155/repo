# Certificado corporativo para Maven

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
