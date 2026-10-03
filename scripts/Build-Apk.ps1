. "$PSScriptRoot\Environment.ps1"
& $Gradle :app:assembleDebug '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
if ($LASTEXITCODE -ne 0) { throw 'APK build failed. See the build output above.' }
$apkPath = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apkPath)) { throw 'The APK output was not found.' }
$namedApkPath = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\SPSir.apk'
Copy-Item -LiteralPath $apkPath -Destination $namedApkPath -Force
Write-Host "Installable debug APK: $namedApkPath"
Write-Host 'Copy this APK to your Android phone to install it. This script does not start the emulator.'
