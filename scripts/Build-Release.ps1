. "$PSScriptRoot\Environment.ps1"
if (-not (Test-Path -LiteralPath "$ProjectRoot\.local\release-signing.properties")) {
    throw 'Release signing is missing. Restore your existing key and .local/release-signing.properties. See docs/RELEASE.md.'
}
& $Gradle :app:assembleRelease '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
$output = Join-Path $ProjectRoot 'app\build\outputs\apk\release'
$metadata = Get-Content -LiteralPath (Join-Path $output 'output-metadata.json') -Raw | ConvertFrom-Json
$element = $metadata.elements[0]
$version = $element.versionName
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Expected a stable x.y.z version.' }
$apk = Join-Path $output $element.outputFile
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify $apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$name = "SPSir-$version.apk"
$destination = Join-Path $output $name
Copy-Item -LiteralPath $apk -Destination $destination -Force
$update = [ordered]@{
    applicationId = $metadata.applicationId
    versionCode = $element.versionCode
    versionName = $version
    minSdk = $metadata.minSdkVersionForDexing
    apkName = $name
    sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
}
[IO.File]::WriteAllText((Join-Path $output 'update.json'), ($update | ConvertTo-Json), (New-Object Text.UTF8Encoding($false)))
Write-Host "Release APK: $destination"
Write-Host "Upload this APK and $output\update.json together to GitHub Release v$version."
