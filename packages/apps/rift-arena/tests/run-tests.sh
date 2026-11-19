#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$ROOT"
bash packages/apps/build.sh rift-arena
python3 base/tools/install_fonts.py --classes out/framework/classes
TEST_WORK="$(mktemp -d /tmp/rift-arena-tests.XXXXXX)"
trap 'rm -rf -- "$TEST_WORK"' EXIT
mkdir -p "$TEST_WORK/classes" out/rift-arena-tests
ARENA_JDK="$(dirname -- "$(dirname -- "$(readlink -f -- "$(command -v javac)")")")"
gcc -O2 -std=c11 -Wall -Wextra -Werror -fPIC -shared -I"$ARENA_JDK/include" -I"$ARENA_JDK/include/linux" -Ibase -o "$TEST_WORK/libwindow.so" base/com/android/XWindow.c -lX11
gcc -O2 -std=c11 -Wall -Wextra -Werror -I"$ARENA_JDK/include" -I"$ARENA_JDK/include/linux" -Ibase -o "$TEST_WORK/xwindow-pixels" tests/native/xwindow_pixels_test.c -lX11
"$TEST_WORK/xwindow-pixels"
APP_CP="out/framework/classes:out/system/app/rift-arena/rift-arena.apk"
javac -encoding UTF-8 -source 8 -target 8 -cp "$APP_CP" -d "$TEST_WORK/classes" packages/apps/rift-arena/tests/ArenaOptimizationTest.java packages/apps/rift-arena/tests/ArenaAnimationRenderTest.java packages/apps/rift-arena/tests/ArenaRigTest.java packages/apps/rift-arena/tests/ArenaMusicTest.java packages/apps/rift-arena/tests/ArenaAssetsTest.java packages/apps/rift-arena/tests/ArenaWorldTest.java packages/apps/rift-arena/tests/ArenaSwordTest.java packages/apps/rift-arena/tests/ArenaArrowTest.java packages/apps/rift-arena/tests/ArenaRetreatTest.java packages/apps/rift-arena/tests/ArenaScatterTest.java packages/apps/rift-arena/tests/ArenaHitTest.java packages/apps/rift-arena/tests/ArenaSlamTest.java packages/apps/rift-arena/tests/ArenaResponseTest.java packages/apps/rift-arena/tests/ArenaDashTest.java packages/apps/rift-arena/tests/ArenaMeteorTest.java packages/apps/rift-arena/tests/ArenaPerformanceTest.java packages/apps/rift-arena/tests/ArenaSceneCacheTest.java packages/apps/rift-arena/tests/ArenaUiTest.java packages/apps/rift-arena/tests/RiftArenaAppTest.java
if [[ "${1:-}" == "--profile" ]]; then
    xvfb-run -a -s '-screen 0 1280x900x24 +extension GLX' java -Djava.awt.headless=true -Dframework.riftarena.profile=true -Dframework.render.profile=true -Djava.library.path="$TEST_WORK:$ROOT/base/com/android" -Dorg.lwjgl.system.SharedLibraryExtractPath="$TEST_WORK/profile-native" -cp "$TEST_WORK/classes:out/framework/classes:libs/*" com.packages.riftarena.tests.RiftArenaAppTest
    exit 0
fi
if [[ "${1:-}" == "--performance" ]]; then
    xvfb-run -a -s '-screen 0 1280x900x24 +extension GLX' java -Djava.awt.headless=true -Dorg.lwjgl.system.SharedLibraryExtractPath="$TEST_WORK/perf-native" -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaPerformanceTest
    exit 0
fi
java -Djava.awt.headless=true -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaOptimizationTest
java -Djava.awt.headless=true -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaWorldTest
java -Djava.awt.headless=true -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaScatterTest
java -Djava.awt.headless=true -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaRetreatTest
java -Djava.awt.headless=true -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaArrowTest
java -Djava.awt.headless=true -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaSwordTest
java -Djava.awt.headless=true -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaHitTest
java -Djava.awt.headless=true -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaSlamTest
java -Djava.awt.headless=true -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaResponseTest
java -Djava.awt.headless=true -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaDashTest
java -Djava.awt.headless=true -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaMeteorTest
java -Djava.awt.headless=true -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaMusicTest
java -Djava.awt.headless=true -Dorg.lwjgl.system.SharedLibraryExtractPath="$TEST_WORK/asset-native" -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaAssetsTest
java -Djava.awt.headless=true -Dorg.lwjgl.system.SharedLibraryExtractPath="$TEST_WORK/rig-native" -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaRigTest
xvfb-run -a -s '-screen 0 1280x900x24 +extension GLX' java -Djava.awt.headless=true -Dorg.lwjgl.system.SharedLibraryExtractPath="$TEST_WORK/cache-native" -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaSceneCacheTest
xvfb-run -a -s '-screen 0 1280x900x24 +extension GLX' java -Djava.awt.headless=true -Dorg.lwjgl.system.SharedLibraryExtractPath="$TEST_WORK/rig-render-native" -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaAnimationRenderTest
xvfb-run -a -s '-screen 0 1280x900x24 +extension GLX' java -Djava.awt.headless=true -Dorg.lwjgl.system.SharedLibraryExtractPath="$TEST_WORK/native" -cp "$TEST_WORK/classes:$APP_CP" com.packages.riftarena.ArenaUiTest
xvfb-run -a -s '-screen 0 1280x900x24 +extension GLX' java -Djava.awt.headless=true -Djava.library.path="$TEST_WORK:$ROOT/base/com/android" -Dorg.lwjgl.system.SharedLibraryExtractPath="$TEST_WORK/isolated-native" -cp "$TEST_WORK/classes:out/framework/classes:libs/*" com.packages.riftarena.tests.RiftArenaAppTest
