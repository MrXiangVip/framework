param([string]$FrameworkClasses)
$ErrorActionPreference = 'Stop'
if ($FrameworkClasses) { & "$PSScriptRoot/../build.ps1" tonghuashun -FrameworkClasses $FrameworkClasses }
else { & "$PSScriptRoot/../build.ps1" tonghuashun }
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
