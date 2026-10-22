#requires -Version 5.1
<#
.SYNOPSIS
Creates a local, filtered text-change folder and ZIP for manual review/sharing.
.DESCRIPTION
Combines staged/unstaged changes against Base and copies non-ignored untracked
files. Deletions appear in the patch. Not a backup or an exhaustive secret scanner.
#>
[CmdletBinding()]
param(
    [string]$Base = 'HEAD',
    [string]$OutputParent,
    [ValidateRange(1, 104857600)][long]$MaxFileBytes = 1MB,
    [ValidateRange(1, 104857600)][long]$MaxTotalBytes = 10MB
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false, $true)
$gitExe = (Get-Command git -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$gitDirectory = (Get-Location).ProviderPath

# Capture NUL-delimited names without PowerShell line splitting or locale decoding.
# No shell, external diff, textconv, filesystem-monitor hook or index refresh.
function Invoke-Git([string[]]$GitArguments, [switch]$AllowFailure) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = $gitExe
    $info.Arguments = (($GitArguments | ForEach-Object {
        '"' + [regex]::Replace([regex]::Replace($_, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
    }) -join ' ')
    $info.WorkingDirectory = $script:gitDirectory
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = $utf8
    $info.StandardErrorEncoding = $utf8
    $info.EnvironmentVariables['GIT_OPTIONAL_LOCKS'] = '0'
    $info.EnvironmentVariables['GIT_NO_LAZY_FETCH'] = '1'
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $errorTask = $process.StandardError.ReadToEndAsync()
        $output = $process.StandardOutput.ReadToEnd()
        $process.WaitForExit()
        [void]$errorTask.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0 -and -not $AllowFailure) {
            throw 'Git command failed; check repository access and the requested base revision.'
        }
        [pscustomobject]@{ Text = $output; Code = $process.ExitCode }
    } finally { $process.Dispose() }
}
$root = [IO.Path]::GetFullPath((Invoke-Git @('rev-parse', '--show-toplevel')).Text.Trim())
$gitDirectory = $root
$baseHash = (Invoke-Git @('rev-parse', '--verify', '--end-of-options', ($Base + '^{commit}'))).Text.Trim()
$rootPrefix = $root.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
if (-not $OutputParent) { $OutputParent = Split-Path -Parent $root }
$parentItem = Get-Item -LiteralPath $OutputParent
if (-not $parentItem.PSIsContainer -or $parentItem.PSProvider.Name -ne 'FileSystem') {
    throw 'OutputParent must be an existing filesystem directory outside the repository.'
}
$parentPath = $parentItem.FullName
if ($parentPath -ieq $root -or $parentPath.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputParent must be outside the repository.'
}
for ($ancestor = $parentItem; $null -ne $ancestor; $ancestor = $ancestor.Parent) {
    if ($ancestor.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'OutputParent must not traverse a link or junction.' }
}

