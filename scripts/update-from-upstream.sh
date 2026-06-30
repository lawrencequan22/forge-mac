#!/usr/bin/env bash
#
# update-from-upstream.sh — Pull the latest Card-Forge/forge code and rebuild the Mac app.
#
# Usage:
#   ./scripts/update-from-upstream.sh                          # merge upstream/master, rebuild both
#   ./scripts/update-from-upstream.sh forge-2.0.15             # merge a specific upstream tag/branch
#   ./scripts/update-from-upstream.sh upstream/master desktop  # ...and only rebuild one edition
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Ensure the upstream remote exists (idempotent).
if ! git remote get-url upstream >/dev/null 2>&1; then
  git remote add upstream https://github.com/Card-Forge/forge.git
fi

TARGET="${1:-upstream/master}"
EDITION="${2:-both}"
BRANCH="$(git branch --show-current)"

echo "==> Fetching upstream (Card-Forge/forge) ..."
git fetch upstream --tags

echo "==> Merging '$TARGET' into '$BRANCH' ..."
git merge --no-edit "$TARGET"

echo "==> Rebuilding the macOS app(s): $EDITION ..."
exec "$ROOT/scripts/build-mac-app.sh" "$EDITION"
