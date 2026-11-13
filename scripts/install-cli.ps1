$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$commandDirectory = (Resolve-Path -LiteralPath (Join-Path $projectRoot 'bin')).Path
$jar = Join-Path $projectRoot 'vcs-cli/target/vcs-cli-0.0.1-SNAPSHOT.jar'

if (!(Test-Path -LiteralPath $jar)) {
    throw 'Build the CLI first: mvn -pl vcs-cli -am package -DskipTests'
}

# Add only this launcher directory; leave all existing user PATH entries intact.
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$userEntries = @($userPath -split ';' | Where-Object { $_ })
$alreadyInstalled = $userEntries | Where-Object {
    [Environment]::ExpandEnvironmentVariables($_).TrimEnd('\') -ieq $commandDirectory.TrimEnd('\')
}
if (!$alreadyInstalled) {
    $updatedPath = if ([string]::IsNullOrEmpty($userPath)) { $commandDirectory } else { $userPath.TrimEnd(';') + ';' + $commandDirectory }
    [Environment]::SetEnvironmentVariable('Path', $updatedPath, 'User')
}

# Also enable it in the PowerShell process running this installer.
if (!(@($env:Path -split ';') | Where-Object { $_.TrimEnd('\') -ieq $commandDirectory.TrimEnd('\') })) {
    $env:Path += ';' + $commandDirectory
}
Write-Output 'Installed vcs for your Windows account. No administrator access is needed.'
Write-Output 'Restart VS Code or your terminal once so new sessions inherit the updated PATH.'
& (Join-Path $commandDirectory 'vcs.cmd') --version
if ($LASTEXITCODE -ne 0) { throw 'The launcher was installed, but Java could not run the CLI.' }
