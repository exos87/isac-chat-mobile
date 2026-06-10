param(
    [string]$DeviceId = "emulator-5554",
    [string]$Flavor = "useitacDev",
    [string]$BaseUrl = "https://useitac.onesoft.sk/chat-backend/",
    [string]$WsUrl = "wss://useitac.onesoft.sk/chat-backend/ws/chat",
    [string]$ProfileApiUrl = "https://useitac.onesoft.sk/backend",
    [string]$ApiType = "private",
    [string]$Token = "",
    [string]$Username = "",
    [string]$Password = "",
    [switch]$InstallOnly
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$apkPath = Join-Path $repoRoot "app\build\outputs\apk\$Flavor\debug\app-$Flavor-debug.apk"
$adbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path $adbPath)) {
    $adbPath = "adb"
}

if (-not (Test-Path $apkPath)) {
    throw "APK sa nenaslo na $apkPath. Najprv spusti assembleDebug."
}

if ([string]::IsNullOrWhiteSpace($Token) -and -not $InstallOnly) {
    if (-not [string]::IsNullOrWhiteSpace($Username) -and -not [string]::IsNullOrWhiteSpace($Password)) {
        $tokenResponse = Invoke-RestMethod `
            -Uri "$($ProfileApiUrl.TrimEnd('/'))/auth/private" `
            -Method Post `
            -Body (@{
                username = $Username
                password = $Password
            } | ConvertTo-Json) `
            -ContentType "application/json"
        $Token = $tokenResponse.accessToken
    } else {
        $tokenResponse = Invoke-RestMethod `
            -Uri "http://localhost:8080/auth/realms/ISAC-Test/protocol/openid-connect/token" `
            -Method Post `
            -Body @{
                grant_type = "client_credentials"
                client_id = "isac-backend"
                client_secret = "871e8368-6c38-4f57-95e3-8497695c41a0"
            } `
            -ContentType "application/x-www-form-urlencoded"
        $Token = $tokenResponse.access_token
    }
}

& $adbPath -s $DeviceId install -r $apkPath | Out-Null

if ($InstallOnly) {
    Write-Output "APK_INSTALLED"
    exit 0
}

& $adbPath -s $DeviceId shell pm clear sk.uss.isac.chat.mobile | Out-Null
& $adbPath -s $DeviceId shell am start `
    -n sk.uss.isac.chat.mobile/.app.MainActivity `
    --es debug_base_url $BaseUrl `
    --es debug_ws_url $WsUrl `
    --es debug_x_api_type $ApiType `
    --es debug_profile_api_url $ProfileApiUrl `
    --es debug_access_token $Token | Out-Null

Start-Sleep -Seconds 4

$uiDumpDevicePath = "/sdcard/isac-chat-mobile-view.xml"
$uiDumpLocalPath = Join-Path $repoRoot "tmp\isac-chat-mobile-view.xml"
$uiDumpLocalDir = Split-Path -Parent $uiDumpLocalPath
if (-not (Test-Path $uiDumpLocalDir)) {
    New-Item -ItemType Directory -Force -Path $uiDumpLocalDir | Out-Null
}

& $adbPath -s $DeviceId shell uiautomator dump $uiDumpDevicePath | Out-Null
& $adbPath -s $DeviceId pull $uiDumpDevicePath $uiDumpLocalPath | Out-Null

Write-Output "BOOTSTRAP_DONE"
Write-Output $uiDumpLocalPath
