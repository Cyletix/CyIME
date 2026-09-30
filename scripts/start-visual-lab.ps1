$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try { & node tools/visual-lab/server.mjs } finally { Pop-Location }
