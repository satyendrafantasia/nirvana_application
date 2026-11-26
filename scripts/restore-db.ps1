<#
.SYNOPSIS
  Windows PowerShell equivalent of scripts/restore-db.sh: restores a backup-db backup into a
  database and prints per-table row counts, optionally comparing them with another database.

.DESCRIPTION
  Connection settings come from DB_HOST, DB_PORT, DB_USER and DB_PASSWORD or DB_PASSWORD_FILE
  (parameters override them). MARIADB_BIN is the folder holding mariadb.exe when it is not on PATH.
  Exit codes: 0 ok, 1 failed, 3 row counts differ from -CompareWith.

.EXAMPLE
  .\scripts\restore-db.ps1 -File .\backups\daily\nirvana-20261127T210000Z.sql.gz -Target nirvana_drill -CompareWith nirvana
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$File,
    [Parameter(Mandatory = $true)][string]$Target,
    [string]$CompareWith,
    [switch]$Force,
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { '127.0.0.1' }),
    [int]$DbPort = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$DbUser = $(if ($env:DB_USER) { $env:DB_USER } else { 'root' })
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version 2.0

function Write-Log([string]$Message) {
    [Console]::Error.WriteLine(('{0} restore: {1}' -f (Get-Date -Format 'yyyy-MM-ddTHH:mm:sszzz'), $Message))
}
function Stop-Restore([string]$Message) { Write-Log "ERROR: $Message"; exit 1 }

foreach ($db in @($Target, $CompareWith)) {
    if ($db -and $db -notmatch '^[A-Za-z0-9_]+$') { Stop-Restore 'database names may only contain letters, digits and _' }
}
if (-not (Test-Path -LiteralPath $File)) { Stop-Restore "cannot read $File" }
$File = (Resolve-Path -LiteralPath $File).Path

$client = $null
if ($env:MARIADB_BIN -and (Test-Path (Join-Path $env:MARIADB_BIN 'mariadb.exe'))) { $client = Join-Path $env:MARIADB_BIN 'mariadb.exe' }
elseif (Get-Command mariadb.exe -ErrorAction SilentlyContinue) { $client = (Get-Command mariadb.exe).Source }
else { Stop-Restore 'mariadb.exe not found; set MARIADB_BIN to the MariaDB bin folder' }

if ($env:DB_PASSWORD_FILE) {
    if (-not (Test-Path $env:DB_PASSWORD_FILE)) { Stop-Restore 'cannot read DB_PASSWORD_FILE' }
    $pw = ([IO.File]::ReadAllText($env:DB_PASSWORD_FILE)).TrimEnd("`r", "`n")
}
elseif ($null -ne $env:DB_PASSWORD) { $pw = $env:DB_PASSWORD }
else { Stop-Restore 'set DB_PASSWORD or DB_PASSWORD_FILE' }

