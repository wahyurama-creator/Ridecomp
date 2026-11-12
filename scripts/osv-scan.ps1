<#
.SYNOPSIS
  CVE scan of every locked dependency against the public OSV.dev database (SUP-06).

.DESCRIPTION
  Reads all Gradle lockfiles, sends only public library coordinates (group:name + version) to
  https://api.osv.dev and prints known vulnerabilities. Exit code 1 when a HIGH or CRITICAL
  vulnerability is found (blocker), 0 otherwise.
  Complements `./gradlew dependencyCheckAggregate`, which needs an NVD API key (decision D-16).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/osv-scan.ps1
#>
$ErrorActionPreference = 'Stop'

$OsvBatchUrl = 'https://api.osv.dev/v1/querybatch'
$OsvVulnUrl = 'https://api.osv.dev/v1/vulns/'
$Ecosystem = 'Maven'
$BatchSize = 500
$BlockingSeverities = @('HIGH', 'CRITICAL')
$RequestTimeoutSec = 60
# Configurations whose dependencies end up inside the APK.
$ShippedConfigPattern = '(^|,)(releaseRuntimeClasspath|runtimeClasspath)(,|$)'

$root = Split-Path -Parent $PSScriptRoot
$lockfiles = Get-ChildItem -Path $root -Recurse -Filter '*gradle.lockfile' -File |
    Where-Object { $_.FullName -notmatch '\\build\\' }

# Coordinate -> configurations it is resolved in (to tell shipped code from build tooling).
$configsByCoordinate = @{}
$lockfiles | ForEach-Object { Get-Content $_.FullName } |
    Where-Object { $_ -and -not $_.StartsWith('#') -and -not $_.StartsWith('empty=') } |
    ForEach-Object {
        $coordinate, $configs = $_ -split '=', 2
        $configsByCoordinate[$coordinate] = "$($configsByCoordinate[$coordinate]),$configs"
    }
$coordinates = @($configsByCoordinate.Keys | Sort-Object)

Write-Host "Scanning $($coordinates.Count) unique dependencies from $($lockfiles.Count) lockfiles..."

$findings = @()
for ($offset = 0; $offset -lt $coordinates.Count; $offset += $BatchSize) {
    $batch = $coordinates[$offset..([Math]::Min($offset + $BatchSize, $coordinates.Count) - 1)]
    $queries = $batch | ForEach-Object {
        $parts = $_ -split ':'
        @{ package = @{ ecosystem = $Ecosystem; name = "$($parts[0]):$($parts[1])" }; version = $parts[2] }
    }
    $body = @{ queries = @($queries) } | ConvertTo-Json -Depth 5 -Compress
    $response = Invoke-RestMethod -Method Post -Uri $OsvBatchUrl -Body $body -ContentType 'application/json' `
        -TimeoutSec $RequestTimeoutSec
    for ($i = 0; $i -lt $batch.Count; $i++) {
        foreach ($vuln in @($response.results[$i].vulns)) {
            if ($null -eq $vuln) { continue }
            $detail = Invoke-RestMethod -Uri ($OsvVulnUrl + $vuln.id) -TimeoutSec $RequestTimeoutSec
            $severity = $detail.database_specific.severity
            if (-not $severity) { $severity = 'UNKNOWN' }
            $findings += [pscustomobject]@{
                Dependency = $batch[$i]
                Scope = if ($configsByCoordinate[$batch[$i]] -match $ShippedConfigPattern) { 'SHIPPED' } else { 'TOOLING' }
                Id = $vuln.id
                Severity = $severity.ToUpperInvariant()
                Summary = $detail.summary
            }
        }
    }
}

if ($findings.Count -eq 0) {
    Write-Host 'OSV scan: no known vulnerabilities.'
    exit 0
}

$findings | Sort-Object Scope, Severity, Dependency | Format-Table -AutoSize -Wrap | Out-String -Width 200 | Write-Host
$highOrCritical = @($findings | Where-Object { $BlockingSeverities -contains $_.Severity })
$blocking = @($highOrCritical | Where-Object { $_.Scope -eq 'SHIPPED' })
if ($blocking.Count -gt 0) {
    Write-Host "OSV scan: $($blocking.Count) HIGH/CRITICAL finding(s) in shipped code - blocker (SUP-06)."
    exit 1
}
if ($highOrCritical.Count -gt 0) {
    Write-Host "OSV scan: $($highOrCritical.Count) HIGH/CRITICAL finding(s) in build tooling only (not in the APK) - update tools at the Phase Gate (SUP-10)."
}
Write-Host "OSV scan: $($findings.Count) finding(s) total, none HIGH/CRITICAL in shipped code - evaluate and record (SUP-06)."
exit 0
