#include <cstdio>
#include <jni.h>

JNIEnv* create_vm(JavaVM **jvm)
{
    printf("%s\n",__func__);
    JNIEnv* env = NULL;
    JavaVMInitArgs args;
    args.version = JNI_VERSION_1_8;
    // Bootstrap JVM options come from JAVA_TOOL_OPTIONS in build.sh.
    // Application APKs are loaded by ActivityThread after fork.
    args.nOptions = 0;
    args.options = NULL;
    args.ignoreUnrecognized = 0;
    int rv = JNI_CreateJavaVM(jvm, (void**)&env, &args);
    if (rv < 0 || !env)
        fprintf(stderr, "Unable to launch JVM: %d\n", rv);
    else
        printf("Launched JVM! :)\n");
    return env;
}

// JNI does not print exceptions automatically when native code invokes Java main.
static int startup_failed(JNIEnv* env, const char* stage)
{
    fprintf(stderr, "app_process: startup failed during %s\n", stage);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    fflush(stderr);
    return 1;
}

int invoke_class(JNIEnv* env)
{
    printf("%s\n", __func__);
    jclass zygote_init_class = env->FindClass("com/android/ZygoteInit");
    if (!zygote_init_class || env->ExceptionCheck())
        return startup_failed(env, "loading ZygoteInit");
    jmethodID main_method = env->GetStaticMethodID(zygote_init_class, "main", "([Ljava/lang/String;)V");
    if (!main_method || env->ExceptionCheck())
        return startup_failed(env, "resolving ZygoteInit.main");
    env->CallStaticVoidMethod(zygote_init_class, main_method, NULL);
    if (env->ExceptionCheck())
        return startup_failed(env, "ZygoteInit.main");
    return 0;
}

int main()
{
    JavaVM* jvm;
    JNIEnv* env = create_vm(&jvm);
    if (env == NULL) return 1;
    // A Java startup exception must also be visible to build.sh as a failed process.
    return invoke_class(env);
}
