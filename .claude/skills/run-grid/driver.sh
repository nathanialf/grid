#!/usr/bin/env bash
# Build / test / sign driver for the Grid Android app on a headless Linux box.
#
# Grid is an Android (Jetpack Compose) app — there is NO GUI path on this machine
# (no /dev/kvm, so no emulator). The thing you "run" here is the build/test/sign
# pipeline. This script is that pipeline, and it enforces the project's zero-tolerance
# rule: ANY compiler / KSP / lint warning fails the build.
#
# Usage:  ./driver.sh [all|debug|test|lint|release]   (default: all)
#
#   all      clean + assembleDebug + unit tests + lint + signed release APK & AAB
#   debug    assembleDebug only (fast inner loop)
#   test     unit tests only (note: the project currently has no unit tests)
#   lint     lintDebug only (the zero-tolerance gate)
#   release  signed release APK (.apk) + App Bundle (.aab) for Play
#
set -euo pipefail

# Repo root = three levels up from .claude/skills/run-grid/
cd "$(dirname "$0")/../../.."
ROOT="$PWD"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0"

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "ERROR: Android SDK not found at $ANDROID_HOME — see SKILL.md → Prerequisites." >&2
  exit 1
fi

MODE="${1:-all}"
LOG="$(mktemp)"
trap 'rm -f "$LOG"' EXIT

gradle_run() {
  echo "+ ./gradlew $*"
  # pipefail (set above) makes this fail if gradlew fails.
  ./gradlew "$@" 2>&1 | tee "$LOG"
  # Belt-and-suspenders: the build config already promotes warnings to errors, but
  # also fail loudly here on any stray compiler/KSP 'w:' line that slipped through.
  if grep -q "^w: " "$LOG"; then
    echo "" >&2
    echo "ZERO-TOLERANCE FAIL: compiler/KSP warnings detected (the 'w:' lines above). Fix them." >&2
    exit 1
  fi
}

case "$MODE" in
  debug)   gradle_run :app:assembleDebug ;;
  test)    gradle_run :app:testDebugUnitTest ;;
  lint)    gradle_run :app:lintDebug ;;
  release) gradle_run :app:assembleRelease :app:bundleRelease ;;
  all)     gradle_run clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug \
                       :app:assembleRelease :app:bundleRelease ;;
  *) echo "usage: $(basename "$0") [all|debug|test|lint|release]" >&2; exit 2 ;;
esac

# Verify the release signature whenever a release APK was produced.
APK="app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK" ]; then
  echo "=== apksigner verify (release) ==="
  "$BUILD_TOOLS/apksigner" verify --print-certs "$APK" | grep -v "^WARNING: System" | head -3
fi

echo ""
echo "DONE [$MODE] — zero warnings, build green. Artifacts:"
ls -1 app/build/outputs/apk/debug/*.apk \
       app/build/outputs/apk/release/*.apk \
       app/build/outputs/bundle/release/*.aab 2>/dev/null | sed "s#^#  $ROOT/#" || true
