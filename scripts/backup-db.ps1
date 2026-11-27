<#
.SYNOPSIS
  Windows PowerShell equivalent of scripts/backup-db.sh: a consistent, gzip-compressed
  mariadb-dump with daily/weekly retention and an optional S3 copy.

.DESCRIPTION
  Reads the same environment variables as backup-db.sh (parameters override them):
    DB_NAME (required), DB_HOST=127.0.0.1, DB_PORT=3306, DB_USER=root,
    DB_PASSWORD or DB_PASSWORD_FILE (never put on a command line),
    BACKUP_DIR=.\backups, BACKUP_KEEP_DAILY=14, BACKUP_KEEP_WEEKLY=8, BACKUP_WEEKLY_DAY=7 (ISO, 7=Sunday),
    BACKUP_S3_BUCKET, BACKUP_S3_PREFIX=nirvana/db, BACKUP_S3_ENDPOINT, AWS_REGION=ap-south-1,
    MARIADB_BIN = folder holding mariadb-dump.exe when it is not on PATH.
  Files and checksums are interchangeable with the Linux script.
  Exit codes: 0 ok, 1 backup failed, 2 backup kept locally but the S3 upload failed.

.EXAMPLE
  $env:DB_PASSWORD_FILE = "$HOME\.nirvana\db-root.txt"
  .\scripts\backup-db.ps1 -DbName nirvana_spa_test -DbPort 3307
#>
[CmdletBinding()]
param(
    [string]$DbName = $env:DB_NAME,
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }),
    [int]$DbPort = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$DbUser = $(if ($env:DB_USER) { $env:DB_USER } else { 'root' }),
    [string]$BackupDir = $(if ($env:BACKUP_DIR) { $env:BACKUP_DIR } else { '.\backups' }),
    [int]$KeepDaily = $(if ($env:BACKUP_KEEP_DAILY) { [int]$env:BACKUP_KEEP_DAILY } else { 14 }),
    [int]$KeepWeekly = $(if ($env:BACKUP_KEEP_WEEKLY) { [int]$env:BACKUP_KEEP_WEEKLY } else { 8 }),
    [int]$WeeklyDay = $(if ($env:BACKUP_WEEKLY_DAY) { [int]$env:BACKUP_WEEKLY_DAY } else { 7 })
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

function Write-Log([string]$Message) {
    [Console]::Error.WriteLine(('{0} backup: {1}' -f (Get-Date -Format 'yyyy-MM-ddTHH:mm:sszzz'), $Message))
}
function Stop-Backup([string]$Message) { Write-Log "ERROR: $Message"; exit 1 }

