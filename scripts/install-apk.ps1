param(
    [string]$DeviceId = "",
    [ValidateSet("useitacDev", "usskTest", "usskProd")]
    [string]$Flavor = "useitacDev",
    [ValidateSet("debug", "release")]
    [string]$Variant = "release"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adbPath)) {
    $adbPath = "adb"
}

$apkPath = if ($Variant -eq "debug") {
    Join-Path $repoRoot ("app\build\outputs\apk\{0}\debug\app-{0}-debug.apk" -f $Flavor)
} else {
    Join-Path $repoRoot ("app\build\outputs\apk\{0}\release\app-{0}-release.apk" -f $Flavor)
}

if (-not (Test-Path $apkPath)) {
    throw "APK sa nenaslo na $apkPath. Najprv sprav build pre flavor $Flavor a variant $Variant."
}

$adbArgs = @()
if (-not [string]::IsNullOrWhiteSpace($DeviceId)) {
    $adbArgs += @("-s", $DeviceId)
}

& $adbPath @adbArgs install -r $apkPath
