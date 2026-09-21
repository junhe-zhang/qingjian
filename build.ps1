param([string]$OutputPath = (Join-Path $PSScriptRoot 'QingJian.exe'))
$ErrorActionPreference = 'Stop'
$framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
$refs = @('System.dll','System.Core.dll','System.Security.dll','System.Xaml.dll','System.Runtime.Serialization.dll','System.Drawing.dll','System.Windows.Forms.dll','WPF\WindowsBase.dll','WPF\PresentationCore.dll','WPF\PresentationFramework.dll')
$arguments = @('/nologo','/target:winexe','/optimize+','/utf8output',('/out:' + $OutputPath),('/win32icon:' + (Join-Path $PSScriptRoot 'app.ico')))
foreach ($ref in $refs) { $arguments += '/reference:' + (Join-Path $framework $ref) }
$arguments += Join-Path $PSScriptRoot 'App.cs'
$arguments += Join-Path $PSScriptRoot 'VirtualDesktopPin.cs'
$arguments += Join-Path $PSScriptRoot 'SyncModel.cs'
$arguments += Join-Path $PSScriptRoot 'WebDavSync.cs'
$arguments += Join-Path $PSScriptRoot 'SyncView.cs'
$arguments += Join-Path $PSScriptRoot 'SyncTests.cs'
& (Join-Path $framework 'csc.exe') @arguments
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'windows\QingJian.exe.config') -Destination ($OutputPath + '.config') -Force
Write-Output ('Built: ' + $OutputPath)
