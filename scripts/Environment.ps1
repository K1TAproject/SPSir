$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $ProjectRoot

$localJava = Join-Path $ProjectRoot '.local\java-home.txt'
if (Test-Path -LiteralPath $localJava) {
    $env:JAVA_HOME = (Get-Content -LiteralPath $localJava -Raw).Trim()
} elseif (Test-Path -LiteralPath 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe') {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) {
    throw 'Set JAVA_HOME to JDK 17 or newer, or configure .local/java-home.txt. See README.md.'
}
$env:ANDROID_HOME = Join-Path $ProjectRoot '.tools\sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $ProjectRoot '.tools\android-user'
$env:ANDROID_AVD_HOME = Join-Path $ProjectRoot '.tools\avd'
$env:GRADLE_USER_HOME = Join-Path $ProjectRoot '.tools\gradle-home'
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
New-Item -ItemType Directory -Force "$ProjectRoot\.local",$env:ANDROID_USER_HOME,$env:ANDROID_AVD_HOME | Out-Null

$Gradle = Join-Path $ProjectRoot '.tools\gradle-8.13\bin\gradle.bat'
if (-not (Test-Path -LiteralPath $Gradle)) { $Gradle = Join-Path $ProjectRoot 'gradlew.bat' }
$Adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$Emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
$Device = 'emulator-5580'
