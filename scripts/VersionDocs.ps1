# Shared guard for the build scripts: a build that produces an installer must
# also ship the documentation for that exact version. Details: docs/RELEASE.md.
# Keep this file ASCII-only: Windows PowerShell reads BOM-less scripts as ANSI.
$ErrorActionPreference = 'Stop'

function Get-DeclaredVersion {
    param([string]$Root = (Split-Path $PSScriptRoot -Parent))
    $gradle = Join-Path $Root 'app\build.gradle.kts'
    $text = Get-Content -LiteralPath $gradle -Raw -Encoding UTF8
    $version = [regex]::Match($text, 'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"').Groups[1].Value
    $code = [regex]::Match($text, 'versionCode\s*=\s*([0-9]+)').Groups[1].Value
    if (-not $version -or -not $code) { throw 'Could not read versionName/versionCode from app/build.gradle.kts.' }
    [pscustomobject]@{ Version = $version; VersionCode = [int]$code }
}

function Assert-VersionDocs {
    param(
        [Parameter(Mandatory)][string]$Version,
        [string]$Root = (Split-Path $PSScriptRoot -Parent)
    )
    $escape = [regex]::Escape($Version)
    $problems = New-Object System.Collections.Generic.List[string]

    $readmePath = Join-Path $Root 'README.md'
    if (-not (Test-Path -LiteralPath $readmePath)) {
        $problems.Add('README.md is missing.')
    } else {
        $readme = Get-Content -LiteralPath $readmePath -Raw -Encoding UTF8
        $firstLine = ($readme -split "`r?`n", 2)[0]
        if ($firstLine -notmatch $escape) {
            $problems.Add("README.md line 1 does not mention $Version (found: $firstLine).")
        }
        if ($readme -notmatch "SPSir-$escape\.apk") {
            $problems.Add("README.md does not mention the current debug package SPSir-$Version.apk.")
        }
    }

    $notesPath = Join-Path $Root "docs\RELEASE-NOTES-$Version.md"
    if (-not (Test-Path -LiteralPath $notesPath)) {
        $problems.Add("User-facing release notes docs/RELEASE-NOTES-$Version.md are missing.")
    } else {
        $notes = Get-Content -LiteralPath $notesPath -Raw -Encoding UTF8
        $notesTitle = ($notes -split "`r?`n", 2)[0]
        if ([string]::IsNullOrWhiteSpace($notes) -or $notesTitle -notmatch $escape) {
            $problems.Add("docs/RELEASE-NOTES-$Version.md is empty or its heading does not mention $Version.")
        }
    }

    if ($problems.Count -gt 0) {
        throw ("Version documentation is out of date, the installer was not generated:`n  - " +
            ($problems -join "`n  - ") +
            "`nUpdate README.md and docs/RELEASE-NOTES-$Version.md first, then build again. See docs/RELEASE.md.")
    }

    $buildDoc = Join-Path $Root "docs\BUILD-$Version.md"
    if (-not (Test-Path -LiteralPath $buildDoc)) {
        Write-Warning "docs/BUILD-$Version.md (verification record) is missing; add test and signing results before publishing."
    }
    $releaseDoc = Join-Path $Root 'docs\RELEASE.md'
    if ((Test-Path -LiteralPath $releaseDoc) -and
        ((Get-Content -LiteralPath $releaseDoc -Raw -Encoding UTF8) -notmatch "$escape / versionCode")) {
        Write-Warning "docs/RELEASE.md does not declare current version $Version / versionCode."
    }
    $installDoc = Join-Path $Root 'docs\INSTALL-ANDROID.md'
    if ((Test-Path -LiteralPath $installDoc) -and
        ((Get-Content -LiteralPath $installDoc -Raw -Encoding UTF8) -notmatch "SPSir-$escape\.apk")) {
        Write-Warning "docs/INSTALL-ANDROID.md does not mention SPSir-$Version.apk."
    }
}
