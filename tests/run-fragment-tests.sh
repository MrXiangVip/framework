#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"
TEST_DIR="$(mktemp -d /tmp/framework-fragment-tests.XXXXXX)"
trap 'rm -rf -- "$TEST_DIR"' EXIT
mkdir -p "$TEST_DIR/classes"
mapfile -d '' -t sources < <(find base/com/android -name '*.java' -print0)
javac -encoding UTF-8 -source 8 -target 8 -cp 'libs/*' -d "$TEST_DIR/classes" \
    "${sources[@]}" tests/com/android/app/FragmentTest.java tests/com/android/view/WindowCloseLifecycleTest.java
bash packages/apps/build.sh --framework-classes "$TEST_DIR/classes" fragment-demo
java -Djava.awt.headless=true -cp "$TEST_DIR/classes:libs/*" \
    com.android.app.FragmentTest out/system/app/fragment-demo/fragment-demo.apk
java -Djava.awt.headless=true -cp "$TEST_DIR/classes:libs/*" com.android.view.WindowCloseLifecycleTest
