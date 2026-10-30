#!/usr/bin/env bash
set -euo pipefail
APP_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$(cd -- "$APP_DIR/../../.." && pwd)"
OUT="$ROOT/out/kof-tests"
mkdir -p "$OUT/classes" "$OUT/previews"
mapfile -d '' -t sources < <(find "$ROOT/base/com/android" "$APP_DIR/src" "$APP_DIR/tests" -name '*.java' -print0)
javac -encoding UTF-8 -source 8 -target 8 -cp "$ROOT/libs/*" -d "$OUT/classes" "${sources[@]}"
python3 "$ROOT/base/tools/install_fonts.py" --classes "$OUT/classes"
java -Djava.awt.headless=true -Dframework.kof.sound=false -cp "$OUT/classes:$APP_DIR:$ROOT/libs/*" com.packages.kof.HeroRosterTest "$OUT/previews"
