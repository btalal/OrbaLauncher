# Re-enables the packages of a debloat list on an Android TV over ADB (the undo of tools\disable.ps1).
#   tools\restore.ps1 -TvHost 192.168.1.50 tools\philips-65oled908\debloat.txt
# Packages the firmware had disabled itself are left alone when the backup taken by disable.ps1 is found.
param(
    [Parameter(Mandatory = $true)][string]$TvHost,
    [Parameter(Mandatory = $true, Position = 0)][string]$List
)
$ErrorActionPreference = "Stop"
$adb = if ($env:ADB) { $env:ADB } elseif (Get-Command adb -ErrorAction SilentlyContinue) { "adb" } else { "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" }
$target = if ($TvHost -match ':') { $TvHost } else { "${TvHost}:5555" }

$packages = Get-Content -LiteralPath $List | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ }
& $adb connect $target | Out-Null
if ((& $adb -s $target get-state 2>$null) -ne "device") { throw "The TV $target does not answer." }

# The oldest backup for this TV says what was disabled before any list was applied.
$keepOff = @()
$first = Get-ChildItem (Join-Path $PSScriptRoot "..") -Directory -Filter "backup-$($TvHost -replace '[:.]', '-')-*" -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -First 1
if ($first -and (Test-Path "$($first.FullName)\packages-disabled-before.txt")) {
    $keepOff = (Get-Content "$($first.FullName)\packages-disabled-before.txt") -replace '^package:', ''
}
foreach ($p in $packages) {
    if ($keepOff -contains $p) { "left disabled (was off before)  $p"; continue }
    $r = & $adb -s $target shell pm enable $p 2>&1
    "$p  ->  $r"
}
"Done."