function Find-MariaDbTool([string]$Name) {
    if ($env:MARIADB_BIN) {
        $candidate = Join-Path $env:MARIADB_BIN "$Name.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command "$Name.exe" -ErrorAction SilentlyContinue
    if ($null -ne $cmd) { return $cmd.Source }
    Stop-Backup "$Name.exe not found; set MARIADB_BIN to the MariaDB bin folder"
}

function Get-DbPassword {
    if ($env:DB_PASSWORD_FILE) {
        if (-not (Test-Path $env:DB_PASSWORD_FILE)) { Stop-Backup 'cannot read DB_PASSWORD_FILE' }
        return ([IO.File]::ReadAllText($env:DB_PASSWORD_FILE)).TrimEnd("`r", "`n")
    }
    if ($null -ne $env:DB_PASSWORD) { return $env:DB_PASSWORD }
    Stop-Backup 'set DB_PASSWORD or DB_PASSWORD_FILE'
}

# A private option file keeps the password off the command line.
function New-ClientConfig([string]$Dir) {
    $pw = (Get-DbPassword).Replace('\', '\\').Replace('"', '\"')
    $path = Join-Path $Dir 'client.cnf'
    $text = "[client]`nhost=$DbHost`nport=$DbPort`nuser=$DbUser`npassword=`"$pw`"`ndefault-character-set=utf8mb4`n"
    [IO.File]::WriteAllText($path, $text, (New-Object Text.UTF8Encoding($false)))
    return $path
}

function Get-Sha256Line([string]$Path) {
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
    return "$hash  $([IO.Path]::GetFileName($Path))`n"
}

function Send-ToS3([string]$File, [string]$Key) {
    $aws = Get-Command aws -ErrorAction SilentlyContinue
    if ($null -ne $aws) {
        $awsArgs = @('s3', 'cp', $File, "s3://$($env:BACKUP_S3_BUCKET)/$Key", '--only-show-errors', '--sse', 'AES256')
        if ($env:BACKUP_S3_ENDPOINT) { $awsArgs += @('--endpoint-url', $env:BACKUP_S3_ENDPOINT) }
        & $aws.Source @awsArgs
        return ($LASTEXITCODE -eq 0)
    }
    if (-not $env:AWS_ACCESS_KEY_ID -or -not $env:AWS_SECRET_ACCESS_KEY) {
        Write-Log 'no aws CLI and no AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY'
        return $false
    }
    $region = if ($env:AWS_REGION) { $env:AWS_REGION } elseif ($env:AWS_DEFAULT_REGION) { $env:AWS_DEFAULT_REGION } else { 'ap-south-1' }
    if ($env:BACKUP_S3_ENDPOINT) { $base = "$($env:BACKUP_S3_ENDPOINT.TrimEnd('/'))/$($env:BACKUP_S3_BUCKET)" }
    else { $base = "https://$($env:BACKUP_S3_BUCKET).s3.$region.amazonaws.com" }
    # Credentials reach curl on stdin (-K -), not on its command line.
    $cfg = "user = `"$($env:AWS_ACCESS_KEY_ID):$($env:AWS_SECRET_ACCESS_KEY)`"`n"
    if ($env:AWS_SESSION_TOKEN) { $cfg += "header = `"x-amz-security-token: $($env:AWS_SESSION_TOKEN)`"`n" }
    $null = $cfg | & curl.exe -fsS --retry 3 --retry-delay 5 -K - --aws-sigv4 "aws:amz:${region}:s3" `
        -H 'x-amz-server-side-encryption: AES256' -T $File "$base/$Key"
    return ($LASTEXITCODE -eq 0)
}

if (-not $DbName) { Stop-Backup 'DB_NAME is required' }
if ($DbName -notmatch '^[A-Za-z0-9_]+$') { Stop-Backup 'DB_NAME may only contain letters, digits and _' }
if ($KeepDaily -lt 1 -or $KeepWeekly -lt 0) { Stop-Backup 'retention counts must be whole numbers (daily >= 1)' }

$dump = Find-MariaDbTool 'mariadb-dump'
$work = Join-Path ([IO.Path]::GetTempPath()) ("nirvana-backup-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
$daily = Join-Path $BackupDir 'daily'
$weekly = Join-Path $BackupDir 'weekly'
New-Item -ItemType Directory -Force -Path $daily, $weekly | Out-Null
$daily = (Resolve-Path $daily).Path
$weekly = (Resolve-Path $weekly).Path

$name = '{0}-{1}.sql.gz' -f $DbName, (Get-Date).ToUniversalTime().ToString("yyyyMMdd'T'HHmmss'Z'")
$final = Join-Path $daily $name
$partial = Join-Path $daily ".$name.partial"
try {
    $cnf = New-ClientConfig $work
    $sqlFile = Join-Path $work 'dump.sql'

    Write-Log "dumping $DbName from ${DbHost}:$DbPort"
    & $dump "--defaults-extra-file=$cnf" --single-transaction --quick --routines --triggers --events `
        --hex-blob --default-character-set=utf8mb4 --no-tablespaces "--result-file=$sqlFile" $DbName
    if ($LASTEXITCODE -ne 0) { Stop-Backup "mariadb-dump failed with exit code $LASTEXITCODE" }
    $last = Get-Content -LiteralPath $sqlFile -Tail 1
    if ($last -notmatch '^-- Dump completed') { Stop-Backup 'dump is incomplete (no completion marker)' }

    $in = [IO.File]::OpenRead($sqlFile)
    $out = [IO.File]::Create($partial)
    $gz = New-Object IO.Compression.GZipStream($out, [IO.Compression.CompressionLevel]::Optimal)
    try { $in.CopyTo($gz) } finally { $gz.Dispose(); $out.Dispose(); $in.Dispose() }

    Move-Item -LiteralPath $partial -Destination $final
    [IO.File]::WriteAllText("$final.sha256", (Get-Sha256Line $final), (New-Object Text.UTF8Encoding($false)))
    Write-Log "wrote $final ($((Get-Item -LiteralPath $final).Length) bytes)"
}
finally {
    Remove-Item -Recurse -Force -LiteralPath $work -ErrorAction SilentlyContinue
    Remove-Item -Force -LiteralPath $partial -ErrorAction SilentlyContinue
}

$pattern = '^' + [regex]::Escape($DbName) + '-\d{8}T\d{6}Z\.sql\.gz$'
$isoDay = [int](Get-Date).DayOfWeek
if ($isoDay -eq 0) { $isoDay = 7 }
$haveWeekly = @(Get-ChildItem -LiteralPath $weekly -File | Where-Object { $_.Name -match $pattern }).Count -gt 0
$madeWeekly = $false
if ($KeepWeekly -gt 0 -and ($isoDay -eq $WeeklyDay -or -not $haveWeekly)) {
    Copy-Item -LiteralPath $final, "$final.sha256" -Destination $weekly
    $madeWeekly = $true
    Write-Log 'kept as weekly backup'
}

function Remove-OldBackups([string]$Dir, [int]$Keep) {
    Get-ChildItem -LiteralPath $Dir -File | Where-Object { $_.Name -match $pattern } |
        Sort-Object Name -Descending | Select-Object -Skip $Keep | ForEach-Object {
            Remove-Item -LiteralPath $_.FullName, "$($_.FullName).sha256" -Force -ErrorAction SilentlyContinue
            Write-Log "removed old backup $($_.FullName)"
        }
}
Remove-OldBackups $daily $KeepDaily
Remove-OldBackups $weekly $KeepWeekly

[IO.File]::WriteAllText((Join-Path $BackupDir 'last-local-success'), (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss'Z'") + "`n")

if ($env:BACKUP_REQUIRE_REMOTE -eq 'true' -and -not $env:BACKUP_S3_BUCKET) { Write-Log 'ERROR: remote backup required; set BACKUP_S3_BUCKET'; exit 2 }
if ($env:BACKUP_S3_BUCKET) {
    $prefix = if ($env:BACKUP_S3_PREFIX) { $env:BACKUP_S3_PREFIX.TrimEnd('/') } else { 'nirvana/db' }
    $ok = (Send-ToS3 $final "$prefix/daily/$name") -and (Send-ToS3 "$final.sha256" "$prefix/daily/$name.sha256")
    if ($ok -and $madeWeekly) {
        $ok = (Send-ToS3 $final "$prefix/weekly/$name") -and (Send-ToS3 "$final.sha256" "$prefix/weekly/$name.sha256")
    }
    if (-not $ok) { Write-Log "ERROR: S3 upload failed; the local backup $final is kept"; exit 2 }
    Copy-Item -LiteralPath (Join-Path $BackupDir 'last-local-success') -Destination (Join-Path $BackupDir 'last-remote-success') -Force
    Write-Log "uploaded to s3://$($env:BACKUP_S3_BUCKET)/$prefix/"
}
Copy-Item -LiteralPath (Join-Path $BackupDir 'last-local-success') -Destination (Join-Path $BackupDir 'last-success') -Force
Write-Log 'done'
exit 0
