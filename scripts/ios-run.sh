#!/usr/bin/env bash
#
# ios-run.sh — launch Forge on the connected iOS device, capture EVERY diagnostic, and append a
# structured, timestamped entry to forge-gui-ios/PORT_LOG.md. Raw artifacts go to build/ios-logs/<stamp>/.
#
# This turns the ad-hoc "launch + pull forge.log + pull crash report + eyeball it" loop into one
# reproducible, self-documenting command — so progress is never lost between build cycles.
#
# Usage:  ./scripts/ios-run.sh ["short note about what this run is testing"]
# Env:    FORGE_IOS_DEVICE=<udid>   pin a device (else auto-detects the first connected one)
#         FORGE_IOS_WAIT=<seconds>  how long to let the app run before pulling logs (default 14)
#
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
BUNDLE="ca.lawrencequan.forge"
NOTE="${1:-}"
WAIT="${FORGE_IOS_WAIT:-14}"
LOG="forge-gui-ios/PORT_LOG.md"

# --- pick the connected device. `devicectl list` prints the coredevice UUID (8-4-4-4-12), which
#     `--device` accepts. Prefer a connected iPad, else any connected device. -----------------------
UUID_RE='[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}'
if [ -n "${FORGE_IOS_DEVICE:-}" ]; then
  UDID="$FORGE_IOS_DEVICE"
else
  DEVLIST="$(xcrun devicectl list devices 2>/dev/null | grep -i 'connected')"
  LINE="$(echo "$DEVLIST" | grep -i 'ipad' | head -1)"
  [ -z "$LINE" ] && LINE="$(echo "$DEVLIST" | head -1)"
  UDID="$(echo "$LINE" | grep -oE "$UUID_RE" | head -1)"
fi
if [ -z "$UDID" ]; then
  echo "ERROR: no connected iOS device found. Plug in + unlock the device, or set FORGE_IOS_DEVICE." >&2
  echo "       devicectl sees:" >&2
  xcrun devicectl list devices 2>/dev/null | grep -iE 'ipad|iphone' >&2
  exit 1
fi
echo "==> device: $UDID"

STAMP="$(date +%Y%m%d-%H%M%S)"
LOGDIR="build/ios-logs/$STAMP"; mkdir -p "$LOGDIR/crashes"
COMMIT="$(git rev-parse --short HEAD 2>/dev/null || echo '?') $(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?')"
SUBJECT="$(git log -1 --pretty=%s 2>/dev/null || echo '?')"

echo "==> launching $BUNDLE on $UDID (capturing for ${WAIT}s) ..."
xcrun devicectl device process launch --device "$UDID" "$BUNDLE" >"$LOGDIR/launch.txt" 2>&1 || true
sleep "$WAIT"

# --- pull every diagnostic the app may have written -----------------------------------------------
for f in Library/local/data/forge.log Library/local/enumfix.log Library/local/enumtest.log; do
  xcrun devicectl device copy from --device "$UDID" --domain-type appDataContainer \
        --domain-identifier "$BUNDLE" --source "$f" --destination "$LOGDIR/$(basename "$f")" >/dev/null 2>&1 || true
done
idevicecrashreport -u "$UDID" -k "$LOGDIR/crashes" >/dev/null 2>&1 || true

# --- analyze forge.log: crashed (and where) or ran clean? -----------------------------------------
# NB: "Error handling registered!" is a normal startup line, so match real exceptions only.
EXC_RE='Exception|AssertionError|NoClassDefFoundError|UnsatisfiedLinkError|NoSuchMethodError|Caused by:'
FL="$LOGDIR/forge.log"
STATUS="unknown (no forge.log pulled — device locked? crashed pre-logging?)"
BLOCKER=""; LASTAPP=""
if [ -f "$FL" ]; then
  if grep -qE "$EXC_RE" "$FL"; then
    EXC="$(grep -m1 -E "$EXC_RE" "$FL" | sed 's/^[[:space:]]*//')"
    APPFRAME="$(grep -m1 -E '^[[:space:]]*at forge\.' "$FL" | sed 's/^[[:space:]]*//')"
    STATUS="CRASH"
    BLOCKER="$EXC  ||  $APPFRAME"
  else
    STATUS="RAN — no exception in forge.log"
  fi
  LASTAPP="$(grep -E 'GuiBase:|Read cards|Splash|loaded|at forge\.' "$FL" | tail -1 | sed 's/^[[:space:]]*//')"
fi
NCRASH="$(ls "$LOGDIR/crashes"/*orge* 2>/dev/null | wc -l | tr -d ' ')"

# --- append a structured entry to PORT_LOG.md -----------------------------------------------------
{
  echo ""
  echo "### $STAMP"
  echo "- commit: \`$COMMIT\` — $SUBJECT"
  [ -n "$NOTE" ] && echo "- testing: $NOTE"
  echo "- status: **$STATUS**"
  [ -n "$BLOCKER" ] && echo "- blocker: \`$BLOCKER\`"
  [ -n "$LASTAPP" ] && echo "- furthest log line: \`$LASTAPP\`"
  echo "- artifacts: \`$LOGDIR/\` (forge.log, enum*.log, crashes/ [$NCRASH], launch.txt)"
} >> "$LOG"

echo "==> appended entry $STAMP to $LOG"
echo "    status : $STATUS"
[ -n "$BLOCKER" ] && echo "    blocker: $BLOCKER"
echo "    raw    : $LOGDIR/"