$work = Join-Path ([IO.Path]::GetTempPath()) ("nirvana-restore-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
$utf8 = New-Object Text.UTF8Encoding($false)
try {
    $cnf = Join-Path $work 'client.cnf'
    $pwEsc = $pw.Replace('\', '\\').Replace('"', '\"')
    [IO.File]::WriteAllText($cnf, "[client]`nhost=$DbHost`nport=$DbPort`nuser=$DbUser`npassword=`"$pwEsc`"`ndefault-character-set=utf8mb4`n", $utf8)
    Remove-Variable pw, pwEsc

    function Invoke-Sql([string]$Query, [string]$Database) {
        $sqlArgs = @("--defaults-extra-file=$cnf", '-N', '-B')
        if ($Database) { $sqlArgs += $Database }
        $sqlArgs += @('-e', $Query)
        $result = & $client @sqlArgs
        if ($LASTEXITCODE -ne 0) { Stop-Restore "query failed: $Query" }
        return $result
    }

    if (Test-Path -LiteralPath "$File.sha256") {
        $expected = ((Get-Content -LiteralPath "$File.sha256" -TotalCount 1) -split '\s+')[0]
        $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $File).Hash
        if ($expected -ne $actual.ToLowerInvariant()) { Stop-Restore "checksum mismatch for $File" }
        Write-Log 'checksum ok'
    }

    $sqlFile = Join-Path $work 'restore.sql'
    $in = [IO.File]::OpenRead($File)
    $gz = New-Object IO.Compression.GZipStream($in, [IO.Compression.CompressionMode]::Decompress)
    $out = [IO.File]::Create($sqlFile)
    try { $gz.CopyTo($out) } catch { Stop-Restore "$File is not a valid gzip file" } finally { $out.Dispose(); $gz.Dispose(); $in.Dispose() }
    if ((Get-Content -LiteralPath $sqlFile -Tail 1) -notmatch '^-- Dump completed') { Stop-Restore "$File is an incomplete dump" }

    $tables = [int](Invoke-Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Target'")
    if ($tables -ne 0) {
        if (-not $Force) { Stop-Restore "$Target already has $tables tables; pass -Force to replace it" }
        Write-Log "dropping existing database $Target"
        Invoke-Sql "DROP DATABASE ``$Target``" | Out-Null
    }
    Invoke-Sql "CREATE DATABASE IF NOT EXISTS ``$Target`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci" | Out-Null
    Invoke-Sql "ALTER DATABASE ``$Target`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci" | Out-Null

    Write-Log "restoring $File into $Target"
    $watch = [Diagnostics.Stopwatch]::StartNew()
    # Feed the file on stdin, exactly as `mariadb db < file` would.
    $proc = Start-Process -FilePath $client -ArgumentList @("--defaults-extra-file=`"$cnf`"", $Target) `
        -RedirectStandardInput $sqlFile -RedirectStandardError (Join-Path $work 'err.txt') -NoNewWindow -Wait -PassThru
    if ($proc.ExitCode -ne 0) {
        Write-Log (Get-Content -Raw (Join-Path $work 'err.txt'))
        Stop-Restore "restore failed; $Target is incomplete"
    }
    Write-Log ("restored in {0:N0}s" -f $watch.Elapsed.TotalSeconds)

    function Get-RowCounts([string]$Db) {
        $union = Invoke-Sql ("SET SESSION group_concat_max_len = 1048576; " +
            "SELECT GROUP_CONCAT(CONCAT('SELECT ', QUOTE(table_name), ', COUNT(*) FROM ``', table_schema, '``.``', table_name, '``') " +
            "ORDER BY table_name SEPARATOR ' UNION ALL ') FROM information_schema.tables " +
            "WHERE table_schema = '$Db' AND table_type = 'BASE TABLE'")
        if (-not $union -or $union -eq 'NULL') { return @() }
        # Fed on stdin: the generated query can exceed the Windows command-line length limit.
        $rows = $union | & $client "--defaults-extra-file=$cnf" -N -B
        if ($LASTEXITCODE -ne 0) { Stop-Restore "row count query failed for $Db" }
        return @($rows | ForEach-Object {
                $parts = $_ -split "`t"
                [pscustomobject]@{ Table = $parts[0]; Rows = [long]$parts[1] }
            } | Sort-Object Table)
    }

    $restored = Get-RowCounts $Target
    $sum = ($restored | Measure-Object -Property Rows -Sum).Sum
    if ($null -eq $sum) { $sum = 0 }
    Write-Log "$($restored.Count) tables, $sum rows in $Target"

    if ($CompareWith) {
        $source = Get-RowCounts $CompareWith
        $diff = Compare-Object $source $restored -Property Table, Rows
        if ($diff) {
            Write-Log "row counts differ from $CompareWith (<= $CompareWith, => $Target):"
            $diff | Format-Table -AutoSize | Out-String | Write-Host
            exit 3
        }
        Write-Log "row counts match $CompareWith for every table"
    }
    else {
        $restored | Format-Table -AutoSize
    }
}
finally {
    Remove-Item -Recurse -Force -LiteralPath $work -ErrorAction SilentlyContinue
}
exit 0
