$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

git config core.hooksPath .githooks | Out-Null

Write-Host "Git hooks path set to .githooks"
Write-Host "Tip: If you are on macOS/Linux, run: chmod +x .githooks/pre-push"
