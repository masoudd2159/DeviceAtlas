#requires -Version 5.1
# Standalone regression checks. Only creates/mutates a disposable Git repository.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$exporter = Join-Path (Split-Path -Parent $PSScriptRoot) 'export-codex-changes.ps1'
$sandbox = Join-Path ([IO.Path]::GetTempPath()) ('deviceatlas-export-test-' + [guid]::NewGuid().ToString('N'))
$repo = Join-Path $sandbox 'sample project [literal]'
$output = Join-Path $sandbox 'exports [literal]'
$utf8 = New-Object Text.UTF8Encoding($false)
$assertions = 0
function Assert([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "FAILED: $Message" }
    $script:assertions++
}
function Write-Sample([string]$Path, [string]$Text) {
    $full = Join-Path $repo $Path
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $full))
    [IO.File]::WriteAllText($full, $Text, $utf8)
}
function Invoke-FixtureGit([string[]]$GitArgs) {
    $result = & git -c core.autocrlf=false -C $repo @GitArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Fixture Git command failed.' }
    $result
}
try {
    [void][IO.Directory]::CreateDirectory($repo)
    [void][IO.Directory]::CreateDirectory($output)
    Invoke-FixtureGit @('init', '-q')
    Write-Sample 'staged.txt' 'before'
    Write-Sample 'unstaged.txt' 'before'
    Write-Sample 'deleted.txt' 'removed text'
    Write-Sample 'old name.txt' 'rename me'
    Write-Sample 'old-sensitive.txt' ('password' + '=test-private-value')
    Write-Sample 'removed-secret.txt' ('api_key' + '=test-private-removed')
    Write-Sample 'absolute-history.txt' ('/' + 'home/example/private/settings.json')
    $safePaths = @(
        'app/src/main/java/io/github/masoudd2159/deviceatlas/presentation/home/HomeScreen.kt',
        'docs/architecture/overview.md', 'README.md', 'presentation/home/HomeScreen.kt'
    )
    foreach ($safePath in $safePaths) { Write-Sample $safePath 'original safe source' }
    # A Git symlink blob can be created without OS symlink privileges.
    $linkBlobFile = Join-Path $sandbox 'link-blob.txt'
    [IO.File]::WriteAllText($linkBlobFile, 'relative-target', $utf8)
    $linkBlob = (Invoke-FixtureGit @('hash-object','-w',$linkBlobFile)).Trim()
    Write-Sample '.gitignore' "ignored/`n"
    Invoke-FixtureGit @('add', '--all')
    Invoke-FixtureGit @('update-index','--add','--cacheinfo',"120000,$linkBlob,historical-link.txt")
    Invoke-FixtureGit @('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'baseline')
    $base = (Invoke-FixtureGit @('rev-parse', 'HEAD')).Trim()
    Write-Sample 'committed.txt' 'committed after the selected base'
    Invoke-FixtureGit @('add', '--', 'committed.txt')
    Invoke-FixtureGit @('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'later change')
    $expectedHead = (Invoke-FixtureGit @('rev-parse', 'HEAD')).Trim()
    Write-Sample 'staged.txt' 'staged version'
    Invoke-FixtureGit @('add', '--', 'staged.txt')
    Write-Sample 'staged.txt' 'final working version'
    Write-Sample 'unstaged.txt' 'new working version'
    Remove-Item -LiteralPath (Join-Path $repo 'deleted.txt')
    Invoke-FixtureGit @('mv', '--', 'old name.txt', 'new name.txt')
    Write-Sample 'old-sensitive.txt' 'now harmless'
    Remove-Item -LiteralPath (Join-Path $repo 'removed-secret.txt')
    foreach ($safePath in $safePaths) { Write-Sample $safePath ("safe relative references:`n" + ($safePaths -join "`n") + "`nRoot-name documentation: " + [char]96 + '/' + 'home/' + [char]96 + "`nHonest structural device pages.") }
    Write-Sample 'untracked-safe.md' ('See ' + 'presentation/home/HomeScreen.kt')
    Write-Sample 'absolute-history.txt' 'now safe; private base must remain excluded'
    Write-Sample 'historical-link.txt' 'regular file replacing historical link'
    $unicode = 'new [literal] ' + [char]0x00e9 + ' ' + [char]0x0645 + '.txt'
    Write-Sample $unicode ('UTF-8 ' + [char]0x0645)
    Write-Sample 'README.txt' 'source README must not collide with bundle metadata'
    Write-Sample 'empty.txt' ''
    Write-Sample 'local.properties' 'private-local-setting'
    Write-Sample '.env' 'private-environment'
    Write-Sample '.git-credentials' 'private-credential-store'
    Write-Sample 'header.txt' ('Authorization' + ': Bearer test-private-value')
    Write-Sample 'password=test-private-filename.txt' 'ordinary content'
    Write-Sample 'release.keystore' 'private-signing'
    Write-Sample 'build/generated.txt' 'generated-build'
    Write-Sample 'native/target/generated.txt' 'generated-native'
    Write-Sample 'graphify-out/graph.json' '{}'
    Write-Sample 'ignored/no.txt' 'ignored-content'
    Write-Sample 'innocent.txt' ('access_token' + '=test-private-token')
    Write-Sample 'machine.txt' ('C:' + '\Users\Fixture\private')
    $privateExamples = @{
        'windows-project.txt' = ('D:' + '\Programming\private-project\secret.txt');
        'windows-forward.txt' = ('C:' + '/Users/example/private/settings.json');
        'embedded-windows.txt' = ('diagnostic-prefixC:' + '\Users\example\private\settings.json');
        'adb-uppercase.txt' = ('ZX1234567890' + ' device');
        'adb-lowercase.txt' = ('test123456789' + ' device');
        'adb-table.txt' = ('abcdefghijklmnop' + "`tdevice");
        'unix-home.txt' = ('/' + 'home/example/private/settings.json');
        'unix-users.txt' = ('/' + 'Users/example/private/settings.json');
        'quoted-unix.txt' = ('path="/' + 'home/example/private/settings.json"');
        'markdown-unix.txt' = ('[private](/' + 'Users/example/private/settings.json)');
        'api-key.txt' = ('api_key' + '=test-private-key');
        'private-key.txt' = ('-----BEGIN ' + 'PRIVATE KEY-----' + "`ntest-private-key");
        'provider-token.txt' = ('ghp_' + ('Z' * 30));
        'deleted-history.txt' = ('before ' + '/' + 'home/example/private/settings.json')
    }
    foreach ($privateName in $privateExamples.Keys) { Write-Sample $privateName $privateExamples[$privateName] }
    $linkTarget = Join-Path $sandbox 'private-target'
    [void][IO.Directory]::CreateDirectory($linkTarget)
    [IO.File]::WriteAllText((Join-Path $linkTarget 'private.txt'), 'test-private-linked', $utf8)
    $linkedNames = @()
    $outputLink = Join-Path $sandbox 'output-link'
    if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
        $outputLinkTarget = Join-Path $sandbox 'output-link-target'
        [void][IO.Directory]::CreateDirectory($outputLinkTarget)
        [void](New-Item -ItemType Junction -Path (Join-Path $repo 'linked') -Target $linkTarget)
        [void](New-Item -ItemType Junction -Path $outputLink -Target $outputLinkTarget)
        $linkedNames += 'linked/private.txt'
    }
    try {
        [void](New-Item -ItemType SymbolicLink -Path (Join-Path $repo 'symbolic.txt') -Target (Join-Path $linkTarget 'private.txt') -ErrorAction Stop)
        $linkedNames += 'symbolic.txt'
        Write-Host 'Symbolic-link exclusion test available.'
    } catch {
        Write-Host 'Symbolic-link creation BLOCKED by host permissions; junction exclusion is tested on Windows.'
    }
    Write-Sample 'device.txt' ('ANDROID_' + 'SERIAL=private-device')
    Write-Sample 'large.txt' ('x' * 4096)
    [IO.File]::WriteAllBytes((Join-Path $repo 'binary.txt'), [byte[]]@(65, 0, 66))
    [IO.File]::WriteAllBytes((Join-Path $repo 'invalid.txt'), [byte[]]@(255, 254, 253))
    Copy-Item -LiteralPath $exporter -Destination (Join-Path $repo 'export-codex-changes.ps1')
    # Force-added generated/configuration files must still be excluded.
    Invoke-FixtureGit @('add', '-f', '--', 'local.properties', 'build/generated.txt')
    $before = (Invoke-FixtureGit @('status', '--porcelain=v1', '--untracked-files=all')) -join "`n"
    $indexHash = (Get-FileHash -LiteralPath (Join-Path $repo '.git/index')).Hash
    $locationBefore = Get-Location
    Push-Location -LiteralPath $repo
    try {
        & (Join-Path $repo 'export-codex-changes.ps1') -Base $base -OutputParent $output -MaxFileBytes 1024
        Assert ((Get-Location).Path -eq $repo) 'caller location preserved'
        & (Join-Path $repo 'export-codex-changes.ps1') -Base HEAD -OutputParent $output -MaxFileBytes 1024
        $invalidFailed = $false
        try { & (Join-Path $repo 'export-codex-changes.ps1') -Base '--bad-base' -OutputParent $output } catch { $invalidFailed = $true }
        Assert $invalidFailed 'invalid base rejected'
        $insideFailed = $false
        try { & (Join-Path $repo 'export-codex-changes.ps1') -OutputParent $repo } catch { $insideFailed = $true }
        Assert $insideFailed 'output inside repository rejected'
        if (Test-Path -LiteralPath $outputLink) {
            $junctionFailed = $false
            try { & (Join-Path $repo 'export-codex-changes.ps1') -OutputParent $outputLink } catch { $junctionFailed = $true }
            Assert $junctionFailed 'output through a junction rejected'
        }
    } finally { Pop-Location }
    Assert ((Get-Location).Path -eq $locationBefore.Path) 'test location restored'
    Assert (((Invoke-FixtureGit @('status', '--porcelain=v1', '--untracked-files=all')) -join "`n") -ceq $before) 'Git working status preserved'
    Assert ((Get-FileHash -LiteralPath (Join-Path $repo '.git/index')).Hash -eq $indexHash) 'index byte-for-byte preserved'
    Assert ((Invoke-FixtureGit @('rev-parse', 'HEAD')).Trim() -eq $expectedHead) 'HEAD preserved'
    $bundles = @(Get-ChildItem -LiteralPath $output -Directory)
    Assert ($bundles.Count -eq 2) 'unique output directories without overwrite'
    Assert (@(Get-ChildItem -LiteralPath $output -Filter '*.zip').Count -eq 2) 'two ZIP archives created'
    foreach ($bundle in $bundles) {
        $copies = Join-Path $bundle.FullName 'files'
        Assert ([IO.File]::ReadAllText((Join-Path $copies 'staged.txt')) -ceq 'final working version') 'final staged plus unstaged content'
        Assert ([IO.File]::ReadAllText((Join-Path $copies $unicode), $utf8) -ceq ('UTF-8 ' + [char]0x0645)) 'Unicode and literal bracket paths'
        Assert (Test-Path -LiteralPath (Join-Path $copies 'README.txt')) 'metadata does not overwrite source README'
        Assert ((Get-Item -LiteralPath (Join-Path $copies 'empty.txt')).Length -eq 0) 'empty text file copied'
        $bundleInfo = [IO.File]::ReadAllText((Join-Path $bundle.FullName 'README.txt'))
        Assert ((Test-Path -LiteralPath (Join-Path $copies 'committed.txt')) -eq $bundleInfo.Contains($base)) 'base revision controls committed change inclusion'
        Assert (-not (Test-Path -LiteralPath (Join-Path $copies 'export-codex-changes.ps1'))) 'running helper excluded'
        foreach ($excluded in @('local.properties', '.env', '.git-credentials', 'header.txt', 'password=test-private-filename.txt', 'release.keystore', 'build/generated.txt', 'native/target/generated.txt', 'graphify-out/graph.json', 'ignored/no.txt', 'innocent.txt', 'old-sensitive.txt', 'machine.txt', 'device.txt', 'large.txt', 'binary.txt', 'invalid.txt')) {
            Assert (-not (Test-Path -LiteralPath (Join-Path $copies $excluded))) "policy excludes $excluded"
        }
        foreach ($excluded in @($privateExamples.Keys) + @('removed-secret.txt','absolute-history.txt','historical-link.txt') + $linkedNames) {
            Assert (-not (Test-Path -LiteralPath (Join-Path $copies $excluded))) "privacy/link policy excludes $excluded"
        }
        $patch = [IO.File]::ReadAllText((Join-Path $bundle.FullName 'changes.patch'), $utf8)
        Assert ($patch.Contains('deleted file mode') -and $patch.Contains('deleted.txt')) 'tracked deletion represented'
        Assert ($patch.Contains('old name.txt') -and $patch.Contains('new name.txt')) 'rename represented as delete/add'
        Assert ($patch.Contains('final working version')) 'patch contains final working version'
        $metadata = [IO.File]::ReadAllText((Join-Path $bundle.FullName 'changed-files.txt'), $utf8)
        foreach ($safePath in $safePaths) {
            Assert (Test-Path -LiteralPath (Join-Path $copies $safePath)) "safe source copied: $safePath"
            Assert ($patch.Contains($safePath)) "safe source appears in patch: $safePath"
            Assert ($metadata.Contains($safePath)) "safe source appears in metadata: $safePath"
        }
        Assert (Test-Path -LiteralPath (Join-Path $copies 'untracked-safe.md')) 'relative reference in untracked documentation copied'
        Assert ($metadata.Contains('untracked-safe.md')) 'untracked accepted file recorded in changed metadata'
        foreach ($excluded in @($privateExamples.Keys) + @('removed-secret.txt', 'old-sensitive.txt','absolute-history.txt','historical-link.txt') + $linkedNames) {
            Assert (-not $patch.Contains($excluded)) "excluded path absent from patch: $excluded"
            Assert (-not $metadata.Contains($excluded)) "excluded path absent from changed metadata: $excluded"
        }
        $expectedCopies = @('staged.txt','unstaged.txt','new name.txt',$unicode,'README.txt','empty.txt','untracked-safe.md') + $safePaths
        $expectedPatchPaths = @('staged.txt','unstaged.txt','deleted.txt','old name.txt','new name.txt') + $safePaths
        if ($bundleInfo.Contains($base)) { $expectedCopies += 'committed.txt'; $expectedPatchPaths += 'committed.txt' }
        $actualCopies = @(Get-ChildItem -LiteralPath $copies -Recurse -File | ForEach-Object { $_.FullName.Substring($copies.Length + 1).Replace('\','/') })
        Assert ((($actualCopies | Sort-Object) -join "`n") -ceq (($expectedCopies | Sort-Object) -join "`n")) 'complete files inventory matches every accepted current path'
        $patchPaths = @(Invoke-FixtureGit @('apply','--numstat',(Join-Path $bundle.FullName 'changes.patch')) | ForEach-Object { ($_ -split "`t")[-1] })
        Assert ((($patchPaths | Sort-Object) -join "`n") -ceq (($expectedPatchPaths | Sort-Object) -join "`n")) 'complete patch inventory matches accepted tracked paths including deletions'
        $allText = (Get-ChildItem -LiteralPath $bundle.FullName -Recurse -File | ForEach-Object { [IO.File]::ReadAllText($_.FullName, $utf8) }) -join "`n"
        Assert (-not $allText.Contains('test-private')) 'secrets excluded from current and historical content'
        Assert (-not $allText.Contains('old-sensitive.txt')) 'excluded path absent from metadata'
        Assert (-not $allText.Contains($repo)) 'absolute project path absent'
        foreach ($privateValue in $privateExamples.Values) {
            Assert (-not $allText.Contains($privateValue)) 'private content absent from files, patch and metadata'
        }
        $zip = [IO.Compression.ZipFile]::OpenRead(($bundle.FullName + '.zip'))
        try { Assert ($zip.Entries.Count -gt 4) 'ZIP contains files and metadata' } finally { $zip.Dispose() }
    }
    Push-Location -LiteralPath $repo
    try { & (Join-Path $repo 'export-codex-changes.ps1') -OutputParent $output -MaxTotalBytes 1 } finally { Pop-Location }
    $emptyBundle = Get-ChildItem -LiteralPath $output -Directory | Where-Object { $_.FullName -notin $bundles.FullName }
    $budgetBytes = (Get-ChildItem -LiteralPath (Join-Path $emptyBundle.FullName 'files') -Recurse -File | Measure-Object Length -Sum).Sum
    Assert ($budgetBytes -le 1) 'total input byte budget enforced (empty files cost zero)'
    Assert (-not (Test-Path -LiteralPath (Join-Path $emptyBundle.FullName 'files/staged.txt'))) 'oversized combined input excluded'
    # The helper must remain a local Git/filesystem tool, without network commands.
    $parseErrors = $null
    $tokens = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile($exporter, [ref]$tokens, [ref]$parseErrors)
    Assert ($parseErrors.Count -eq 0) 'export helper parses without errors'
    $commands = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.CommandAst] }, $true) | ForEach-Object { $_.GetCommandName() })
    Assert (@($commands | Where-Object { $_ -in @('Invoke-WebRequest','Invoke-RestMethod','curl','wget','ssh','scp') }).Count -eq 0) 'export helper contains no network commands'
    Write-Host "PASS: $assertions export regression assertions on PowerShell $($PSVersionTable.PSVersion)."
} finally {
    # Only the unique fixture directory created here is eligible for cleanup.
    $tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    if ([IO.Path]::GetFullPath($sandbox).StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $sandbox)) {
        Remove-Item -LiteralPath $sandbox -Recurse -Force
    }
}
