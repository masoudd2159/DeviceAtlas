#requires -Version 5.1
<#
.SYNOPSIS
Verifies background-process termination and existing-task restoration on an emulator.
.DESCRIPTION
Tries ActivityManager am kill, then a debug app-UID SIGKILL if the OS retains the
background process. Never uses force-stop or clears app data/settings. Requires
an installed debug app and an explicitly selected emulator.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidatePattern('^emulator-[0-9]+$')][string]$Serial,
    [string]$AdbExecutable = 'adb'
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$appId = 'io.github.masoudd2159.deviceatlas'
$resourceRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'app/src/main/res'
$resourceSets = @('values','values-fa') | ForEach-Object {
    [xml][IO.File]::ReadAllText((Join-Path $resourceRoot "$_/strings.xml"), [Text.Encoding]::UTF8)
}
$remoteXml = '/sdcard/deviceatlas-restoration-' + [guid]::NewGuid().ToString('N') + '.xml'

function Invoke-Adb([string[]]$AdbArguments, [switch]$AllowFailure) {
    $result = & $AdbExecutable -s $Serial @AdbArguments 2>&1
    if ($LASTEXITCODE -ne 0 -and -not $AllowFailure) { throw 'adb failed during the isolated app restoration check.' }
    return ($result -join "`n")
}
function Read-AppUi {
    [void](Invoke-Adb @('shell','uiautomator','dump',$remoteXml))
    [xml](Invoke-Adb @('shell','cat',$remoteXml))
}
function Find-Label($Ui, [string]$ResourceName) {
    $labels = @($resourceSets | ForEach-Object { $_.resources.string | Where-Object { $_.name -eq $ResourceName } | ForEach-Object { $_.InnerText } })
    return @($Ui.SelectNodes('//node') | Where-Object { $_.package -eq $appId -and $_.text -in $labels })
}
function Tap-Label([string]$ResourceName) {
    $node = Find-Label (Read-AppUi) $ResourceName | Select-Object -First 1
    if (-not $node) { throw "Required application control missing: $ResourceName" }
    $bounds = [regex]::Match($node.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    $tapX = [int](([int]$bounds.Groups[1].Value + [int]$bounds.Groups[3].Value) / 2)
    $tapY = [int](([int]$bounds.Groups[2].Value + [int]$bounds.Groups[4].Value) / 2)
    [void](Invoke-Adb @('shell','input','tap',"$tapX","$tapY"))
}
function Read-ProcessId { (Invoke-Adb @('shell','pidof',$appId) -AllowFailure).Trim() }
function Read-TaskId {
    $activities = Invoke-Adb @('shell','dumpsys','activity','activities')
    $component = [regex]::Escape($appId) + '/(?:\.|' + [regex]::Escape($appId) + '\.)MainActivity'
    $match = [regex]::Match($activities, 'mResumedActivity:[^\r\n]*' + $component + '[^\r\n]*\bt(\d+)\b')
    if (-not $match.Success) { throw 'Cannot identify the resumed application task; no restoration pass can be claimed.' }
    return $match.Groups[1].Value
}
try {
    [void](Invoke-Adb @('shell','am','start','-W','-n',"$appId/.MainActivity",'-f','0x10200000'))
    Tap-Label 'settings'
    $ui = Read-AppUi
    for ($attempt = 0; $attempt -lt 3 -and -not (Find-Label $ui 'version'); $attempt++) {
        if (Find-Label $ui 'about') { Tap-Label 'about'; break }
        [void](Invoke-Adb @('shell','input','keyevent','4'))
        $ui = Read-AppUi
    }
    if (-not (Find-Label (Read-AppUi) 'version')) { throw 'About subsection was not reached before process termination.' }
    $oldProcessId = Read-ProcessId
    $oldTaskId = Read-TaskId
    if (-not $oldProcessId) { throw 'App process missing before backgrounding.' }
    [void](Invoke-Adb @('shell','input','keyevent','3'))
    $terminationMethod = 'ActivityManager am kill'
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        [void](Invoke-Adb @('shell','am','kill',$appId))
        Start-Sleep -Milliseconds 250
        if (-not (Read-ProcessId)) { break }
    }
    if (Read-ProcessId) {
        if ($oldProcessId -notmatch '^\d+$' -or (Read-ProcessId) -ne $oldProcessId) { throw 'The original app PID is no longer an unambiguous signal target.' }
        # Kernel UID checks restrict this to the debug app's own process, without root.
        [void](Invoke-Adb @('shell','run-as',$appId,'kill','-9',$oldProcessId))
        $terminationMethod = 'debug app-UID SIGKILL after backgrounding'
        Start-Sleep -Milliseconds 250
    }
    if (Read-ProcessId) { throw 'The background app process was not terminated; restoration is unverified.' }
    $history = Invoke-Adb @('shell','dumpsys','activity','activities')
    if ($history -notmatch ([regex]::Escape($appId) + '/[^\r\n]*\bt' + $oldTaskId + '\b')) {
        throw 'The old task was not retained after process termination.'
    }
    [void](Invoke-Adb @('shell','am','start','-W','-n',"$appId/.MainActivity",'-f','0x10200000'))
    $newProcessId = Read-ProcessId
    $newTaskId = Read-TaskId
    if (-not $newProcessId -or $newProcessId -eq $oldProcessId) { throw 'A new app process was not established.' }
    if ($newTaskId -ne $oldTaskId) { throw 'A new task was launched instead of restoring the retained task.' }
    if (-not (Find-Label (Read-AppUi) 'version')) { throw 'The nested About destination was not restored.' }
    Write-Host "PASS: terminated process $oldProcessId, new process $newProcessId, retained task $oldTaskId, nested About restored."
    Write-Host "Termination method: $terminationMethod."
    Write-Host 'This verifies killed-process/existing-task restoration, not force-stop/relaunch or a real low-memory-killer event.'
} finally {
    [void](Invoke-Adb @('shell','rm','-f',$remoteXml) -AllowFailure)
}
