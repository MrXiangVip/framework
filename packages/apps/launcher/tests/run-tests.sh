#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../../.."
bash packages/apps/launcher/build.sh
mkdir -p out/launcher-tests
javac -encoding UTF-8 -cp out/framework/classes:out/system/app/launcher/launcher.apk -d out/launcher-tests packages/apps/launcher/tests/DesktopPagesTest.java
java -Djava.awt.headless=true -cp out/launcher-tests:out/framework/classes:out/system/app/launcher/launcher.apk com.packages.launcher.DesktopPagesTest
