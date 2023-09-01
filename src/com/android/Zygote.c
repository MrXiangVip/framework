//
// Created by xshx on 2021/5/25.
//
// 相当于 Android_9.0.0_2.0.0-ga/frameworks/base/core/jni/com_android_internal_os_Zygote.cpp
#include "jni.h"
#include "com_android_os_Zygote.h"
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

static pid_t ForkAndSpecializeCommon(JNIEnv *env, jobject obj){
    pid_t fpid = fork();
    if( fpid == 0 ){
        printf("这里是子进程 ID :%d \n",getpid());

//        jclass launcher_class;
//        jmethodID main_method;
//        launcher_class =(*env)->FindClass(env, "com/wave/Launcher");
//        launcher_class =(*env)->FindClass(env, "com/wave/SystemServer");
//        main_method =(*env)->GetStaticMethodID(env, launcher_class, "main","([Ljava/lang/String;)V");
//        (*env)->CallStaticVoidMethod(env,launcher_class, main_method, NULL);
//        if ((*env)->ExceptionCheck(env)) {
//            printf("Error calling post fork hooks. \n");
//        }
//        printf("start launcher.main \n");

    }else {
        printf("这是 父进程 ID :%d \n", getpid());
        int status;
        if (waitpid(fpid, &status, WNOHANG) == fpid) {
            printf("System server process %d has died. Restarting Zygote!", fpid);
        }
    }

    return fpid;
}

// 创建系统进程
JNIEXPORT jint JNICALL Java_com_android_os_Zygote_nativeForkSystemServer (JNIEnv *env, jobject obj){
    printf("Java_com_android_Zygote_nativeForkSystemServer \n");
    return  ForkAndSpecializeCommon(env, obj);

}


// 创建应用进程
JNIEXPORT jint JNICALL Java_com_android_os_Zygote_nativeForkAndSpecialize (JNIEnv *env, jobject obj){
    printf("Java_com_android_Zygote_nativeForkAndSpecialize \n");
    return  ForkAndSpecializeCommon(env, obj);
}

