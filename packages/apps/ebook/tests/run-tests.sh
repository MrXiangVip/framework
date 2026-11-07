#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../../.." && pwd)"
cd "$ROOT"
bash packages/apps/ebook/build.sh
python3 base/tools/install_fonts.py --classes out/framework/classes
mkdir -p out/ebook-tests/classes
TEST_HOME="$(mktemp -d)"
trap 'rm -rf -- "$TEST_HOME"' EXIT
javac -encoding UTF-8 -cp 'out/framework/classes:out/system/app/ebook/ebook.apk' -d out/ebook-tests/classes packages/apps/ebook/tests/*.java
java -Djava.awt.headless=true -Duser.home="$TEST_HOME" -cp 'out/ebook-tests/classes:out/framework/classes:out/system/app/ebook/ebook.apk:libs/*' com.packages.ebook.EbookSmokeTest

java -Djava.awt.headless=true -Duser.home="$TEST_HOME/imports" -cp 'out/ebook-tests/classes:out/framework/classes:out/system/app/ebook/ebook.apk:libs/*' com.packages.ebook.BookImportTest

java -Djava.awt.headless=true -Duser.home="$TEST_HOME/markdown" -cp 'out/ebook-tests/classes:out/framework/classes:out/system/app/ebook/ebook.apk:libs/*' com.packages.ebook.MarkdownImportTest

java -Djava.awt.headless=true -Duser.home="$TEST_HOME/pdf" -cp 'out/ebook-tests/classes:out/framework/classes:out/system/app/ebook/ebook.apk:libs/*' com.packages.ebook.PdfImportTest

java -Djava.awt.headless=true -Duser.home="$TEST_HOME/swipe" -cp 'out/ebook-tests/classes:out/framework/classes:out/system/app/ebook/ebook.apk:libs/*' com.packages.ebook.SwipePageTest
