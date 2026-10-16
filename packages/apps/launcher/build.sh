#!/usr/bin/env bash
set -euo pipefail
APP_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$APP_DIR/../../.." && pwd)"
FRAMEWORK_CLASSES="$REPO_ROOT/out/launcher/framework-classes"
APP_CLASSES="$REPO_ROOT/out/launcher/classes"
APK="$REPO_ROOT/out/system/app/launcher/launcher.apk"
mkdir -p "$FRAMEWORK_CLASSES" "$APP_CLASSES" "$(dirname -- "$APK")"
mapfile -d '' -t FRAMEWORK_SOURCES < <(find "$REPO_ROOT/src/com/android" -name '*.java' -print0)
mapfile -d '' -t APP_SOURCES < <(find "$APP_DIR/src" -name '*.java' -print0)
javac -encoding UTF-8 -source 8 -target 8 -cp "$REPO_ROOT/libs/*" -d "$FRAMEWORK_CLASSES" "${FRAMEWORK_SOURCES[@]}"
javac -encoding UTF-8 -source 8 -target 8 -cp "$FRAMEWORK_CLASSES:$REPO_ROOT/libs/*" -sourcepath "$APP_DIR/src" -d "$APP_CLASSES" "${APP_SOURCES[@]}"
jar cf "$APK" -C "$APP_CLASSES" . -C "$APP_DIR" AndroidManifest.xml -C "$APP_DIR" res
echo "Built: $APK"
