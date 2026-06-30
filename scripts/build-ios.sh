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

# 1a. Build the boot-classpath stubs (classes MobiVM's iOS runtime lacks) into one jar — reproducibly
#     from source. java.base: java.nio.file.* + java.lang.EnumConstantsRegistry ; java.management:
#     ManagementFactory/RuntimeMXBean. robovm.xml lists libs/ios-stubs.jar on <bootclasspath>.
echo "==> Building boot-classpath stubs (ios-stubs.jar) ..."
SOUT="$IOS/tools/stubout"; rm -rf "$SOUT"; mkdir -p "$SOUT"
javac --patch-module java.base="$IOS/stubs" -d "$SOUT" $(find "$IOS/stubs/java/nio" "$IOS/stubs/java/util" -name '*.java')
javac --patch-module java.management="$IOS/stubs" -d "$SOUT" "$IOS/stubs/java/lang/management/"*.java
javac -d "$SOUT" $(find "$IOS/stubs/forge" -name '*.java')   # forge.rt.EnumRegistry (boot-classpath, non-java pkg)
( cd "$SOUT" && jar cf "$ROOT/$IOS/libs/ios-stubs.jar" . )
echo "    -> $IOS/libs/ios-stubs.jar"

# 1b. Add java.io.File.toPath() to the RoboVM SDK runtime (idempotent). MobiVM's iOS runtime has no
#     toPath(); Forge calls file.toPath(), and RoboVM searches its runtime jar BEFORE our boot
#     classpath, so a bootclasspath override can't add it — we patch the runtime's File.class itself.
echo "==> Ensuring java.io.File.toPath() in the RoboVM SDK runtime ..."
javac -cp "$IOS/tools/lib/asm-9.4.jar" -d "$IOS/tools/out" "$IOS/tools/FilePatcher.java"
RTJAR="$(find "$HOME/.m2/repository/com/mobidevelop/robovm/robovm-dist" -path '*unpacked*' -name robovm-rt.jar 2>/dev/null | head -1)"
if [ -n "$RTJAR" ] && ! javap -p -cp "$RTJAR" java.io.File 2>/dev/null | grep -q toPath; then
  tmp="$(mktemp -d)"
  unzip -o -q "$RTJAR" 'java/io/File.class' -d "$tmp"
  java -cp "$IOS/tools/out:$IOS/tools/lib/asm-9.4.jar" FilePatcher "$tmp/java/io/File.class" "$tmp/java/io/File.class"
  jar uf "$RTJAR" -C "$tmp" java/io/File.class
  find "$HOME/.robovm/cache" -name robovm-rt.jar -exec cp "$RTJAR" {} \; 2>/dev/null || true
  rm -rf "$tmp"
  echo "    patched $RTJAR"
else
  echo "    already present"
fi

# 1c. rt-patch java.lang.Enum.getSharedConstants to consult forge.ios.EnumRegistry first (large-enum
#     reflection fix). Rebuilt from the PRISTINE compile-time rt artifact each run, so it's idempotent
#     and self-healing (never double-injects, even if a prior build left a stale patch in the SDK rt).
echo "==> Patching Enum.getSharedConstants (registry hook) in the RoboVM SDK runtime ..."
javac -cp "$ASM" -d "$IOS/tools/out" "$IOS/tools/EnumPatcher.java"
PRISTINE_RT="$(find "$HOME/.m2/repository/com/mobidevelop/robovm/robovm-rt" -name 'robovm-rt-*.jar' 2>/dev/null | grep -v sources | head -1)"
if [ -n "$RTJAR" ] && [ -n "$PRISTINE_RT" ]; then
  tmp="$(mktemp -d)"
  unzip -o -q "$PRISTINE_RT" 'java/lang/Enum.class' -d "$tmp"      # pristine, unpatched Enum
  java -cp "$IOS/tools/out:$ASM" EnumPatcher "$tmp/java/lang/Enum.class" "$tmp/java/lang/Enum.class"
  jar uf "$RTJAR" -C "$tmp" java/lang/Enum.class                   # overwrite SDK rt's Enum with the patched one
  find "$HOME/.robovm/cache" -name robovm-rt.jar -exec cp "$RTJAR" {} \; 2>/dev/null || true
  rm -rf "$tmp"
  echo "    patched Enum -> $RTJAR (from pristine $PRISTINE_RT)"
else
  echo "    SKIPPED (rt jar not found)"
fi

# 2. Reactor build (-am resolves ${revision}); the ios-derecord profile runs the transformer at
#    prepare-package; the ios-sim/ios-device profile runs the MobiVM goal at package.
# Device builds need a code-signing identity + provisioning profile. Pass them via env:
#   FORGE_IOS_SIGN_IDENTITY="Apple Development: YOUR NAME (TEAMID)"
#   FORGE_IOS_PROFILE="<provisioning profile name or UUID>"   (must be in ~/Library/MobileDevice/Provisioning Profiles)
EXTRA=()
if [ "$TARGET" = device ]; then
  [ -n "${FORGE_IOS_SIGN_IDENTITY:-}" ] && EXTRA+=("-Drobovm.iosSignIdentity=${FORGE_IOS_SIGN_IDENTITY}")
  [ -n "${FORGE_IOS_PROFILE:-}" ]        && EXTRA+=("-Drobovm.iosProvisioningProfile=${FORGE_IOS_PROFILE}")
else
  EXTRA+=("-Drobovm.device.name=${FORGE_IOS_SIM_DEVICE:-iPad Pro 11-inch (M5)}")
fi

echo "==> Reactor build + de-record + MobiVM ($GOALP) ..."
mvn -pl forge-gui-ios -am package -P ios-derecord,"$GOALP" \
    -Drevision="$REV" -DskipTests -Dcheckstyle.skip=true \
    "${EXTRA[@]}"
