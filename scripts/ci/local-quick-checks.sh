#!/usr/bin/env bash
set -euo pipefail

target="${1:-all}"

resolve_cli_dir() {
  if [ -d "koupper-cli" ] && [ -f "koupper-cli/gradlew" ]; then
    printf "koupper-cli"
    return
  fi

  if [ -d "../koupper-cli" ] && [ -f "../koupper-cli/gradlew" ]; then
    printf "../koupper-cli"
    return
  fi

  echo "[ci] ERROR: koupper-cli project not found (expected ./koupper-cli or ../koupper-cli)" >&2
  exit 1
}

resolve_docs_dir() {
  if [ -d "koupper-document" ]; then
    printf "koupper-document"
    return
  fi

  if [ -d "../koupper-document" ]; then
    printf "../koupper-document"
    return
  fi

  echo "[ci] ERROR: koupper-document project not found (expected ./koupper-document or ../koupper-document)" >&2
  exit 1
}

CLI_DIR="$(resolve_cli_dir)"
DOCS_DIR="$(resolve_docs_dir)"

run_core() {
  echo "[ci] Running core/providers targeted checks"
  ./gradlew :providers:test --tests "com.koupper.providers.ProviderCatalogConsistencyTest" --tests "com.koupper.providers.command.CommandRunnerServiceProviderTest"
}

run_cli() {
  echo "[ci] Running CLI targeted checks"
  (
    cd "$CLI_DIR"
    ./gradlew test --tests "com.koupper.cli.commands.ProviderCommandCatalogPathTest"
  )
}

run_docs() {
  echo "[ci] Running docs checks/build"
  (
    cd "$DOCS_DIR"
    npm run docs:check
    npm run docs:build
  )
}

case "$target" in
  core)
    run_core
    ;;
  cli)
    run_cli
    ;;
  docs)
    run_docs
    ;;
  all)
    run_core
    run_cli
    run_docs
    ;;
  *)
    echo "Usage: scripts/ci/local-quick-checks.sh [core|cli|docs|all]"
    exit 2
    ;;
esac

echo "[ci] Quick checks completed for target: $target"
