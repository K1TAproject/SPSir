param(
    [switch]$NoBuild,
    [switch]$Headless,
    [switch]$SoftwareEmulation
)
. "$PSScriptRoot\Environment.ps1"
if (-not (Test-Path -LiteralPath $Emulator)) { throw 'Android SDK/emulator is not installed. See README.md.' }
if (-not (Test-Path -LiteralPath "$env:ANDROID_HOME\system-images\android-35\google_apis\x86_64\system.img")) {
    throw 'Install the Android 35 Google APIs x86_64 system image first. See README.md.'
}
if (-not (Test-Path -LiteralPath "$env:ANDROID_AVD_HOME\SPSir_API35.ini")) {
    'no' | & "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat" create avd --name SPSir_API35 --package 'system-images;android-35;google_apis;x86_64' --device pixel_5
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the virtual device.' }
}
$avdConfig = Join-Path $env:ANDROID_AVD_HOME 'SPSir_API35.avd\config.ini'
$config = Get-Content -LiteralPath $avdConfig -Raw
if ($config -match 'hw.keyboard=no') {
    Set-Content -LiteralPath $avdConfig -Value ($config.Replace('hw.keyboard=no', 'hw.keyboard=yes')) -Encoding ASCII
}
& $Adb start-server
$devices = & $Adb devices
if (-not ($devices -match "^$Device\s")) {
    if (-not $SoftwareEmulation) {
        & $Emulator -accel-check
        if ($LASTEXITCODE -ne 0) {
            throw 'Emulator acceleration is unavailable. See README.md. SoftwareEmulation is supported for diagnostics but can be very slow.'
        }
    }
    $arguments = @('-avd', 'SPSir_API35', '-port', '5580', '-no-snapshot', '-no-boot-anim', '-gpu', 'swiftshader', '-memory', '2048', '-cores', '2', '-no-audio')
    if ($Headless) { $arguments += '-no-window' }
    if ($SoftwareEmulation) { $arguments += @('-accel', 'off') }
    $window = if ($Headless) { 'Hidden' } else { 'Normal' }
    Start-Process -FilePath $Emulator -ArgumentList $arguments -WindowStyle $window -RedirectStandardOutput "$ProjectRoot\.local\emulator.log" -RedirectStandardError "$ProjectRoot\.local\emulator-error.log" | Out-Null
}

if (-not $NoBuild) {
    & $Gradle :app:assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Debug build failed.' }
}
$apk = Join-Path $ProjectRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Debug build is missing. Run again without -NoBuild.' }

Write-Host 'Waiting for the Android virtual device...'
$deadline = (Get-Date).AddMinutes(12)
do {
    $connected = & $Adb devices
    $ready = ''
    if ($connected -match "^$Device\s+device$") {
        $ready = (& $Adb -s $Device shell getprop sys.boot_completed 2>$null)
    }
    if ($ready -match '^1') { break }
    if ((Get-Date) -gt $deadline) { throw 'Emulator boot timed out. Inspect .local/emulator-error.log.' }
    Start-Sleep -Seconds 3
} while ($true)
$avdName = & $Adb -s $Device emu avd name
if ($avdName -notcontains 'SPSir_API35') { throw 'Port 5580 belongs to another virtual device; no app was installed.' }
& $Adb -s $Device install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Installing the debug app failed.' }
& $Adb -s $Device shell am start -n com.spsir.ledger/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'Launching the app failed.' }
Write-Host 'The app is running. Edit source and run this script again to update it without clearing data.'
