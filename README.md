# CVD Docker local

Configuracion Docker portable para superponer sobre el repositorio privado de microservicios.
Este repositorio publico contiene solo infraestructura y documentacion: no incluye
codigo de los microservicios, config-repo de desarrollo, credenciales ni certificados.
No arranca como proyecto independiente: copia estos archivos en la raiz de tu
repositorio privado, conservando las carpetas de servicios y configuracion existentes.

## Inicio

Desde la raiz de tu repositorio privado, con Docker Desktop iniciado:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\docker\local.ps1 init -SecretsDirectory "C:\Desarrollo\repositorio\secrets"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\docker\local.ps1 build
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\docker\local.ps1 up
```

Adapta la carpeta de secretos a tu equipo. Los puertos y limites se configuran en `.env`.

Si Maven falla con PKIX, coloca la CA corporativa real en la raiz como
`corporate-root-ca.cer`. El script de compilacion la detecta automaticamente.
La CA aun debe obtenerse de tu empresa; no viene incluida y no puede sustituirse
por un certificado inventado. Ver [CERTIFICADO.md](CERTIFICADO.md).

[Manual completo](MANUAL_DOCKER.md).
