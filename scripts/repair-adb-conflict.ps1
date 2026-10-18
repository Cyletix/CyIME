# Unify SuperDisplay's private ADB with the Android SDK, preserving its service/settings.
# A version command is local: it does not contact or restart the ADB daemon.
[CmdletBinding()]
param(
    [string]$SdkDirectory = "$env:LOCALAPPDATA/Android/Sdk/platform-tools",
    [string]$SuperDisplayDirectory = "$env:ProgramFiles/SuperDisplay",
    [string]$BackupRoot = 'D:/CyIME-Data/adb-compat',
    [string]$Receipt = '',
    [switch]$RepairDynamicPorts
)
$ErrorActionPreference = 'Stop'
if (-not $Receipt) { $Receipt = Join-Path $PSScriptRoot '../.codex-artifacts/adb-compat-repair.json' }
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)


$sdk = (Resolve-Path -LiteralPath $SdkDirectory).Path
$product = (Resolve-Path -LiteralPath $SuperDisplayDirectory).Path
$target = (Resolve-Path -LiteralPath (Join-Path $product 'adb')).Path
$sdkAdb = Join-Path $sdk 'adb.exe'
$privateAdb = Join-Path $target 'adb.exe'
$names = @('adb.exe', 'AdbWinApi.dll', 'AdbWinUsbApi.dll')
foreach ($name in $names) {
    if (-not (Test-Path -LiteralPath (Join-Path $sdk $name) -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $target $name) -PathType Leaf)) {
        throw "Missing ADB runtime file: $name"
    }
}
$serviceInfo = Get-CimInstance Win32_Service -Filter "Name='SuperDisplay'"
if (-not $serviceInfo -or $serviceInfo.PathName.Trim('"') -ne (Join-Path $product 'MirrorService.exe')) {
    throw 'SuperDisplay service does not match the requested installation; nothing changed.'
}
function Get-AdbVersion([string]$Executable) {
    $result = (& $Executable version 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $result -notmatch 'Android Debug Bridge version 1\.0\.\d+') {
        throw "Cannot identify ADB executable: $Executable"
    }
    return $result
}
$oldVersion = Get-AdbVersion $privateAdb
$newVersion = Get-AdbVersion $sdkAdb
if ($newVersion -notmatch 'Android Debug Bridge version 1\.0\.41') {
    throw 'This repair was validated with SDK ADB protocol 41; review a different version before replacing files.'
}
$sameFiles = $true
foreach ($name in $names) {
    if ((Get-FileHash -LiteralPath (Join-Path $sdk $name) -Algorithm SHA256).Hash -ne
        (Get-FileHash -LiteralPath (Join-Path $target $name) -Algorithm SHA256).Hash) { $sameFiles = $false }
}
if ($sameFiles -and -not $RepairDynamicPorts) {
    $receiptPath = [IO.Path]::GetFullPath($Receipt)
    New-Item -ItemType Directory -Path (Split-Path -Parent $receiptPath) -Force | Out-Null
    [ordered]@{status='already-compatible'; timestamp=(Get-Date).ToString('o'); source=$sdk;
        destination=$target; version=$newVersion; serviceState=$serviceInfo.State} |
        ConvertTo-Json | Set-Content -LiteralPath $receiptPath -Encoding UTF8
    Write-Output 'ADB files already match; no elevation or service restart needed.'
    exit 0
}
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    # Standard UAC consent, not a privilege bypass. Do the concrete repair only after consent.
    $arguments = @('-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File',
        ('"{0}"' -f $PSCommandPath), '-SdkDirectory', ('"{0}"' -f $SdkDirectory),
        '-SuperDisplayDirectory', ('"{0}"' -f $SuperDisplayDirectory),
        '-BackupRoot', ('"{0}"' -f $BackupRoot), '-Receipt', ('"{0}"' -f $Receipt))
    if ($RepairDynamicPorts) { $arguments += '-RepairDynamicPorts' }
    $process = Start-Process powershell.exe -Verb RunAs -WindowStyle Hidden -ArgumentList $arguments -PassThru
    $process.WaitForExit()
    exit $process.ExitCode
}
$backup = Join-Path ([IO.Path]::GetFullPath($BackupRoot)) (Get-Date -Format 'yyyyMMdd-HHmmss-fff')
New-Item -ItemType Directory -Path $backup | Out-Null
$manifest = @()
foreach ($name in $names) {
    $from = Join-Path $target $name
    Copy-Item -LiteralPath $from -Destination (Join-Path $backup $name)
    $manifest += [ordered]@{name=$name; before=(Get-FileHash -LiteralPath $from -Algorithm SHA256).Hash;
        after=(Get-FileHash -LiteralPath (Join-Path $sdk $name) -Algorithm SHA256).Hash}
}
$wasRunning = $serviceInfo.State -eq 'Running'
$record = [ordered]@{status='started'; timestamp=(Get-Date).ToString('o'); service='SuperDisplay';
    serviceStartMode=$serviceInfo.StartMode; wasRunning=$wasRunning; backup=$backup;
    source=$sdk; destination=$target; receipt=[IO.Path]::GetFullPath($Receipt);
    oldVersion=$oldVersion; newVersion=$newVersion; files=$manifest}
