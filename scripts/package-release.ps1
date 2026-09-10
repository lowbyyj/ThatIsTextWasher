[CmdletBinding()]
param(
    [string]$SigningDirectory = (Join-Path $env:LOCALAPPDATA 'TextWasher\signing'),
    [string]$SdkDirectory = $env:ANDROID_HOME,
    [string]$JavaDirectory,
    [string]$BuildToolsVersion = '36.0.0'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repository = Split-Path -Parent $PSScriptRoot
if (-not $SdkDirectory) { $SdkDirectory = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not $JavaDirectory) {
    $studioJava = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    $JavaDirectory = if (Test-Path -LiteralPath $studioJava) { $studioJava } else { $env:JAVA_HOME }
}
if (-not $JavaDirectory) { throw 'Set -JavaDirectory to a JDK 17 or newer installation.' }
$apksigner = Join-Path $SdkDirectory "build-tools\$BuildToolsVersion\apksigner.bat"
$keystore = Join-Path $SigningDirectory 'text-washer-release.p12'
$passwordFile = Join-Path $SigningDirectory 'password.dpapi'
$lineage = Join-Path $repository 'signing\release-lineage.bin'
$certificate = Join-Path $repository 'signing\release-certificate.pem'
foreach ($required in @($apksigner, $keystore, $passwordFile, $lineage, $certificate)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Missing release signing input: $required. Restore the existing key; do not generate a replacement."
    }
}

$expectedCertificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($certificate)
$expectedFingerprint = $expectedCertificate.GetCertHashString([System.Security.Cryptography.HashAlgorithmName]::SHA256).ToLowerInvariant()
$expectedCertificate.Dispose()
$previousPassword = $env:TEXT_WASHER_RELEASE_PASSWORD
$previousJavaHome = $env:JAVA_HOME
$secret = $null
Push-Location $repository
try {
    $env:JAVA_HOME = $JavaDirectory
    $secret = Get-Content -LiteralPath $passwordFile -Raw | ConvertTo-SecureString
    $env:TEXT_WASHER_RELEASE_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
    & .\gradlew.bat -q --console=plain assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }

    $metadata = Get-Content -LiteralPath 'app\build\outputs\apk\release\output-metadata.json' -Raw | ConvertFrom-Json
    $version = $metadata.elements[0].versionName
    $unsigned = Join-Path 'app\build\outputs\apk\release' $metadata.elements[0].outputFile
    New-Item -ItemType Directory -Path 'artifacts' -Force | Out-Null
    $destination = Join-Path 'artifacts' "Text-Washer-$version.apk"

    # The app requires API 29+. A v3 proof of rotation permits an in-place upgrade
    # from the development certificate while all supported OS versions use the new key.
    & $apksigner sign --ks $keystore --ks-key-alias text-washer-release `
        --ks-pass env:TEXT_WASHER_RELEASE_PASSWORD --key-pass env:TEXT_WASHER_RELEASE_PASSWORD `
        --lineage $lineage --rotation-min-sdk-version 28 `
        --v1-signing-enabled false --v2-signing-enabled false --v3-signing-enabled true `
        --v4-signing-enabled false --out $destination $unsigned
    if ($LASTEXITCODE -ne 0) { throw 'Release signing failed.' }
    $verification = & $apksigner verify --verbose --print-certs --min-sdk-version 29 $destination
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    if (($verification -join "`n") -notmatch [regex]::Escape("Signer #1 certificate SHA-256 digest: $expectedFingerprint")) {
        throw 'The APK signer does not match the pinned public release certificate.'
    }
    $verification | Write-Output

    Copy-Item -LiteralPath $destination -Destination 'artifacts\Text-Washer.apk' -Force
    # v4 signatures are not published; remove stale sidecars from earlier local builds.
    foreach ($sidecar in @("$destination.idsig", 'artifacts\Text-Washer.apk.idsig')) {
        if (Test-Path -LiteralPath $sidecar) { Remove-Item -LiteralPath $sidecar }
    }
    $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    [System.IO.File]::WriteAllText((Join-Path $repository 'artifacts\SHA256SUMS.txt'), "$hash  Text-Washer.apk`n")
    Write-Output "Release ready: $destination"
} finally {
    $env:TEXT_WASHER_RELEASE_PASSWORD = $previousPassword
    $env:JAVA_HOME = $previousJavaHome
    if ($null -ne $secret) { $secret.Dispose() }
    Pop-Location
}
