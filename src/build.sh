#!/bin/bash
VPATH=$(pwd)
echo ${VPATH}

# JDK配置
JDK_HOME=/usr/lib/jvm/java-8-openjdk-amd64
JNI_INC="-I${JDK_HOME}/include -I${JDK_HOME}/include/linux -I${VPATH}"
JVM_LIB="${JDK_HOME}/jre/lib/amd64/server"

# JNI头文件生成（javah 替换成 javac -h ，兼容新标准）
javac -h . com/android/os/Zygote.java
javac -h . com/android/wm/XWindow.java

echo "编译android动态库"
cd ${VPATH}/com/android
gcc -fPIC ${JNI_INC} -shared -o libandroid.so  Zygote.c
echo "导出动态库路径 $(pwd)"
export LD_LIBRARY_PATH=$(pwd)/:${LD_LIBRARY_PATH}

cd ${VPATH}
echo "编译window动态库"
cd ${VPATH}/com/android
gcc -fPIC ${JNI_INC} -shared -o libwindow.so  XWindow.c -lX11

cd ${VPATH}
echo $(pwd)
echo "编译 app_process"
g++ -D__int64="long long" ${JNI_INC} -o app_process app_main.cpp  -L${JVM_LIB} -ljvm
export LD_LIBRARY_PATH=${JVM_LIB}:${LD_LIBRARY_PATH}

# 编译java部分
echo "编译 java 部分"
CP=".:../libs/xmlpull-1.1.3.1.jar:../libs/kxml2-2.3.0.jar"
javac -cp ${CP} com/android/os/Zygote.java
javac -cp ${CP} com/android/ZygoteInit.java
javac -cp ${CP} com/android/server/SystemServer.java
javac -cp ${CP} com/android/app/ActivityThread.java
javac -cp ${CP} com/android/res/Resources.java
javac -cp ${CP} com/packages/launcher/Launcher.java
javac -cp ${CP} com/android/widget/Button.java
javac -cp ${CP} com/android/widget/LinearLayout.java
javac -cp ${CP} com/android/widget/TextView.java
echo "执行 app_process，启动 SystemServer"
# 参数：第一个参数是ANDROID_ROOT，第二个是主类
./app_process