$record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $backup 'manifest.json') -Encoding UTF8
function Invoke-Netsh([string[]]$Arguments) {
    $output = (& netsh.exe @Arguments 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw "netsh failed: $($Arguments -join ' '): $output" }
    return $output
}
function Read-TcpRange([string]$Family) {
    $output = Invoke-Netsh @('interface', $Family, 'show', 'dynamicport', 'tcp')
    $numbers = @([regex]::Matches($output, ':\s*(\d+)') | ForEach-Object { [int]$_.Groups[1].Value })
    if ($numbers.Count -ne 2) { throw "Cannot read $Family dynamic TCP range: $output" }
    return [ordered]@{family=$Family; start=$numbers[0]; count=$numbers[1]; text=$output}
}
$oldTcp = @()
$tcpChanged = $false
if ($RepairDynamicPorts) {
    # Do not interrupt active virtual-machine/container sessions to fix a host port.
    $runningWsl = (& wsl.exe --list --running --quiet 2>$null | Out-String) -replace "`0", ''
    if ($runningWsl.Trim() -or (Get-Process vmmem,vmmemWSL,'Docker Desktop',com.docker.backend -ErrorAction SilentlyContinue)) {
        throw 'Active WSL/Docker detected; stop them before refreshing WinNAT.'
    }
    $oldTcp = @(Read-TcpRange 'ipv4'; Read-TcpRange 'ipv6')
    $record.tcpBefore = $oldTcp
    $record.exclusionsBefore = Invoke-Netsh @('interface','ipv4','show','excludedportrange','protocol=tcp')
    $record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $backup 'manifest.json') -Encoding UTF8
}
function Stop-KnownAdb {
    $processes = @(Get-CimInstance Win32_Process -Filter "Name='adb.exe'" | Where-Object {
        $_.ExecutablePath -eq $privateAdb -or $_.ExecutablePath -eq $sdkAdb
    })
    $processes | Select-Object ProcessId,ParentProcessId,ExecutablePath |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backup 'stopped-processes.json') -Encoding UTF8
    foreach ($item in $processes) {
        $process = Get-Process -Id $item.ProcessId -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $item.ProcessId -Force
            if (-not $process.WaitForExit(10000)) { throw "ADB process did not exit: $($item.ProcessId)" }
        }
    }
}
try {
    if ($wasRunning) {
        $serviceProcess = Get-Process -Id $serviceInfo.ProcessId -ErrorAction SilentlyContinue
        Stop-Service -Name SuperDisplay
        (Get-Service SuperDisplay).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(15))
        # SERVICE_STOPPED can be signalled before MirrorService's ADB children exit.
        if ($serviceProcess -and -not $serviceProcess.WaitForExit(15000)) { throw 'SuperDisplay process did not finish stopping.' }
    }
    # Only the two identified ADB installations, never every adb/Java/vendor process.
    Stop-KnownAdb
    foreach ($name in $names) {
        Copy-Item -LiteralPath (Join-Path $sdk $name) -Destination (Join-Path $target $name) -Force
        $actual = (Get-FileHash -LiteralPath (Join-Path $target $name) -Algorithm SHA256).Hash
        if ($actual -ne ($manifest | Where-Object { $_.name -eq $name }).after) { throw "Copy verification failed: $name" }
    }
    if ((Get-AdbVersion $privateAdb) -notmatch 'Android Debug Bridge version 1\.0\.41') { throw 'Replacement version mismatch' }
    if ($RepairDynamicPorts) {
        $tcpChanged = $true
        foreach ($family in @('ipv4','ipv6')) {
            $null = Invoke-Netsh @('interface',$family,'set','dynamicport','tcp','start=49152','num=16384')
        }
        $natWasRunning = (Get-Service winnat).Status -eq 'Running'
        try {
            if ($natWasRunning) {
                # HNS may immediately demand-start WinNAT again. Stop-Service waits for
                # a Stopped state it can miss and can hang indefinitely in that race.
                $null = & sc.exe stop winnat
                if ($LASTEXITCODE -ne 0) { throw 'WinNAT stop request failed.' }
                $deadline = (Get-Date).AddSeconds(15)
                while ((Get-Service winnat).Status -eq 'StopPending' -and (Get-Date) -lt $deadline) {
                    Start-Sleep -Milliseconds 200
                }
            }
        } finally {
            if ($natWasRunning) {
                if ((Get-Service winnat).Status -eq 'Stopped') {
                    $null = & sc.exe start winnat
                    if ($LASTEXITCODE -ne 0) { throw 'WinNAT restart request failed.' }
                }
                (Get-Service winnat).WaitForStatus('Running', [TimeSpan]::FromSeconds(15))
            }
        }
        $record.tcpAfter = @(Read-TcpRange 'ipv4'; Read-TcpRange 'ipv6')
        $record.exclusionsAfter = Invoke-Netsh @('interface','ipv4','show','excludedportrange','protocol=tcp')
        # Before resuming SuperDisplay, prove its exact fixed local port can bind.
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 1445)
        try { $listener.Start(); $record.port1445Bindable = $true } finally { $listener.Stop() }
    }
    # Service keeps its original automatic-start configuration and connection preferences.
    if ($wasRunning) { Start-Service -Name SuperDisplay; (Get-Service SuperDisplay).WaitForStatus('Running', [TimeSpan]::FromSeconds(15)) }
    $record.status = 'repaired'
} catch {
    $record.status = 'failed'
    $record.error = $_.Exception.Message
    if ($tcpChanged) {
        foreach ($range in $oldTcp) {
            $null = Invoke-Netsh @('interface',$range.family,'set','dynamicport','tcp',"start=$($range.start)","num=$($range.count)")
        }
    }
    # Restore the verified original runtime if the replacement failed.
    Stop-Service -Name SuperDisplay -ErrorAction SilentlyContinue
    Stop-KnownAdb
    foreach ($file in $manifest) {
        $saved = Join-Path $backup $file.name
        if ((Get-FileHash -LiteralPath $saved -Algorithm SHA256).Hash -ne $file.before) { throw 'Backup checksum changed; refusing rollback.' }
        Copy-Item -LiteralPath $saved -Destination (Join-Path $target $file.name) -Force
    }
    $record.status = 'rolled-back'
    if ($wasRunning) { Start-Service -Name SuperDisplay }
} finally {
    $receiptPath = [IO.Path]::GetFullPath($Receipt)
    New-Item -ItemType Directory -Path (Split-Path -Parent $receiptPath) -Force | Out-Null
    $record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $receiptPath -Encoding UTF8
    $record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $backup 'result.json') -Encoding UTF8
}
if ($record.status -ne 'repaired') { throw $record.error }