# Apply policy even to tracked/force-added files. Do not list excluded names.
function Test-ExcludedPath([string]$Path) {
    $Path -match '(?i)(^|/)(\.git|\.gradle|\.kotlin|\.idea|\.codex|\.agents|\.aws|\.ssh|\.gnupg|build|target|graphify-out|captures|\.cxx|\.externalNativeBuild|node_modules|codex-changes-[^/]*)(/|$)' -or
    $Path -match '(?i)(^|/)(\.env[^/]*|\.git-credentials|\.netrc|\.npmrc|\.pypirc|local\.properties|keystore\.properties|signing\.properties|gradle\.properties|credentials[^/]*|secrets?[^/]*|google-services\.json|hs_err_pid[^/]*)(/|$)' -or
    $Path -match '(?i)\.(jks|keystore|p12|pfx|pem|key|log|hprof|dmp|so|a|o|apk|aab|jar|zip|png|webp|jpg|jpeg|gif|pdf|exe|dll|bin)$'
}
function Test-SensitiveValue([string]$Text) {
    ($Text -match '-----BEGIN (?:[A-Z0-9 ]+ )?PRIVATE KEY-----' -or
        $Text -match '(?i)(?:api[_-]?key|access[_-]?token|auth[_-]?token|password|storePassword|keyPassword|client[_-]?secret)\s*["'']?\s*[:=]\s*["'']?[^\s"'',;}]+' -or
        $Text -match '(?i)\bAuthorization\s*[:=]\s*["'']?(?:Bearer|Basic)\s+\S+' -or
        $Text -match '(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16}|sk-[A-Za-z0-9_-]{20,})' -or
        $Text -match '(?i)\b(?:ANDROID_SERIAL|device[_ -]?serial|serialnumber)\s*[:=]\s*\S+' -or
        $Text -match '\bemulator-\d{4,5}\b|\b[A-Z0-9]{10,}\s+device\b')
}
function Test-PrivateAbsolutePath([string]$Text) {
    # Unix roots start at a token boundary, not inside repository components/URLs.
    # Windows private roots stay conservatively matched, including diagnostic text.
    $Text -match '(?i)[A-Z]:[\\/]+(?:Users|Programming|Softwares)[\\/]' -or
    $Text -match '(?i)(?<![\p{L}\p{N}_./\\-])/(?:home|Users)/[\p{L}\p{N}_.-]+'
}
function Test-RepositoryPath([string]$Path) {
    # Git names are repository-relative; filename policy and secret values still apply.
    -not ([string]::IsNullOrEmpty($Path) -or $Path.Contains([string][char]0) -or
        [IO.Path]::IsPathRooted($Path) -or $Path -match '^[A-Za-z]:|^[\\/]' -or
        $Path -match '(^|[\\/])\.\.([\\/]|$)' -or
        (Test-ExcludedPath $Path) -or (Test-SensitiveValue $Path))
}
function Test-ReviewableText([string]$Text) {
    -not ($Text.Contains([string][char]0) -or (Test-SensitiveValue $Text) -or
        (Test-PrivateAbsolutePath $Text))
}
$changed = (Invoke-Git @('--literal-pathspecs', '-c', 'core.fsmonitor=false', 'diff', '--no-ext-diff', '--no-textconv', '--no-renames', '--name-only', '-z', $baseHash, '--')).Text
$untracked = (Invoke-Git @('-c', 'core.fsmonitor=false', 'ls-files', '--others', '--exclude-standard', '-z')).Text
$files = @((($changed + $untracked).Split([char]0) | Where-Object { $_.Length -gt 0 }) | Sort-Object -Unique)
$accepted = New-Object 'System.Collections.Generic.List[object]'
$skipped = 0
$total = 0L
foreach ($relative in $files) {
    if (-not (Test-RepositoryPath $relative)) { $skipped++; continue }
    $source = [IO.Path]::GetFullPath((Join-Path $root $relative))
    if (-not $source.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe source path.' }
    # Preserve the original policy of excluding the running exporter itself.
    if ($source -ieq $PSCommandPath) { $skipped++; continue }
    $linked = $false
    for ($cursor = $source; $cursor -ine $root; $cursor = Split-Path -Parent $cursor) {
        if ((Test-Path -LiteralPath $cursor) -and ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { $linked = $true; break }
    }
    if ($linked) { $skipped++; continue }
    $exists = Test-Path -LiteralPath $source -PathType Leaf
    if ((Test-Path -LiteralPath $source) -and -not $exists) { $skipped++; continue }
    $blob = $baseHash + ':' + $relative
    $baseEntry = Invoke-Git @('--literal-pathspecs', 'ls-tree', '-z', $baseHash, '--', $relative)
    $hasBase = $baseEntry.Text.Length -gt 0
    # Regular files only, including historical input. A symlink is also a Git blob.
    if ($hasBase -and $baseEntry.Text -notmatch '^100(?:644|755) blob ') { $skipped++; continue }
    $oldSize = 0L
    if ($hasBase) { $oldSize = [long](Invoke-Git @('cat-file', '-s', $blob)).Text.Trim() }
    $newSize = 0L
    if ($exists) { $newSize = (Get-Item -LiteralPath $source).Length }
    if ($oldSize -gt $MaxFileBytes -or $newSize -gt $MaxFileBytes -or $total + $oldSize + $newSize -gt $MaxTotalBytes) { $skipped++; continue }
    try {
        $currentBytes = $null
        $valid = $true
        if ($exists) { $currentBytes = [IO.File]::ReadAllBytes($source); $valid = Test-ReviewableText ($utf8.GetString($currentBytes)) }
        if ($valid -and $hasBase) { $valid = Test-ReviewableText (Invoke-Git @('cat-file', 'blob', $blob)).Text }
    } catch [System.Text.DecoderFallbackException] { $valid = $false }
    if (-not $valid) { $skipped++; continue }
    $total += $oldSize + $newSize
    $accepted.Add([pscustomobject]@{ Relative = $relative; Bytes = $currentBytes; Exists = $exists })
}
$name = 'codex-changes-{0}-{1}-{2}' -f (Split-Path -Leaf $root), (Get-Date -Format 'yyyyMMdd-HHmmss'), ([guid]::NewGuid().ToString('N'))
$destination = Join-Path $parentPath $name
$zipPath = "$destination.zip"
[void](New-Item -Path $destination -ItemType Directory)
$patchPath = Join-Path $destination 'changes.patch'
$partPath = Join-Path $destination 'patch-part.tmp'
$statusLines = New-Object 'System.Collections.Generic.List[string]'
$changeLines = New-Object 'System.Collections.Generic.List[string]'
$copied = 0
try {
    [IO.File]::WriteAllBytes($patchPath, [byte[]]@())
    foreach ($entry in $accepted) {
        $relative = $entry.Relative
        # One literal path at a time avoids the Windows command-line length limit.
        [void](Invoke-Git @('--literal-pathspecs', '-c', 'core.fsmonitor=false', 'diff', '--no-ext-diff', '--no-textconv', '--no-renames', "--output=$partPath", $baseHash, '--', $relative))
        $partBytes = [IO.File]::ReadAllBytes($partPath)
        if (-not (Test-ReviewableText ($utf8.GetString($partBytes)))) { throw 'Patch failed privacy checks or a file changed during export; inspect and retry.' }
        $stream = [IO.File]::Open($patchPath, [IO.FileMode]::Append)
        try { $stream.Write($partBytes, 0, $partBytes.Length) } finally { $stream.Dispose() }
        $status = (Invoke-Git @('--literal-pathspecs', '-c', 'core.fsmonitor=false', 'status', '--short', '--untracked-files=all', '--', $relative)).Text.TrimEnd()
        $change = (Invoke-Git @('--literal-pathspecs', 'diff', '--no-ext-diff', '--no-textconv', '--no-renames', '--name-status', $baseHash, '--', $relative)).Text.TrimEnd()
        if ([string]::IsNullOrWhiteSpace($change) -and $entry.Exists) { $change = "??`t$relative" }
        if (-not (Test-ReviewableText $status) -or -not (Test-ReviewableText $change)) {
            throw 'Git metadata failed privacy checks; inspect and retry.'
        }
        $statusLines.Add($status)
        $changeLines.Add($change)
        if ($entry.Exists) {
            $target = Join-Path (Join-Path $destination 'files') $relative
            [void][IO.Directory]::CreateDirectory((Split-Path -Parent $target))
            [IO.File]::WriteAllBytes($target, $entry.Bytes)
            $copied++
        }
    }
    if (Test-Path -LiteralPath $partPath) { Remove-Item -LiteralPath $partPath }
    [IO.File]::WriteAllLines((Join-Path $destination 'git-status.txt'), $statusLines, $utf8)
    [IO.File]::WriteAllLines((Join-Path $destination 'changed-files.txt'), $changeLines, $utf8)
    [IO.File]::WriteAllLines((Join-Path $destination 'README.txt'), @(
        "Base commit: $baseHash", "Created: $(Get-Date -Format 's')",
        "Exported current files: $copied; accepted paths: $($accepted.Count); excluded paths: $skipped.",
        'files/ contains final working copies; changes.patch contains accepted tracked changes and deletions.',
        'Staged and unstaged content is combined. Untracked files are copies, not patch additions.',
        'Binary/non-UTF-8, sensitive, linked and oversized files are skipped, including from metadata.',
        'This is a filtered review bundle, not a backup. Detection is heuristic: inspect before sharing.',
        'No network upload or remote Git operation was performed.'
    ), $utf8)
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::CreateFromDirectory($destination, $zipPath)
} catch {
    # Delete only this invocation's unique generated output; never repository files.
    if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath }
    if ([IO.Path]::GetFullPath($destination).StartsWith($parentPath.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $destination -Recurse
    }
    throw
}
Write-Host "Exported $copied current files; $skipped paths excluded by policy or size limits."
Write-Host "ZIP: $zipPath" -ForegroundColor Green
Write-Host 'Review files, patch and metadata for private information before sharing.'
