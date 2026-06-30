#!/usr/bin/env bash
#
# build-ios.sh — Build Forge for iOS (iPad-first) via MobiVM (RoboVM fork).
#
# MobiVM's runtime is Java-8 level and cannot compile Java records, which Forge uses heavily.
# So this first compiles a small ASM "de-record" transformer (forge-gui-ios/tools/RecordDesugar)
# and runs it over the built module outputs (in the reactor, at prepare-package) BEFORE the
# MobiVM AOT compile. No Forge *source* is changed, so the fork stays upstream-mergeable.
#
# Usage:  ./scripts/build-ios.sh [sim|device]      (default: sim = iPad Simulator)
#
set -euo pipefail
TARGET="${1:-sim}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
IOS=forge-gui-ios
REV=2.0.14-SNAPSHOT

export JAVA_HOME="$(/usr/libexec/java_home -v 17 -a arm64 2>/dev/null)"
[ -n "$JAVA_HOME" ] || { echo "ERROR: arm64 JDK 17 not found (brew install openjdk@17)"; exit 1; }
export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
echo "==> JDK: $JAVA_HOME"

case "$TARGET" in
  sim)    GOALP=ios-sim ;;
  device) GOALP=ios-device ;;
  *) echo "usage: $0 [sim|device]"; exit 2 ;;
esac

# 1. Compile the de-record transformer.
ASM="$IOS/tools/lib/asm-9.4.jar:$IOS/tools/lib/asm-tree-9.4.jar:$IOS/tools/lib/asm-analysis-9.4.jar:$IOS/tools/lib/asm-commons-9.4.jar"
echo "==> Compiling RecordDesugar ..."
mkdir -p "$IOS/tools/out"
javac -cp "$ASM" -d "$IOS/tools/out" "$IOS/tools/RecordDesugar.java"

# 2. Reactor build (-am resolves ${revision}); the ios-derecord profile runs the transformer at
#    prepare-package; the ios-sim/ios-device profile runs the MobiVM goal at package.
echo "==> Reactor build + de-record + MobiVM ($GOALP) ..."
mvn -pl forge-gui-ios -am package -P ios-derecord,"$GOALP" \
    -Drevision="$REV" -DskipTests -Dcheckstyle.skip=true \
    -Drobovm.device.name="iPad Pro 11-inch (M5)"
