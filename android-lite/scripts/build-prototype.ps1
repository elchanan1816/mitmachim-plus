param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectPath = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$workPath = (Resolve-Path (Join-Path $projectPath '..')).Path
$env:JAVA_HOME = (Resolve-Path (Join-Path $workPath 'toolchain\jdk')).Path
$env:ANDROID_HOME = (Resolve-Path (Join-Path $workPath 'toolchain\android-sdk')).Path
$env:ANDROID_USER_HOME = (Resolve-Path (Join-Path $workPath 'toolchain\android-user')).Path
$env:ANDROID_SDK_HOME = $null
$env:GRADLE_USER_HOME = (Resolve-Path (Join-Path $workPath 'gradle-home')).Path
$buildUserPath = (Resolve-Path (Join-Path $workPath 'android-build-user')).Path
$env:JAVA_TOOL_OPTIONS = '-Duser.home=' + $buildUserPath
$arguments = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', ':app:assembleDebugAndroidTest', '--no-daemon', '--console=plain')
if ($Offline) { $arguments += '--offline' }
Push-Location $projectPath
try {
    & .\gradlew.bat @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Prototype build failed' }
    $apkPath = Join-Path $projectPath 'app\build\outputs\apk\debug\app-debug.apk'
    & (Join-Path $env:ANDROID_HOME 'build-tools\36.0.0\apksigner.bat') verify --min-sdk-version 19 $apkPath
    if ($LASTEXITCODE -ne 0) { throw 'Prototype APK signature check failed' }
    $outputPath = Join-Path $projectPath 'outputs'
    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
    Copy-Item -LiteralPath $apkPath -Destination (Join-Path $outputPath 'mitmachim-lite-0.1-prototype-debug.apk')
    Write-Host 'Debug prototype ready. Not a distribution release.'
} finally { Pop-Location }
