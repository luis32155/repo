[CmdletBinding()]
param(
    [ValidateSet('init','build','up','down','ps','logs','restart')]
    [string]$Action = 'up',
    [string[]]$Services = @(),
    [string]$SecretsDirectory = '',
    [string]$ManifestsDirectory = '',
    [switch]$Force
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$allServices = @('config-server','ms-auth-token-generator','ms-auth-token-validator','ms-authorizer','ms-login','ms-maintenance-window','ms-cards','ms-products','ms-publications','api-gateway')
foreach ($service in $Services) {
    if ($service -notin ($allServices + 'redis')) { throw "Servicio desconocido: $service" }
}
function Write-Utf8([string]$Path, [string]$Content) {
    [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
}
function Read-SecretManifest([string]$Path) {
    # The supplied manifests use flat stringData with quoted scalars.
    # Reject other YAML constructs instead of silently corrupting credentials.
    $result = [ordered]@{}
    $inside = $false
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^stringData:\s*$') { $inside = $true; continue }
        if (-not $inside -or $line -match '^\s*(#.*)?$') { continue }
        if ($line -notmatch '^\s{2}([A-Z][A-Z0-9_]+):\s*(.*?)\s*$') { throw "Formato stringData no soportado en $Path" }
        $key = $Matches[1]; $value = $Matches[2]
        if ($value.StartsWith('"')) { $value = ConvertFrom-Json $value }
        elseif ($value.StartsWith("'")) { $value = $value.Substring(1,$value.Length-2).Replace("''", "'") }
        elseif ($value -match '^[|>\[{]') { throw "Escalar no soportado: $key" }
        $result[$key] = [string]$value
    }
    if ($result.Count -eq 0) { throw "Sin stringData: $Path" }
    return $result
}
function Invoke-Compose([string[]]$ComposeArgs) {
    & docker compose --parallel 1 @ComposeArgs
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose fallo (codigo $LASTEXITCODE)." }
}
if ($Action -eq 'init') {
    if (-not $SecretsDirectory) {
        $SecretsDirectory = if ($env:CVD_SECRETS_DIR) { $env:CVD_SECRETS_DIR } else { Join-Path $root 'secrets' }
    }
    $source = Join-Path $root 'environments_variables_and_secrets_develop'
    if ($ManifestsDirectory) { $source = (Resolve-Path -LiteralPath $ManifestsDirectory).Path }
    if (-not (Test-Path -LiteralPath $SecretsDirectory -PathType Container)) { throw "No existe la carpeta de secretos: $SecretsDirectory. Indica -SecretsDirectory con la ruta accesible." }
    if ((Test-Path '.local/env') -and -not $Force) { throw 'Ya existe .local/env. Usa init -Force solo para regenerar los archivos locales.' }
    foreach ($dir in @('.local/env','.local/secrets','.local/config-repo')) { New-Item -ItemType Directory -Force $dir | Out-Null }
    $certificates = @('config-server-keystore.p12','keycloak_public_key.pem','rsa_private_key_without_session.pem','rsa_public_key_without_session.pem','rsa_private_key_with_session.pem','rsa_public_key_with_session.pem','rsa_private_key_frontend.pem','rsa_public_key_frontend.pem')
    foreach ($name in $certificates) {
        if (-not (Test-Path -LiteralPath (Join-Path $SecretsDirectory $name))) { throw "Falta $name en $SecretsDirectory" }
    }
    $trustFile = Join-Path $SecretsDirectory 'truststore.jks'
    if (-not (Test-Path -LiteralPath $trustFile)) { $trustFile = Join-Path $SecretsDirectory 'truststore.p12' }
    if (-not (Test-Path -LiteralPath $trustFile)) { throw 'Falta truststore.jks o truststore.p12.' }
    foreach ($name in $certificates) { Copy-Item -LiteralPath (Join-Path $SecretsDirectory $name) -Destination (Join-Path '.local/secrets' $name) }
    # The application detects PKCS12 or JKS independently of the file extension.
    Copy-Item -LiteralPath $trustFile -Destination '.local/secrets/truststore.p12'
    $configEnv = Read-SecretManifest (Join-Path $source 'config-server-secrets.yml')
    # Reuse the password already configured in the local application, without printing it.
    $localYaml = Get-Content 'ms-cards/src/main/resources/application-local.yml' -Raw
    $match = [regex]::Match($localYaml, '(?m)^  truststore:\s*\r?\n\s+path:.*\r?\n\s+password:\s*(.+)$')
    if ($env:CVD_TRUSTSTORE_PASSWORD) { $trustPassword = $env:CVD_TRUSTSTORE_PASSWORD }
    elseif ($match.Success) { $trustPassword = $match.Groups[1].Value.Trim().Trim('"').Trim("'") }
    else { throw 'Configura CVD_TRUSTSTORE_PASSWORD: no se encontro el password del truststore.' }
    foreach ($service in $allServices) {
        $values = Read-SecretManifest (Join-Path $source "$service-secrets.yml")
        if ($service -eq 'config-server') {
            $values['SPRING_PROFILES_ACTIVE'] = 'native'
            $values['SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS'] = 'file:/config-repo'
            $values['SPRING_CLOUD_CONFIG_SERVER_GIT_CLONE_ON_START'] = 'false'
            $values['ENCRYPT_KEY_STORE_LOCATION'] = 'file:/run/secrets/config-server-keystore.p12'
        } else {
            $values['SPRING_PROFILES_ACTIVE'] = 'docker'
            $values['SPRING_CLOUD_CONFIG_URI'] = 'http://config-server:8888'
            $values['SPRING_CLOUD_CONFIG_USERNAME'] = $configEnv['SPRING_SECURITY_USER_NAME']
            $values['SPRING_CLOUD_CONFIG_PASSWORD'] = $configEnv['SPRING_SECURITY_USER_PASSWORD']
            $values['SPRING_CLOUD_CONFIG_FAIL_FAST'] = 'true'
            $values['SPRING_REDIS_HOST'] = 'redis'
            $values['SPRING_REDIS_PORT'] = '6379'
            $values['SPRING_REDIS_PASSWORD'] = ''
            $values['LOCAL_TRUSTSTORE_PASSWORD'] = $trustPassword
            if ($service -eq 'ms-authorizer') {
                $values['SECURITY_OAUTH2_RESOURCE_KEYCLOAK_PUBLIC_KEY'] = 'file:/run/secrets/keycloak_public_key.pem'
            }
            $keycloakFile = Join-Path $SecretsDirectory 'keycloak_public_key.pem'
            if ((Test-Path -LiteralPath $keycloakFile) -and $values.Contains('KEYCLOAK_PUBLIC_KEY')) {
                $values['KEYCLOAK_PUBLIC_KEY'] = ((Get-Content -LiteralPath $keycloakFile | Where-Object { $_ -notmatch '^-----' }) -join '').Trim()
            }
            foreach ($key in @($values.Keys)) {
                if ($key -match '^RSA_.*_PATH$') { $values[$key] = $values[$key].Replace('/mnt/secrets/', '/run/secrets/') }
            }
            if ($service -eq 'api-gateway') {
                $routes = @{ SERVICE_GENERATE_JWT_URI='ms-auth-token-generator:8086'; SERVICE_VALIDATE_JWT_URI='ms-auth-token-validator:8087'; SERVICE_VALIDATE_MAINT_URI='ms-maintenance-window:8089'; SERVICE_PASSWORD_ENCRYPT_VALIDATE_URI='ms-login:8084'; SERVICE_PUBLICACION_NOAUTH_URI='ms-publications:8091'; SERVICE_PUBLICACION_AUTH_URI='ms-publications:8091'; SERVICE_PRODUCTS_VALIDATE_URI='ms-products:8092'; SERVICE_CARDS_DESCRIPTION_URI='ms-cards:8090'; SERVICE_CARDS_V2_URI='ms-cards:8090'; SERVICE_KEEP_ALIVE_URI='ms-login:8084' }
                foreach ($key in $routes.Keys) { $values[$key] = 'http://' + $routes[$key] }
                $values['GATEWAY_TRUST_INSECURE_SSL'] = 'false'
            }
            $configName = $values['SPRING_APPLICATION_NAME']
            $original = Get-Content -LiteralPath "config-repo/$configName.properties" -Raw
            $overrides = Get-Content 'docker/common.properties' -Raw
            if ($service -eq 'api-gateway') { $overrides += "`n" + (Get-Content 'docker/gateway.properties' -Raw) }
            Write-Utf8 "$root/.local/config-repo/$configName.properties" ($original + "`n" + $overrides + "`nserver.port=" + $values['SERVER_PORT'] + "`n")
        }
        $lines = foreach ($key in $values.Keys) {
            $value = $values[$key]
            if ($value -match '[\r\n]') { throw "Valor multilinea no soportado: $service/$key" }
            # Single quotes prevent Compose from interpolating dollar signs in secrets.
            $escaped = $value.Replace("'", "\'")
            "$key='$escaped'"
        }
        Write-Utf8 "$root/.local/env/$service.env" (($lines -join "`n") + "`n")
    }
    if (-not (Test-Path '.env')) { Copy-Item '.env.example' '.env' }
    Write-Host 'Configuracion local generada. Secretos en .local/ (ignorado por Git).'
    Invoke-Compose @('config','--quiet')
    exit
}
if (-not (Test-Path '.local/env/config-server.env')) { throw 'Ejecuta primero: .\docker\local.ps1 init' }
# A real corporate CA placed in the project root is used automatically for builds.
# An explicitly supplied environment variable takes precedence.
if ($Action -eq 'build' -and -not $env:BUILD_CA_FILE -and (Test-Path 'corporate-root-ca.cer')) {
    if ((Get-Item 'corporate-root-ca.cer').Length -eq 0) { throw 'corporate-root-ca.cer esta vacio. Debe contener el certificado CA real, no un archivo de ejemplo.' }
    $env:BUILD_CA_FILE = (Join-Path $root 'corporate-root-ca.cer')
    Write-Host 'Compilacion: usando la CA corporativa de la raiz del proyecto.'
}
switch ($Action) {
    'build' {
        $selected = if ($Services.Count) { $Services } else { $allServices }
        foreach ($service in $selected) { if ($service -ne 'redis') { Invoke-Compose @('build',$service) } }
    }
    'up' { Invoke-Compose (@('up','-d','--wait','--wait-timeout','240') + $Services) }
    'down' { Invoke-Compose @('down') }
    'ps' { Invoke-Compose @('ps') }
    'logs' { Invoke-Compose (@('logs','--tail','100','-f') + $Services) }
    'restart' { Invoke-Compose (@('restart') + $Services) }
}
