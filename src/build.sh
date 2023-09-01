#!/bin/bash

VPATH=$(pwd)
echo ${VPATH}
javah -jni com.android.os.Zygote
echo "编译动态库"
cd ${VPATH}/com/android
gcc -fPIC  -I/usr/lib/jvm/java-8-openjdk-amd64/include  -I/usr/lib/jvm/java-8-openjdk-amd64/include/linux  -I${VPATH} -shared -o libandroid.so  Zygote.c
echo "导出动态库路径 $(pwd)"
export LD_LIBRARY_PATH=$(pwd)/:$LD_LIBRARY_PATH
# 图方便可以将libandroid.so 拷贝到 /usr/lib/x86_64-linux-gnu/jni


cd ${VPATH}
echo $(pwd)
echo "编译 app_process"
g++ -D__int64="long long" -I/usr/lib/jvm/java-8-openjdk-amd64/include  -I/usr/lib/jvm/java-8-openjdk-amd64/include/linux -o app_process app_main.cpp  -L/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server -ljvm

# 编译java部分
echo "编译 java 部分"
javac       com/android/ZygoteInit.java
javac       com/android/server/SystemServer.java
javac       com/android/app/ActivityThread.java
javac       com/android/packages/launcher/Launcher.java

echo "执行.."
./app_process