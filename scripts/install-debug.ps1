# Install the current Debug package without manually typing its version, ABI or suffix.
[CmdletBinding()]
param([string]$Serial = '')
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'install-release.ps1') -BuildType Debug -Serial $Serial
