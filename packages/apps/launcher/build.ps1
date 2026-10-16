$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$buildRoot = Join-Path $repoRoot 'out/launcher'
$frameworkClasses = Join-Path $buildRoot 'framework-classes'
$appClasses = Join-Path $buildRoot 'classes'
$apk = Join-Path $repoRoot 'out/system/app/launcher/launcher.apk'
New-Item -ItemType Directory -Force $frameworkClasses, $appClasses, (Split-Path $apk) | Out-Null
# Framework dependencies are compiled separately, not packaged in the application.
$frameworkSources = @(Get-ChildItem (Join-Path $repoRoot 'src/com/android') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$appSources = @(Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& javac -encoding UTF-8 -source 8 -target 8 -cp (Join-Path $repoRoot 'libs/*') -d $frameworkClasses @frameworkSources
if ($LASTEXITCODE -ne 0) { throw 'Framework dependency compilation failed' }
$classpath = $frameworkClasses + [IO.Path]::PathSeparator + (Join-Path $repoRoot 'libs/*')
& javac -encoding UTF-8 -source 8 -target 8 -cp $classpath -sourcepath (Join-Path $PSScriptRoot 'src') -d $appClasses @appSources
if ($LASTEXITCODE -ne 0) { throw 'Launcher compilation failed' }
& jar cf $apk -C $appClasses . -C $PSScriptRoot AndroidManifest.xml -C $PSScriptRoot res
if ($LASTEXITCODE -ne 0) { throw 'Launcher packaging failed' }
Write-Output "Built: $apk"
