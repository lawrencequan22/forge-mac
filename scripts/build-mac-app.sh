#!/usr/bin/env bash
#
# build-mac-app.sh — Build self-contained, native Apple Silicon Forge app(s) + DMG(s).
#
# Wraps the standard Forge "jar-with-dependencies" in a macOS .app bundle with an embedded
# arm64 JRE (via jlink + jpackage). No system Java is required to run the result.
# Source changes are limited to three small, opt-in (-Dforge.assetsDir) methods, so
# `git merge upstream/master` stays essentially conflict-free.
#
# Usage:
#   ./scripts/build-mac-app.sh [desktop|adventure|both]   (default: desktop)
#
# Editions:
#   desktop   -> forge-gui-desktop   (classic Swing UI)        -> "Forge.app"
#   adventure -> forge-gui-mobile-dev (libGDX/LWJGL3 RPG)      -> "Forge Adventure.app"
#
# Output: build/mac/out/<Name>.app  and  build/mac/out/<Name>-<version>.dmg
#
set -euo pipefail

TARGET="${1:-desktop}"
case "$TARGET" in
  desktop|adventure|both) ;;
  *) echo "Usage: $0 [desktop|adventure|both]" >&2; exit 2 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# --- Pin the native arm64 JDK 17 (Maven, jlink, jpackage all use it) -------------
JAVA_HOME="$(/usr/libexec/java_home -v 17 -a arm64 2>/dev/null || true)"
if [ -z "$JAVA_HOME" ]; then
  echo "ERROR: No arm64 JDK 17 found. Install one, e.g.:  brew install openjdk@17" >&2
  exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "==> Using JDK: $JAVA_HOME"

command -v jlink    >/dev/null 2>&1 || { echo "ERROR: 'jlink' not found (JDK 17 issue)." >&2; exit 1; }
command -v jpackage >/dev/null 2>&1 || { echo "ERROR: 'jpackage' not found (JDK 17 issue)." >&2; exit 1; }
command -v mvn      >/dev/null 2>&1 || { echo "ERROR: 'mvn' not found on PATH. Install:  brew install maven" >&2; exit 1; }

# --- Which editions / modules ----------------------------------------------------
declare -a EDITIONS
case "$TARGET" in
  desktop)   EDITIONS=(desktop) ;;
  adventure) EDITIONS=(adventure) ;;
  both)      EDITIONS=(desktop adventure) ;;
esac

edition_module()    { [ "$1" = desktop ] && echo "forge-gui-desktop"  || echo "forge-gui-mobile-dev"; }
edition_mainclass() { [ "$1" = desktop ] && echo "forge.view.Main"    || echo "forge.app.Main"; }
edition_appname()   { [ "$1" = desktop ] && echo "Forge"              || echo "Forge Adventure"; }

# --- Build all needed modules in one Maven pass ----------------------------------
MODLIST=""
for e in "${EDITIONS[@]}"; do MODLIST="${MODLIST:+$MODLIST,}$(edition_module "$e")"; done
echo "==> Maven build: $MODLIST (jar-with-dependencies) ..."
mvn -U -B -pl "$MODLIST" -am clean package -DskipTests

# jpackage --app-version is strict (digits + dots). Use the pom versionCode.
APP_VERSION="$(grep -m1 '<versionCode>' pom.xml | sed -E 's/.*<versionCode>([0-9.]+).*/\1/')"
[ -n "$APP_VERSION" ] || APP_VERSION="1.0.0"
echo "==> app-version: $APP_VERSION"

# --- Build a full-module arm64 runtime once (shared by all editions) -------------
RUNTIME="build/mac/runtime"
OUT="build/mac/out"
rm -rf build/mac
mkdir -p "$OUT"
echo "==> jlink: building embedded arm64 runtime ..."
jlink --add-modules ALL-MODULE-PATH \
      --strip-debug --no-man-pages --no-header-files --compress=2 \
      --output "$RUNTIME"

