. "$PSScriptRoot\Environment.ps1"
. "$PSScriptRoot\VersionDocs.ps1"
$docVersion = (Get-DeclaredVersion -Root $ProjectRoot).Version
Assert-VersionDocs -Version $docVersion -Root $ProjectRoot
& $Gradle :app:assembleDebug '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
if ($LASTEXITCODE -ne 0) { throw 'APK build failed. See the build output above.' }
$apkPath = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apkPath)) { throw 'The APK output was not found.' }
$metadata = Get-Content -LiteralPath (Join-Path (Split-Path $apkPath) 'output-metadata.json') -Raw | ConvertFrom-Json
$version = $metadata.elements[0].versionName
if ([string]::IsNullOrWhiteSpace($version) -or $version.IndexOfAny([IO.Path]::GetInvalidFileNameChars()) -ge 0) { throw 'Invalid APK version name.' }
if ($version -ne $docVersion) { throw "Build version $version does not match app/build.gradle.kts ($docVersion)." }
$namedApkPath = Join-Path (Split-Path $apkPath) "SPSir-$version.apk"
Copy-Item -LiteralPath $apkPath -Destination $namedApkPath -Force
Write-Host "Installable debug APK: $namedApkPath"
Write-Host 'Copy this APK to your Android phone to install it. This script does not start the emulator.'
