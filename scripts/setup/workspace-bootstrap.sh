#!/usr/bin/env bash
set -euo pipefail

WORKSPACE=""
BRANCH="develop"
PULL=false
DOCTOR_ONLY=false
USE_SSH=false
FORCE_INSTALL=true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --workspace)
      WORKSPACE="${2:-}"
      shift
      ;;
    --branch)
      BRANCH="${2:-}"
      shift
      ;;
    --pull)
      PULL=true
      ;;
    --doctor-only)
      DOCTOR_ONLY=true
      ;;
    --ssh)
      USE_SSH=true
      ;;
    --no-force)
      FORCE_INSTALL=false
      ;;
    --help|-h)
      cat <<'EOF'
Usage: workspace-bootstrap.sh --workspace <path> [options]

Options:
  --workspace <path>  Target workspace root
  --branch <name>     Branch to checkout (default: develop)
  --pull              Pull latest branch when repo is clean
  --doctor-only       Run doctor only (skip install --force)
  --ssh               Use SSH remotes instead of HTTPS
  --no-force          Do not pass --force to install.kts
  --help, -h          Show this help
EOF
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      exit 2
      ;;
  esac
  shift
done

if [[ -z "$WORKSPACE" ]]; then
  echo "Missing --workspace <path>"
  exit 2
fi

if ! command -v git >/dev/null 2>&1; then
  echo "[FAIL] git is required"
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "[FAIL] java is required"
  exit 1
fi

if ! command -v kotlinc >/dev/null 2>&1; then
  echo "[FAIL] kotlinc is required"
  exit 1
fi

abs_path() {
  local input="$1"
  if command -v realpath >/dev/null 2>&1; then
    realpath "$input"
  else
    python -c 'import os,sys; print(os.path.abspath(sys.argv[1]))' "$input"
  fi
}

workspace_parent="$(dirname "$WORKSPACE")"
mkdir -p "$workspace_parent"

if [[ ! -d "$WORKSPACE" ]]; then
  mkdir -p "$WORKSPACE"
fi

WORKSPACE="$(abs_path "$WORKSPACE")"

if [[ "$USE_SSH" == true ]]; then
  INFRA_URL="git@github.com:koupper-jvm/koupper-workspace.git"
  KOUPPER_URL="git@github.com:koupper-jvm/koupper.git"
  CLI_URL="git@github.com:koupper-jvm/koupper-cli.git"
  DOCS_URL="git@github.com:koupper-jvm/koupper-document.git"
else
  INFRA_URL="https://github.com/koupper-jvm/koupper-workspace.git"
  KOUPPER_URL="https://github.com/koupper-jvm/koupper.git"
  CLI_URL="https://github.com/koupper-jvm/koupper-cli.git"
  DOCS_URL="https://github.com/koupper-jvm/koupper-document.git"
fi

is_empty_dir() {
  local dir="$1"
  [[ -d "$dir" ]] && [[ -z "$(ls -A "$dir")" ]]
}

ensure_repo_root() {
  local repo_dir="$1"
  local remote_url="$2"
  if [[ -d "$repo_dir/.git" ]]; then
    return 0
  fi

  if is_empty_dir "$repo_dir"; then
    rmdir "$repo_dir"
    git clone "$remote_url" "$repo_dir"
    return 0
  fi

  if [[ -d "$repo_dir" ]]; then
    echo "[FAIL] $repo_dir exists and is not a git repository"
    exit 1
  fi

  git clone "$remote_url" "$repo_dir"
}

ensure_child_repo() {
  local parent="$1"
  local child_name="$2"
  local remote_url="$3"
  local child_path="$parent/$child_name"

  if [[ -d "$child_path/.git" ]]; then
    return 0
  fi

  if [[ -e "$child_path" ]] && [[ ! -d "$child_path/.git" ]]; then
    echo "[FAIL] $child_path exists but is not a git repository"
    exit 1
  fi

  git clone "$remote_url" "$child_path"
}

update_repo() {
  local repo_path="$1"
  local branch="$2"
  local name="$3"

  git -C "$repo_path" fetch origin --prune
  if git -C "$repo_path" show-ref --verify --quiet "refs/heads/$branch"; then
    git -C "$repo_path" checkout "$branch"
  else
    git -C "$repo_path" checkout -b "$branch" "origin/$branch"
  fi

  if [[ "$PULL" == true ]]; then
    if [[ -z "$(git -C "$repo_path" status --porcelain)" ]]; then
      git -C "$repo_path" pull --ff-only origin "$branch"
    else
      echo "[WARN] Skipping pull in $name (working tree is not clean)"
    fi
  fi
}

echo "[*] Preparing workspace at $WORKSPACE"
ensure_repo_root "$WORKSPACE" "$INFRA_URL"

ensure_child_repo "$WORKSPACE" "koupper" "$KOUPPER_URL"
ensure_child_repo "$WORKSPACE" "koupper-cli" "$CLI_URL"
ensure_child_repo "$WORKSPACE" "koupper-document" "$DOCS_URL"

INSTALL_DIR="$WORKSPACE"
if [[ ! -f "$INSTALL_DIR/install.kts" ]]; then
  INSTALL_DIR="$WORKSPACE/koupper"
fi

if [[ ! -f "$INSTALL_DIR/install.kts" ]]; then
  echo "[FAIL] install.kts not found in workspace root or ./koupper. Ensure this is koupper-workspace."
  exit 1
fi

echo "[*] Syncing repositories on branch $BRANCH"
update_repo "$WORKSPACE" "$BRANCH" "koupper-workspace"
update_repo "$WORKSPACE/koupper" "$BRANCH" "koupper"
update_repo "$WORKSPACE/koupper-cli" "$BRANCH" "koupper-cli"
update_repo "$WORKSPACE/koupper-document" "$BRANCH" "koupper-document"

echo "[*] Running installer"
if [[ "$DOCTOR_ONLY" == true ]]; then
  (cd "$INSTALL_DIR" && kotlinc -script install.kts -- --doctor)
else
  if [[ "$FORCE_INSTALL" == true ]]; then
    (cd "$INSTALL_DIR" && kotlinc -script install.kts -- --force)
  else
    (cd "$INSTALL_DIR" && kotlinc -script install.kts)
  fi
  (cd "$INSTALL_DIR" && kotlinc -script install.kts -- --doctor)
fi

echo "[OK] Maintainer workspace is ready"
echo "[PATH] Workspace: $WORKSPACE"
echo "[NEXT] cd \"$WORKSPACE\""
