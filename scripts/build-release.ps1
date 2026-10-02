param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectPath = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$signingPath = $env:MITMACHIM_RELEASE_STORE_FILE
if (-not $signingPath -or -not (Test-Path -LiteralPath $signingPath -PathType Leaf)) {
    throw 'Set MITMACHIM_RELEASE_STORE_FILE to the original release key outside this repository.'
}
$enteredPassword = $false
$passwordPtr = [IntPtr]::Zero
try {
    if (-not $env:MITMACHIM_RELEASE_PASSWORD) {
        $securePassword = Read-Host 'Release key password' -AsSecureString
        $passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        $env:MITMACHIM_RELEASE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
        $enteredPassword = $true
    }
    Push-Location $projectPath
    try {
        $gradleArgs = @(':app:testDebugUnitTest', ':app:assembleRelease', '--no-daemon')
        if ($Offline) { $gradleArgs += '--offline' }
        & .\gradlew.bat @gradleArgs
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed' }
    } finally { Pop-Location }
    $apkPath = Join-Path $projectPath 'app/build/outputs/apk/release/app-release.apk'
    if (-not $env:ANDROID_HOME) { throw 'Set ANDROID_HOME to verify the signed APK.' }
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $buildTools) { throw 'Android build-tools not found' }
    $signature = & (Join-Path $buildTools.FullName 'apksigner.bat') verify --print-certs $apkPath
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
    $actual = ($signature | Select-String 'Signer #1 certificate SHA-256 digest:' | Select-Object -First 1).Line -replace '.*digest:\s*', ''
    if ($actual.ToUpperInvariant() -ne '0AF6F3E3379F1D10FE640537CE186F6288FFF587D9E97E8367C7364344A45F77') {
        throw 'APK does not match the original release signing identity.'
    }
    Write-Host "Verified release APK: $apkPath"
} finally {
    if ($enteredPassword) { Remove-Item Env:MITMACHIM_RELEASE_PASSWORD -ErrorAction SilentlyContinue }
    if ($passwordPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr) }
}
