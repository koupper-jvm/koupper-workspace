param(
    [ValidateSet("core", "cli", "docs", "all")]
    [string]$Target = "all"
)

$ErrorActionPreference = "Stop"

function Resolve-CliPath {
    if ((Test-Path "koupper-cli") -and (Test-Path "koupper-cli\gradlew.bat")) { return "koupper-cli" }
    if ((Test-Path "..\koupper-cli") -and (Test-Path "..\koupper-cli\gradlew.bat")) { return "..\koupper-cli" }
    throw "[ci] ERROR: koupper-cli project not found (expected .\koupper-cli or ..\koupper-cli)"
}

function Resolve-DocsPath {
    if (Test-Path "koupper-document") { return "koupper-document" }
    if (Test-Path "..\koupper-document") { return "..\koupper-document" }
    throw "[ci] ERROR: koupper-document project not found (expected .\koupper-document or ..\koupper-document)"
}

$CliPath = Resolve-CliPath
$DocsPath = Resolve-DocsPath

function Run-Core {
    Write-Host "[ci] Running core/providers targeted checks"
    .\gradlew.bat :providers:test --tests "com.koupper.providers.ProviderCatalogConsistencyTest" --tests "com.koupper.providers.command.CommandRunnerServiceProviderTest"
}

function Run-Cli {
    Write-Host "[ci] Running CLI targeted checks"
    Push-Location $CliPath
    try {
        .\gradlew.bat test --tests "com.koupper.cli.commands.ProviderCommandCatalogPathTest"
    }
    finally {
        Pop-Location
    }
}

function Run-Docs {
    Write-Host "[ci] Running docs checks/build"
    Push-Location $DocsPath
    try {
        npm run docs:check
        npm run docs:build
    }
    finally {
        Pop-Location
    }
}

switch ($Target) {
    "core" { Run-Core }
    "cli" { Run-Cli }
    "docs" { Run-Docs }
    "all" {
        Run-Core
        Run-Cli
        Run-Docs
    }
}

Write-Host "[ci] Quick checks completed for target: $Target"
