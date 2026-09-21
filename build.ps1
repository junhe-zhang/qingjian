param([string]$OutputPath = (Join-Path $PSScriptRoot 'QingJian.exe'))
$ErrorActionPreference = 'Stop'
$framework = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319'
$refs = @('System.dll','System.Core.dll','System.Xaml.dll','System.Runtime.Serialization.dll','System.Drawing.dll','System.Windows.Forms.dll','WPF\WindowsBase.dll','WPF\PresentationCore.dll','WPF\PresentationFramework.dll')
$arguments = @('/nologo','/target:winexe','/optimize+','/utf8output',('/out:' + $OutputPath),('/win32icon:' + (Join-Path $PSScriptRoot 'app.ico')))
foreach ($ref in $refs) { $arguments += '/reference:' + (Join-Path $framework $ref) }
$arguments += Join-Path $PSScriptRoot 'App.cs'
$arguments += Join-Path $PSScriptRoot 'VirtualDesktopPin.cs'
& (Join-Path $framework 'csc.exe') @arguments
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
Write-Output ('Built: ' + $OutputPath)
