#!/usr/bin/env bash
set -euo pipefail
# Always run from src, including when invoked as bash src/build.sh.
VPATH="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$VPATH"
echo "$VPATH"

JDK_HOME="${JDK_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
JNI_INC=("-I${JDK_HOME}/include" "-I${JDK_HOME}/include/linux" "-I${VPATH}")
JVM_LIB="${JDK_HOME}/jre/lib/amd64/server"
CP="../libs/xmlpull-1.1.3.1.jar:../libs/kxml2-2.3.0.jar"
FRAMEWORK_CLASSES="$VPATH/../out/framework/classes"
mkdir -p "$FRAMEWORK_CLASSES"

# Compile all framework sources, rather than depending on stale class files.
echo "编译 java 部分及 JNI 头文件"
mapfile -d '' -t FRAMEWORK_SOURCES < <(find com/android -name '*.java' -print0)
javac -encoding UTF-8 -cp "$CP" -h . -d "$FRAMEWORK_CLASSES" "${FRAMEWORK_SOURCES[@]}"

echo "Build all applications in packages/apps"
bash "$VPATH/../packages/apps/build.sh" --framework-classes "$FRAMEWORK_CLASSES"

echo "编译android动态库"
gcc -fPIC "${JNI_INC[@]}" -shared -o com/android/libandroid.so com/android/Zygote.c
export LD_LIBRARY_PATH="$VPATH/com/android:${LD_LIBRARY_PATH:-}"

echo "编译window动态库"
gcc -fPIC "${JNI_INC[@]}" -shared -o com/android/libwindow.so com/android/XWindow.c -lX11

echo "编译 app_process"
g++ -D__int64="long long" "${JNI_INC[@]}" -o app_process app_main.cpp -L"$JVM_LIB" -ljvm
export LD_LIBRARY_PATH="$JVM_LIB:$LD_LIBRARY_PATH"

# JAVA_TOOL_OPTIONS is read by JNI_CreateJavaVM. Only framework bootstrap paths belong here.
# Application APK paths travel with ApplicationStartInfo, never in the bootstrap classpath.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.class.path=\"$FRAMEWORK_CLASSES:$VPATH/../libs/xmlpull-1.1.3.1.jar:$VPATH/../libs/kxml2-2.3.0.jar\""

echo "执行 app_process，启动 SystemServer"
./app_process
