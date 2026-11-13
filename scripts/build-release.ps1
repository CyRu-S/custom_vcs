param([switch]$SkipInstall)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    Push-Location (Join-Path $projectRoot 'frontend')
    try {
        if (!$SkipInstall) {
            & npm.cmd ci
            if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed. Stop Vite before reinstalling dependencies.' }
        }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    } finally { Pop-Location }

    & mvn.cmd compile -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    # Generated static files belong in target, not in source control.
    $targetRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'vcs-server/target')) + '\'
    $staticDirectory = [System.IO.Path]::GetFullPath((Join-Path $targetRoot 'classes/static'))
    if (!$staticDirectory.StartsWith($targetRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Unexpected static build directory.'
    }
    if (Test-Path -LiteralPath $staticDirectory) { Remove-Item -LiteralPath $staticDirectory -Recurse -Force }
    New-Item -ItemType Directory -Path $staticDirectory -Force | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'frontend/dist') | Copy-Item -Destination $staticDirectory -Recurse
    & mvn.cmd package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Release packaging failed.' }
    Write-Output 'Release ready: vcs-server/target/vcs-server-0.0.1-SNAPSHOT.jar'
} finally { Pop-Location }
