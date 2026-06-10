param(
    [string]$KeystorePath = ".tools/signing/isac-chat-mobile-release.jks",
    [string]$PropertiesPath = "keystore.properties",
    [string]$Alias = "isac-chat-mobile",
    [string]$StorePassword = "",
    [string]$KeyPassword = "",
    [string]$DName = "CN=ISAC Chat Mobile, OU=ISAC, O=Onesoft, L=Kosice, ST=Kosicky, C=SK"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedKeystorePath = Join-Path $repoRoot $KeystorePath
$resolvedPropertiesPath = Join-Path $repoRoot $PropertiesPath
$keytoolPath = Join-Path $env:ProgramFiles "Android\Android Studio\jbr\bin\keytool.exe"

if (-not (Test-Path $keytoolPath)) {
    $keytoolPath = "keytool"
}

if ([string]::IsNullOrWhiteSpace($StorePassword)) {
    $StorePassword = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
}

if ([string]::IsNullOrWhiteSpace($KeyPassword)) {
    $KeyPassword = $StorePassword
}

$keystoreDir = Split-Path -Parent $resolvedKeystorePath
if (-not (Test-Path $keystoreDir)) {
    New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null
}

if (-not (Test-Path $resolvedKeystorePath)) {
    & $keytoolPath -genkeypair `
        -v `
        -storetype PKCS12 `
        -keystore $resolvedKeystorePath `
        -alias $Alias `
        -keyalg RSA `
        -keysize 2048 `
        -validity 3650 `
        -storepass $StorePassword `
        -keypass $KeyPassword `
        -dname $DName | Out-Null
}

@"
storeFile=$KeystorePath
storePassword=$StorePassword
keyAlias=$Alias
keyPassword=$KeyPassword
"@ | Set-Content -Path $resolvedPropertiesPath -Encoding ASCII

Write-Output "KEYSTORE_READY"
Write-Output $resolvedKeystorePath
Write-Output $resolvedPropertiesPath