# --- Shared JVM options ----------------------------------------------------------
# $APPDIR is substituted by the jpackage launcher at runtime to the ABSOLUTE path
# <App>.app/Contents/app, where res/ is staged. Our opt-in -Dforge.assetsDir patches
# turn that into an absolute assets root, so asset paths are cwd-independent (a native
# .app launches with cwd=/, unlike forge.sh which cd's into the install dir).
JOPTS=(
  --java-options '-Dforge.assetsDir=$APPDIR'
  --java-options -Xmx4096m
  --java-options -Dio.netty.tryReflectionSetAccessible=true
  --java-options -Dfile.encoding=UTF-8
  # NOTE: we intentionally do NOT pass -Dapple.laf.useScreenMenuBar=true. On macOS 26 it
  # triggers a NullPointerException in Apple's Aqua menu-bar painter (RecyclableBorder null);
  # Forge then renders its menu in-window, which is fine for its custom-skinned UI.
  --java-options -Dcom.apple.macos.use-file-dialog-packages=true
  --java-options -Dcom.apple.smallTabs=true
)
# Pull the exact --add-opens list from the desktop pom (identical set used by both editions'
# launch scripts) so it tracks upstream automatically. The block is a single line.
ADDOPENS_RAW="$(sed -n 's/.*<addopen.java.args>\(.*\)<\/addopen.java.args>.*/\1/p' forge-gui-desktop/pom.xml)"
read -r -a TOKENS <<< "$ADDOPENS_RAW"
i=0
while [ "$i" -lt "${#TOKENS[@]}" ]; do
  if [ "${TOKENS[$i]}" = "--add-opens" ]; then
    JOPTS+=(--java-options "--add-opens=${TOKENS[$((i + 1))]}")
    i=$((i + 2))
  else
    i=$((i + 1))
  fi
done

# --- Package one edition ---------------------------------------------------------
declare -a RESULTS
build_edition() {
  local edition="$1"
  local module main_class app_name jar_path main_jar stage app dmg
  module="$(edition_module "$edition")"
  main_class="$(edition_mainclass "$edition")"
  app_name="$(edition_appname "$edition")"

  jar_path="$(ls -t "$module"/target/"$module"-*-jar-with-dependencies.jar 2>/dev/null | head -1 || true)"
  if [ -z "$jar_path" ]; then
    echo "ERROR: jar-with-dependencies not found under $module/target/" >&2; exit 1
  fi
  main_jar="$(basename "$jar_path")"

  stage="build/mac/input/$edition"
  mkdir -p "$stage"
  echo "==> [$edition] staging $main_jar + res/ ..."
  cp "$jar_path" "$stage/"
  cp forge-gui/forge.profile.properties.example "$stage/" 2>/dev/null || true
  # APFS copy-on-write clone of res (instant, no extra disk); fall back to rsync.
  cp -Rc forge-gui/res "$stage/res" 2>/dev/null || rsync -a forge-gui/res "$stage/"

  # Redesign: bundle JavaFX (arm64) so the desktop home can host the HTML design in a
  # WebView. jpackage puts every jar in --input on the classpath. Desktop only.
  if [ "$edition" = "desktop" ]; then
    JFX_VER=17.0.13
    for a in javafx-base javafx-graphics javafx-media javafx-controls javafx-swing javafx-web; do
      jfx_jar="$HOME/.m2/repository/org/openjfx/$a/$JFX_VER/$a-$JFX_VER-mac-aarch64.jar"
      if [ -f "$jfx_jar" ]; then cp "$jfx_jar" "$stage/"; else
        echo "WARN: missing $jfx_jar (WebView home will fall back to Command Center)"; fi
    done
  fi

  echo "==> [$edition] jpackage: building \"$app_name.app\" ..."
  jpackage --type app-image \
    --name "$app_name" --app-version "$APP_VERSION" \
    --input "$stage" --main-jar "$main_jar" --main-class "$main_class" \
    --icon forge-gui-desktop/src/main/config/Forge.icns \
    --runtime-image "$RUNTIME" \
    "${JOPTS[@]}" \
    --dest "$OUT"

  app="$OUT/$app_name.app"
  echo "==> [$edition] ad-hoc codesign + clearing quarantine ..."
  codesign --force --deep --sign - "$app"
  xattr -dr com.apple.quarantine "$app" 2>/dev/null || true

  echo "==> [$edition] jpackage: building DMG ..."
  jpackage --type dmg \
    --name "$app_name" --app-version "$APP_VERSION" \
    --app-image "$app" \
    --dest "$OUT"
  dmg="$(ls -t "$OUT/$app_name"-*.dmg 2>/dev/null | head -1 || true)"

  RESULTS+=("$app_name|$ROOT/$app|${dmg:+$ROOT/$dmg}")
}

for e in "${EDITIONS[@]}"; do build_edition "$e"; done

echo
echo "============================================================"
echo " Done."
for r in "${RESULTS[@]}"; do
  IFS='|' read -r name app dmg <<< "$r"
  echo "  $name"
  echo "     App: $app"
  echo "     DMG: ${dmg:-(not produced)}"
done
echo
echo " Install: open a DMG and drag the app onto Applications,"
echo "          or drag the .app straight into /Applications."
echo " Native arm64, bundled JRE, no system Java needed."
echo " Saves/settings live in ~/Library/Application Support/Forge (shared)."
echo "============================================================"
