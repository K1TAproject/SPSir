. "$PSScriptRoot\Environment.ps1"
& $Gradle :app:testDebugUnitTest :app:lintDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Checks failed.' }
