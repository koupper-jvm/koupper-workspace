param(
    [Parameter(Mandatory = $true)]
    [string]$Workspace,
    [string]$Branch = "develop",
    [switch]$Pull,
    [switch]$DoctorOnly,
    [switch]$Ssh,
    [switch]$NoForce
)

$ErrorActionPreference = "Stop"

function Write-Ok([string]$Message) { Write-Host "[OK] $Message" -ForegroundColor Green }
function Write-Warn([string]$Message) { Write-Host "[WARN] $Message" -ForegroundColor Yellow }
function Write-Fail([string]$Message) { Write-Host "[FAIL] $Message" -ForegroundColor Red }
function Write-Info([string]$Message) { Write-Host "[*] $Message" -ForegroundColor Cyan }

function Test-Command([string]$Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Ensure-Command([string]$Name) {
    if (-not (Test-Command $Name)) {
        throw "$Name is required in PATH"
    }
}

function Ensure-RepoRoot {
    param(
        [string]$RepoPath,
        [string]$RemoteUrl
    )

    if (Test-Path (Join-Path $RepoPath ".git")) {
        return
    }

    if (-not (Test-Path $RepoPath)) {
        git clone $RemoteUrl $RepoPath | Out-Host
        return
    }

    $items = Get-ChildItem -Force -Path $RepoPath
    if ($items.Count -eq 0) {
        Remove-Item -LiteralPath $RepoPath -Force
        git clone $RemoteUrl $RepoPath | Out-Host
        return
    }

    throw "$RepoPath exists and is not a git repository"
}

function Ensure-ChildRepo {
    param(
        [string]$WorkspacePath,
        [string]$FolderName,
        [string]$RemoteUrl
    )

    $target = Join-Path $WorkspacePath $FolderName
    if (Test-Path (Join-Path $target ".git")) {
        return
    }

    if (Test-Path $target) {
        throw "$target exists and is not a git repository"
    }

    git clone $RemoteUrl $target | Out-Host
}

function Update-Repo {
    param(
        [string]$RepoPath,
        [string]$BranchName,
        [string]$Label
    )

    git -C $RepoPath fetch origin --prune | Out-Host

    $hasLocalBranch = $false
    git -C $RepoPath show-ref --verify --quiet "refs/heads/$BranchName"
    if ($LASTEXITCODE -eq 0) {
        $hasLocalBranch = $true
    }

    if ($hasLocalBranch) {
        git -C $RepoPath checkout $BranchName | Out-Host
    } else {
        git -C $RepoPath checkout -b $BranchName "origin/$BranchName" | Out-Host
    }

    if ($Pull) {
        $status = git -C $RepoPath status --porcelain
        if ([string]::IsNullOrWhiteSpace($status)) {
            git -C $RepoPath pull --ff-only origin $BranchName | Out-Host
        }
        else {
            Write-Warn "Skipping pull in $Label (working tree is not clean)"
        }
    }
}

Ensure-Command "git"
Ensure-Command "java"
Ensure-Command "kotlinc"

$workspacePath = [System.IO.Path]::GetFullPath($Workspace)
$workspaceParent = Split-Path -Parent $workspacePath
if (-not (Test-Path $workspaceParent)) {
    New-Item -ItemType Directory -Path $workspaceParent -Force | Out-Null
}
if (-not (Test-Path $workspacePath)) {
    New-Item -ItemType Directory -Path $workspacePath -Force | Out-Null
}

if ($Ssh) {
    $infraUrl = "git@github.com:koupper-jvm/koupper-infrastructure.git"
    $koupperUrl = "git@github.com:koupper-jvm/koupper.git"
    $cliUrl = "git@github.com:koupper-jvm/koupper-cli.git"
    $docsUrl = "git@github.com:koupper-jvm/koupper-document.git"
} else {
    $infraUrl = "https://github.com/koupper-jvm/koupper-infrastructure.git"
    $koupperUrl = "https://github.com/koupper-jvm/koupper.git"
    $cliUrl = "https://github.com/koupper-jvm/koupper-cli.git"
    $docsUrl = "https://github.com/koupper-jvm/koupper-document.git"
}

Write-Info "Preparing workspace at $workspacePath"
Ensure-RepoRoot -RepoPath $workspacePath -RemoteUrl $infraUrl

$installScript = Join-Path $workspacePath "install.kts"
if (-not (Test-Path $installScript)) {
    throw "install.kts not found in workspace root. Ensure this is koupper-infrastructure."
}

Ensure-ChildRepo -WorkspacePath $workspacePath -FolderName "koupper" -RemoteUrl $koupperUrl
Ensure-ChildRepo -WorkspacePath $workspacePath -FolderName "koupper-cli" -RemoteUrl $cliUrl
Ensure-ChildRepo -WorkspacePath $workspacePath -FolderName "koupper-document" -RemoteUrl $docsUrl

Write-Info "Syncing repositories on branch $Branch"
Update-Repo -RepoPath $workspacePath -BranchName $Branch -Label "koupper-infrastructure"
Update-Repo -RepoPath (Join-Path $workspacePath "koupper") -BranchName $Branch -Label "koupper"
Update-Repo -RepoPath (Join-Path $workspacePath "koupper-cli") -BranchName $Branch -Label "koupper-cli"
Update-Repo -RepoPath (Join-Path $workspacePath "koupper-document") -BranchName $Branch -Label "koupper-document"

Write-Info "Running installer"
Push-Location $workspacePath
try {
    if ($DoctorOnly) {
        & kotlinc -script install.kts -- --doctor
        if ($LASTEXITCODE -ne 0) { throw "install doctor failed" }
    }
    else {
        if ($NoForce) {
            & kotlinc -script install.kts
        }
        else {
            & kotlinc -script install.kts -- --force
        }
        if ($LASTEXITCODE -ne 0) { throw "install failed" }

        & kotlinc -script install.kts -- --doctor
        if ($LASTEXITCODE -ne 0) { throw "install doctor failed" }
    }
}
finally {
    Pop-Location
}

Write-Ok "Maintainer workspace is ready"
Write-Host "[PATH] Workspace: $workspacePath"
Write-Host "[NEXT] cd \"$workspacePath\""
