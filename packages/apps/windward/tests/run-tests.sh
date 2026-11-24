#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$ROOT"
bash packages/apps/build.sh windward
python3 base/tools/install_fonts.py --classes out/framework/classes
WINDWARD_TEST_DIR="$(mktemp -d /tmp/windward-tests.XXXXXX)"
trap 'rm -rf -- "$WINDWARD_TEST_DIR"' EXIT
mkdir -p out/windward-tests
APP_CP="out/framework/classes:out/system/app/windward/windward.apk"
javac -encoding UTF-8 -source 8 -target 8 -cp "$APP_CP" -d "$WINDWARD_TEST_DIR" packages/apps/windward/tests/MountainTest.java packages/apps/windward/tests/WindowTest.java packages/apps/windward/tests/AssetTest.java packages/apps/windward/tests/TraversalTest.java packages/apps/windward/tests/CombatTest.java packages/apps/windward/tests/PuzzleTest.java packages/apps/windward/tests/PerformanceTest.java
if [[ "${1:-}" == "--profile" ]]; then
    xvfb-run -a -s '-screen 0 1280x900x24 +extension GLX' java -Djava.awt.headless=true -Dorg.lwjgl.system.SharedLibraryExtractPath="$WINDWARD_TEST_DIR/profile-native" -cp "$WINDWARD_TEST_DIR:$APP_CP" com.packages.windward.PerformanceTest "${2:-out/windward-tests/performance.csv}"
    exit 0
fi
java -Djava.awt.headless=true -cp "$WINDWARD_TEST_DIR:$APP_CP" com.packages.windward.MountainTest
java -Djava.awt.headless=true -cp "$WINDWARD_TEST_DIR:$APP_CP" com.packages.windward.TraversalTest
java -Djava.awt.headless=true -cp "$WINDWARD_TEST_DIR:$APP_CP" com.packages.windward.CombatTest
java -Djava.awt.headless=true -cp "$WINDWARD_TEST_DIR:$APP_CP" com.packages.windward.PuzzleTest
java -Djava.awt.headless=true -cp "$WINDWARD_TEST_DIR:$APP_CP" com.packages.windward.AssetTest
python3 packages/apps/windward/tests/check-assets.py
WINDWARD_JDK="$(dirname -- "$(dirname -- "$(readlink -f -- "$(command -v javac)")")")"
gcc -O2 -fPIC -shared -I"$WINDWARD_JDK/include" -I"$WINDWARD_JDK/include/linux" -Ibase -o "$WINDWARD_TEST_DIR/libwindow.so" base/com/android/XWindow.c -lX11
xvfb-run -a -s '-screen 0 1280x900x24 +extension GLX' java -Djava.awt.headless=true -Dorg.lwjgl.system.SharedLibraryExtractPath="$WINDWARD_TEST_DIR/gl-native" -cp "$WINDWARD_TEST_DIR:$APP_CP" com.packages.windward.MountainTest --graphics
xvfb-run -a -s '-screen 0 1280x900x24 +extension GLX' java -Djava.awt.headless=true -Djava.library.path="$WINDWARD_TEST_DIR" -Dorg.lwjgl.system.SharedLibraryExtractPath="$WINDWARD_TEST_DIR/window-native" -cp "$WINDWARD_TEST_DIR:out/framework/classes:libs/*" com.packages.windward.tests.WindowTest
