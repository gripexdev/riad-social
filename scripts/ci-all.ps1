$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "ci-backend.ps1")
& (Join-Path $PSScriptRoot "ci-frontend.ps1")
