# Run with Windows PowerShell 5.1 (powershell.exe), not pwsh.
[CmdletBinding()]
param([switch]$Force, [string]$OutputPath = '')
$ErrorActionPreference = 'Stop'
if ($PSVersionTable.PSEdition -ne 'Desktop') {
    throw 'Ejecuta este script con powershell.exe (Windows PowerShell 5.1).'
}
if (-not $OutputPath) { $OutputPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'corporate-root-ca.cer' }
if ((Test-Path -LiteralPath $OutputPath) -and -not $Force) {
    throw 'El certificado ya existe. Usa -Force solo si deseas reemplazarlo.'
}
if ($null -ne [Net.ServicePointManager]::ServerCertificateValidationCallback) {
    throw 'Hay un validador TLS personalizado. Ejecuta en un proceso nuevo con -NoProfile.'
}
$previousProtocol = [Net.ServicePointManager]::SecurityProtocol
$response = $null
$chain = $null
$leaf = $null
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $request = [Net.HttpWebRequest]::Create('https://repo.maven.apache.org/maven2/')
    $request.Method = 'HEAD'
    $request.AllowAutoRedirect = $false
    $request.Timeout = 30000
    # Default Windows proxy and standard TLS/hostname validation remain enabled.
    $response = $request.GetResponse()
    if ([int]$response.StatusCode -ne 200) { throw 'Maven Central no devolvio HTTP 200.' }
    if (-not $request.ServicePoint.Certificate) { throw 'No se pudo obtener el certificado TLS.' }
    $leaf = [Security.Cryptography.X509Certificates.X509Certificate2]::new($request.ServicePoint.Certificate)
    $chain = [Security.Cryptography.X509Certificates.X509Chain]::new()
    $chain.ChainPolicy.UrlRetrievalTimeout = [TimeSpan]::FromSeconds(15)
    if (-not $chain.Build($leaf)) {
        $states = ($chain.ChainStatus | ForEach-Object { $_.Status.ToString() }) -join ', '
        throw "Windows no pudo validar completamente la cadena: $states. Consulta con TI."
    }
    $rootCa = $chain.ChainElements[$chain.ChainElements.Count - 1].Certificate
    $trusted = (Test-Path "Cert:\CurrentUser\Root\$($rootCa.Thumbprint)") -or
               (Test-Path "Cert:\LocalMachine\Root\$($rootCa.Thumbprint)")
    if (-not $trusted) { throw 'La raiz no esta en el almacen Root de Windows. No se exportara.' }
    [IO.File]::WriteAllBytes([IO.Path]::GetFullPath($OutputPath), $rootCa.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert))
    Write-Host "CA exportada: $OutputPath"
    Write-Host "Sujeto: $($rootCa.Subject)"
    Write-Host "Huella: $($rootCa.Thumbprint)"
    Write-Host 'Se exporto solo el certificado publico, sin claves privadas.'
    Write-Host 'Ahora ejecuta local.ps1 build. Si Docker usa otro proxy, TI debe confirmar su CA.'
} finally {
    if ($response) { $response.Close() }
    if ($chain) { $chain.Dispose() }
    if ($leaf) { $leaf.Dispose() }
    [Net.ServicePointManager]::SecurityProtocol = $previousProtocol
}
