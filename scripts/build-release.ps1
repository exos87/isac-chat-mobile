param(
    [ValidateSet("all", "useitacDev", "usskTest", "usskProd")]
    [string]$Flavor = "all",
    [switch]$BundleOnly
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$propertiesPath = Join-Path $repoRoot "keystore.properties"

if (-not (Test-Path $propertiesPath)) {
    throw "Chyba keystore.properties. Najprv spusti scripts\\generate-release-keystore.ps1."
}

if ($Flavor -eq "all") {
    $assembleTasks = @(
        "assembleUseitacDevRelease",
        "assembleUsskTestRelease",
        "assembleUsskProdRelease"
    )
    $bundleTasks = @(
        "bundleUseitacDevRelease",
        "bundleUsskTestRelease",
        "bundleUsskProdRelease"
    )
} else {
    $variant = switch ($Flavor) {
        "useitacDev" { "UseitacDevRelease" }
        "usskTest" { "UsskTestRelease" }
        "usskProd" { "UsskProdRelease" }
    }
    $assembleTasks = @("assemble$variant")
    $bundleTasks = @("bundle$variant")
}

if ($BundleOnly) {
    & (Join-Path $repoRoot "gradlew.bat") @bundleTasks
} else {
    & (Join-Path $repoRoot "gradlew.bat") @assembleTasks @bundleTasks
}
