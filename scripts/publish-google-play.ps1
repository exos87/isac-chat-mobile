param(
    [ValidateSet("useitacDev")]
    [string]$Flavor = "useitacDev",
    [string]$Track = "internal",
    [string]$ServiceAccountJsonPath = "",
    [string]$ReleaseName = "",
    [string]$ReleaseNotes = "",
    [int]$VersionCode = 0,
    [string]$VersionName = "0.1.10",
    [ValidateSet("draft", "completed", "halted", "inProgress")]
    [string]$ReleaseStatus = "draft",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

function Get-DefaultVersionCode {
    $now = [System.TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, "Central Europe Standard Time")
    $yearPart = $now.Year % 100
    return ($yearPart * 10000000) + ($now.DayOfYear * 10000) + ($now.Hour * 100) + $now.Minute
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"
$keystorePropertiesPath = Join-Path $repoRoot "keystore.properties"
$releaseNotesPath = Join-Path $repoRoot "app\src\useitacDev\play\release-notes\sk-SK\internal.txt"

if (-not (Test-Path $gradleWrapper)) {
    throw "Gradle wrapper sa nenasiel na $gradleWrapper."
}

if (-not (Test-Path $keystorePropertiesPath)) {
    throw "Chyba keystore.properties. Najprv spusti scripts\generate-release-keystore.ps1."
}

$credentialsContent = $env:ANDROID_PUBLISHER_CREDENTIALS
if ([string]::IsNullOrWhiteSpace($credentialsContent)) {
    $resolvedCredentialsPath = $ServiceAccountJsonPath
    if ([string]::IsNullOrWhiteSpace($resolvedCredentialsPath)) {
        $resolvedCredentialsPath = $env:USEITAC_DEV_PLAY_SERVICE_ACCOUNT_JSON
    }
    if ([string]::IsNullOrWhiteSpace($resolvedCredentialsPath)) {
        throw "Chyba service account JSON. Pouzi -ServiceAccountJsonPath alebo nastav USEITAC_DEV_PLAY_SERVICE_ACCOUNT_JSON."
    }
    if (-not (Test-Path $resolvedCredentialsPath)) {
        throw "Service account JSON sa nenasiel na $resolvedCredentialsPath."
    }
    $credentialsContent = Get-Content $resolvedCredentialsPath -Raw
}

$env:ANDROID_PUBLISHER_CREDENTIALS = $credentialsContent
$resolvedVersionCode = if ($VersionCode -gt 0) { $VersionCode } else { Get-DefaultVersionCode }
$resolvedVersionName = if ([string]::IsNullOrWhiteSpace($VersionName)) { "0.1.10" } else { $VersionName }
$env:ISAC_MOBILE_VERSION_CODE = "$resolvedVersionCode"
$env:ISAC_MOBILE_VERSION_NAME = $resolvedVersionName

if ([string]::IsNullOrWhiteSpace($ReleaseName)) {
    $ReleaseName = "UseIT Chat $resolvedVersionName"
}

if (-not [string]::IsNullOrWhiteSpace($ReleaseNotes)) {
    $releaseNotesDir = Split-Path -Parent $releaseNotesPath
    if (-not (Test-Path $releaseNotesDir)) {
        New-Item -ItemType Directory -Force -Path $releaseNotesDir | Out-Null
    }
    Set-Content -Path $releaseNotesPath -Value $ReleaseNotes -Encoding UTF8
}

$variantTask = switch ($Flavor) {
    "useitacDev" { "publishUseitacDevReleaseBundle" }
}

$gradleArgs = @($variantTask, "--track", $Track, "--release-name", $ReleaseName, "--release-status", $ReleaseStatus)
if ($SkipBuild) {
    $artifactDir = Join-Path $repoRoot "app\build\outputs\bundle\useitacDevRelease"
    if (-not (Test-Path $artifactDir)) {
        throw "AAB artefakt sa nenasiel na $artifactDir. Bez -SkipBuild alebo najprv spusti bundleUseitacDevRelease."
    }
    $gradleArgs += @("--artifact-dir", $artifactDir)
}

& $gradleWrapper @gradleArgs
