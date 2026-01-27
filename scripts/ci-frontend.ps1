$ErrorActionPreference = "Stop"

Push-Location (Join-Path $PSScriptRoot "..\\frontend")
npm test -- --watch=false --code-coverage
Pop-Location
