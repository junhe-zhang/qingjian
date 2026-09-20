param(
    [Parameter(Mandatory=$true)][string]$MakeAppx,
    [string]$IdentityName,
    [string]$Publisher,
    [string]$PublisherDisplayName,
    [switch]$Development
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if ($Development) {
    $IdentityName = 'QingJian.LocalPreview'
    $Publisher = 'CN=QingJian Local Preview'
    $PublisherDisplayName = 'QingJian Preview'
} elseif (!$IdentityName -or !$Publisher -or !$PublisherDisplayName) {
    throw 'Provide the exact Identity Name, Publisher and Publisher Display Name from Partner Center. Use -Development only for a local unsigned preview.'
}
$stage = Join-Path $root ('dist\msix-stage-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $stage 'Assets') -Force | Out-Null
& (Join-Path $root 'build.ps1') -OutputPath (Join-Path $stage 'QingJian.exe')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'QingJian.exe.config') -Destination $stage
Add-Type -AssemblyName System.Drawing
$source = [Drawing.Image]::FromFile((Join-Path $root 'app-icon.png'))
try {
    foreach ($item in @(@('StoreLogo',50),@('Square44x44Logo',44),@('Square150x150Logo',150))) {
        $bitmap = New-Object Drawing.Bitmap($item[1],$item[1])
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        try { $graphics.InterpolationMode = 'HighQualityBicubic'; $graphics.DrawImage($source,0,0,$item[1],$item[1]); $bitmap.Save((Join-Path $stage ('Assets\'+$item[0]+'.png')),[Drawing.Imaging.ImageFormat]::Png) }
        finally { $graphics.Dispose(); $bitmap.Dispose() }
    }
} finally { $source.Dispose() }
$manifest = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'AppxManifest.template.xml'))
$manifest = $manifest.Replace('__IDENTITY__',[Security.SecurityElement]::Escape($IdentityName)).Replace('__PUBLISHER__',[Security.SecurityElement]::Escape($Publisher)).Replace('__PUBLISHER_DISPLAY__',[Security.SecurityElement]::Escape($PublisherDisplayName))
[IO.File]::WriteAllText((Join-Path $stage 'AppxManifest.xml'),$manifest,[Text.UTF8Encoding]::new($false))
$name = if ($Development) { 'QingJian-1.1.0.0-x64-preview-unsigned.msix' } else { 'QingJian-1.1.0.0-x64-store.msix' }
$output = Join-Path $root ('dist\'+$name)
& $MakeAppx pack /d $stage /p $output /o
if ($LASTEXITCODE -ne 0) { throw 'MSIX validation or packaging failed.' }
Write-Output ('MSIX: ' + $output)
if ($Development) { Write-Output 'Local preview identity only. Not signed or associated with a Microsoft Store product.' }
