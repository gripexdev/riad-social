$ErrorActionPreference = "Stop"

Push-Location (Join-Path $PSScriptRoot "..\\backend")
.\mvnw -q test
Pop-Location
