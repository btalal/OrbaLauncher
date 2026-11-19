# Disables (never uninstalls) the packages of a debloat list on an Android TV over ADB, and keeps a log.
#   tools\disable.ps1 -TvHost 192.168.1.50 tools\philips-65oled908\debloat.txt
# Before anything changes, the TV's full package list and its already-disabled list are saved in
# backup-<host>-<date>\, so tools\restore.ps1 never re-enables what the firmware had disabled itself.
# Undo with tools\restore.ps1 and the same list.
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [Parameter(Mandatory = $true, Position = 0)][string]$List,
    [switch]$DryRun
)
$ErrorActionPreference = "Stop"
$adb = if ($env:ADB) { $env:ADB } elseif (Get-Command adb -ErrorAction SilentlyContinue) { "adb" } else { "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" }
$target = if ($TvHost -match ':') { $TvHost } else { "${TvHost}:5555" }

# One package per line; everything after '#' is a comment.
$packages = Get-Content -LiteralPath $List | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ }
if (-not $packages) { throw "No package in $List" }

& $adb connect $target | Out-Null
$state = & $adb -s $target get-state 2>$null
if ($state -ne "device") { throw "The TV $target does not answer. Turn on network debugging and accept the prompt on the TV." }

$stamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$backup = if ($DryRun) { Join-Path ([IO.Path]::GetTempPath()) "orba-dryrun-$stamp" } else { Join-Path $PSScriptRoot "..\backup-$($TvHost -replace '[:.]', '-')-$stamp" }
New-Item -ItemType Directory -Force $backup | Out-Null
& $adb -s $target shell pm list packages | Set-Content "$backup\packages-all.txt"
& $adb -s $target shell pm list packages -d | Set-Content "$backup\packages-disabled-before.txt"
$installed = (Get-Content "$backup\packages-all.txt") -replace '^package:', ''
$log = "$backup\log.txt"
"List: $List`nTV: $target`n" | Set-Content $log

foreach ($p in $packages) {
    if ($installed -notcontains $p) { "skip (not installed)  $p" | Tee-Object -FilePath $log -Append; continue }
    if ($DryRun) { "would disable         $p" | Tee-Object -FilePath $log -Append; continue }
    $r = & $adb -s $target shell pm disable-user --user 0 $p 2>&1
    "$(Get-Date -Format HH:mm:ss)  $p  ->  $r" | Tee-Object -FilePath $log -Append
}
Start-Sleep -Seconds 12   # pm writes package-restrictions.xml with a delay: wait before any reboot
& $adb -s $target shell pm list packages -d | Set-Content "$backup\packages-disabled-after.txt"
"Done. Backup and log: $backup`nUndo: tools\restore.ps1 -TvHost $TvHost $List"
