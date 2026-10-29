#!/usr/bin/env bash
set -euo pipefail

# Resolve all inputs from this script, regardless of the caller's working directory.
REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BASE_DIR="$REPO_ROOT/base"
cd "$BASE_DIR"
echo "$BASE_DIR"

JDK_HOME="${JDK_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
JNI_INC=("-I${JDK_HOME}/include" "-I${JDK_HOME}/include/linux" "-I${BASE_DIR}")
JVM_LIB="${JDK_HOME}/jre/lib/amd64/server"
CP="$REPO_ROOT/libs/xmlpull-1.1.3.1.jar:$REPO_ROOT/libs/kxml2-2.3.0.jar"
FRAMEWORK_CLASSES="$REPO_ROOT/out/framework/classes"
mkdir -p "$FRAMEWORK_CLASSES"

echo "Compile framework Java and JNI headers"
mapfile -d '' -t FRAMEWORK_SOURCES < <(find com/android -name '*.java' -print0)
javac -encoding UTF-8 -cp "$CP" -h . -d "$FRAMEWORK_CLASSES" "${FRAMEWORK_SOURCES[@]}"

python3 "$REPO_ROOT/base/tools/install_fonts.py" --classes "$FRAMEWORK_CLASSES"

echo "Build all applications in packages/apps"
bash "$REPO_ROOT/packages/apps/build.sh" --framework-classes "$FRAMEWORK_CLASSES"

echo "Compile libandroid.so"
gcc -fPIC "${JNI_INC[@]}" -shared -o com/android/libandroid.so com/android/Zygote.c
export LD_LIBRARY_PATH="$BASE_DIR/com/android:${LD_LIBRARY_PATH:-}"

echo "Compile libwindow.so"
gcc -fPIC "${JNI_INC[@]}" -shared -o com/android/libwindow.so com/android/XWindow.c -lX11

echo "Compile app_process"
g++ -D__int64="long long" "${JNI_INC[@]}" -o app_process app_main.cpp -L"$JVM_LIB" -ljvm
export LD_LIBRARY_PATH="$JVM_LIB:$LD_LIBRARY_PATH"

# Bootstrap classpath contains framework classes only; APK paths travel in launch metadata.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.awt.headless=true -Djava.class.path=\"$FRAMEWORK_CLASSES:$CP\""

if [[ "${1:-}" == "--build-only" ]]; then exit 0; fi

source "$BASE_DIR/input-method.sh"

echo "Start app_process / SystemServer"
exec "$BASE_DIR/app_process"
