# Framework 项目设计说明

> 本文档基于当前仓库源码整理，目标是说明项目为什么这样设计、各子系统如何协作，以及开发者如何构建、运行和扩展它。
>
> 项目定位：在 Linux 桌面 JVM/X11 环境中，用 Java 实现一个具有 Android Framework 编程模型的轻量运行时。它借鉴 Android 的组件边界和启动链路，但不追求 Android 设备兼容性，也不实现完整的 Binder、Dex、SELinux、权限和多用户模型。

## 1. 项目目标与设计取舍

### 1.1 解决的问题

传统 Android Framework 依赖设备内核、SurfaceFlinger、Binder、Zygote、ART/Dalvik 和 APK/DEX 运行时，学习和调试成本很高。本项目把问题拆成几个可以在桌面上直接观察的部分：

- 用 Java 类表达 Activity、View、Window、PackageManager 和 ActivityManager 等核心概念。
- 用标准 JVM 启动应用，用 Java class/jar 取代 DEX，用 ZIP/JAR 取代真正的 APK 安装格式。
- 用 X11 创建窗口，用 Java AWT `BufferedImage` 完成软件渲染。
- 用本地回环 TCP 连接模拟系统服务之间的 IPC，用独立 JVM 进程或可选的 `fork` 模式模拟应用进程隔离。
- 用清晰的构建产物和测试夹具，让框架行为可以在没有 Android 设备的环境中回归。

### 1.2 明确的边界

项目追求的是“可理解、可运行、可实验”的系统骨架，而不是 Android API 的完整复刻。源码中明确保留了若干简化点：

- 应用包是包含 Java 字节码、文本 Manifest 和 `res/` 的 ZIP/JAR，不是可安装到 Android 的签名 APK。
- 类加载使用 JVM `URLClassLoader`/应用专属 ClassLoader，启动类路径只放 Framework 类；应用 APK 路径通过启动元数据传递。
- PMS 做启动扫描和 Intent/Activity 查询，但没有持久化设置、签名校验、Dex 优化、权限校验、多用户和完整包广播。
- WMS/ActivityTaskManager 保留 Android 风格接口和职责边界，实际窗口协调主要落在 `ViewRootImpl`/`XWindow`。
- UI 使用软件帧和 X11 Present，不提供 SurfaceFlinger、GPU 合成和真实 VSYNC。

这些取舍让模块可以独立阅读、测试和替换，也避免为了模拟 Android 而引入一套无法在桌面运行的底层依赖。

## 2. 仓库结构

```text
base/
  app_main.cpp                 JNI_CreateJavaVM 入口，启动 SystemServer/应用主类
  Makefile、build.sh           基础构建脚本
  com/android/
    app/                       ActivityThread、Activity、Context 等应用运行时
    am/                        ActivityManagerService 与客户端协议
    pm/                        Manifest 解析、包扫描、Activity 查询
    os/                        Looper、Handler、Process、Zygote、启动元数据
    server/                    SystemServer 启动编排
    wm/                        WindowManager、ActivityTaskManager、X11 窗口封装
    view/、widget/、graphics/  View 树、布局、控件和软件 Canvas
    policy/                    PhoneWindow、DecorView 等窗口策略对象
  com/packages/                作为源码目录保留的内置/遗留包
  *.c、*.h、*.so               JNI 与 X11/native 窗口桥接
packages/apps/
  <app>/                       每个应用的 Manifest、src、res、可选 libs
  build.sh                     批量发现、编译和打包应用
libs/                          XML Pull/KXml 等构建依赖
out/
  framework/classes/           编译后的 Framework class
  system/app/<name>/<name>.apk 应用产物
  launcher-preview.png 等      示例运行截图
 tests/                        Java、Python、C smoke/integration 测试
 build.sh                      从编译 Framework 到启动系统的总入口
```

`packages/apps` 的直接子目录只要同时包含 `AndroidManifest.xml` 和 `src/`，就能被批量构建脚本自动发现。新增应用不需要修改中央注册表。

## 3. 总体架构

```text
                    ┌──────────────────────────┐
                    │       app_process        │
                    │  创建 JVM / 调 SystemServer│
                    └────────────┬─────────────┘
                                 │
                    ┌────────────▼─────────────┐
                    │       SystemServer       │
                    │ Looper + 系统服务启动编排 │
                    └──────┬────────┬───────────┘
                           │        │
             ┌─────────────▼─┐  ┌──▼────────────────┐
             │ PackageManager │  │ ActivityManager   │
             │ 扫描/解析/查询  │  │ 启动/桌面/IPC      │
             └─────────────┬─┘  └──┬────────────────┘
                           │        │ Zygote 协议
                           │        ▼
                           │  ┌───────────────┐
                           │  │ ZygoteServer  │
                           │  │ spawn / fork  │
                           │  └──────┬────────┘
                           │         ▼
                           │  ┌───────────────┐
                           └─►│ ActivityThread│
                              │ LoadedApk + UI │
                              └──────┬────────┘
                                     ▼
                              ViewRootImpl / XWindow
                                     │
                                     ▼
                                   X11
```

架构的关键思想是“职责分层、数据显式传递”：PMS 只负责知道包和组件，AMS 只负责决定启动什么，Zygote 只负责创建应用进程，ActivityThread 只负责应用进程内的 Activity 生命周期，ViewRootImpl 只负责 View 树、测量、绘制和窗口事件。

## 4. 启动流程

### 4.1 构建阶段

根目录 `build.sh` 先定位仓库和 `base`，然后按以下顺序执行：

1. 编译 `base/com/android/**/*.java`，输出到 `out/framework/classes`，并生成 JNI 头文件。
2. 安装 CJK/Emoji 字体到 Framework 输出目录，保证桌面和控件可以显示中文。
3. 调用 `packages/apps/build.sh --framework-classes`，为每个应用编译源码并打包。
4. 编译 `libandroid.so`、`libwindow.so` 和 `app_process`。
5. 设置 `JAVA_TOOL_OPTIONS`，只把 Framework class 与 XML 依赖放进 Bootstrap classpath。
6. 进入 `input-method.sh` 后执行 `app_process`。

应用构建使用临时目录，打包成功后才替换旧产物；这避免编译失败留下半成品。`required-apk-entries.txt` 可以声明运行时必须存在的资源或第三方类，脚本会在移动 APK 前校验它们。

### 4.2 SystemServer 启动

`app_process` 创建 JVM 后进入 `SystemServer.main()`。SystemServer 在主线程创建主 Looper、构造系统 Context，然后依次启动：

- `ActivityManagerService`：监听本地回环端口，提供启动 Activity 和查询桌面 Activity 的请求。
- `Installer` 与 `PackageManagerService`：扫描系统分区、`out/system`、`data/app` 和遗留源码包目录。
- `WindowManagerService`：建立窗口管理服务单例，并接收窗口策略和 ActivityTaskManager 引用。

`systemReady()` 会启动 AMS 请求线程，并根据 `CATEGORY_HOME` 查找 Home Activity；默认情况下 Launcher 因此成为第一个应用进程。

### 4.3 应用启动

一次从 Launcher 点击图标到窗口出现的路径如下：

1. Launcher 通过 `ActivityManagerClient` 向 AMS 发送操作码和 Activity 全名。
2. AMS 通过 PMS 反查 Activity 所属包、APK 路径和包名。
3. `Process.start()` 将 `ApplicationStartInfo(packageName, activityName, apkPath)` 写入 Zygote socket。
4. Zygote 根据 `framework.process.mode` 选择：
   - 默认 `spawn`：`DesktopProcess` 启动独立 JVM，并进入 `ActivityThread`。
   - `fork`：JNI 调用 `forkAndSpecialize()`，子进程进入 `ZygoteInit.childZygoteInit()`。
5. ActivityThread 解析启动参数，创建 `LoadedApk` 和应用 ClassLoader，把线程上下文 ClassLoader 切换到应用类加载器。
6. 实例化 Activity，调用 `onCreate()`，再调用 `onResume()`。
7. Activity 的 DecorView 通过 `WindowManagerImpl` 加入 `WindowManagerGlobal`，最终由 `ViewRootImpl` 创建 X11 窗口并开始遍历绘制。

应用路径只作为启动元数据传递，而不是直接加入系统 Bootstrap classpath。这一设计避免不同应用的类互相污染，也让每个应用可以携带自己的依赖。

## 5. 包管理与组件发现

PMS 的构造函数把可扫描位置抽象成 `SystemPartition`，包括 `system`、`vendor`、`odm`、`oem`、`product`、`system_ext`、`out/system`、`data/app` 和 `base/com/packages`。扫描过程使用固定大小线程池并行解析，最后按文件名排序后注册，保证并行完成顺序不会改变重复包的胜负结果。

解析后的包以 `TreeMap` 保存，Activity 查询都在锁内完成并返回不可修改集合。重要查询包括：

- `getPackageForActivity()`：根据 Activity 全名反查所属包和 APK。
- `queryIntentActivities(action, category)`：只返回 enabled 且 exported、Intent 匹配的组件。
- `resolveHomeActivity()`：查找 `MAIN + HOME`。
- `getLauncherActivities()`：查找 `MAIN + LAUNCHER`，过滤 Home，按包名和 Activity 名排序，并返回标签、图标路径和 APK 路径。

因此 Launcher 不通过“猜目录名”或硬编码类名发现应用，而是读取 Manifest 声明。这是项目可扩展性的核心：应用的可见性由声明决定，桌面无需随应用数量变化而修改。

## 6. 应用包与 Manifest 约定

应用目录至少需要：

```text
packages/apps/sample/
  AndroidManifest.xml
  src/com/example/sample/MainActivity.java
  res/layout/activity_main.xml   # 可选
  res/drawable/icon.png          # 可选
  libs/*.jar                     # 可选
```

应用是桌面 JVM 包，Manifest 主要承担组件元数据作用。一个可出现在 Launcher 中的 Activity 至少需要声明 `MAIN` 和 `LAUNCHER`，Home 则声明 `MAIN` 和 `HOME`。应用或 Activity 可以提供字面量 `android:label`，并可通过 `android:icon="res/drawable/icon.png"` 提供图标。

打包时：

- 编译目标为 Java 8 字节码。
- Framework class 通过 `-cp` 提供，但不会被复制到应用包。
- 应用自己的 class、Manifest 和 `res/` 进入 ZIP/JAR。
- 应用 `libs/` 中的 jar 会解包合并，签名文件会被移除。
- 输出到 `out/system/app/<app>/<app>.apk`，供 PMS 下次启动扫描。

这里的 `.apk` 是兼容项目内部加载器的命名约定，不应当直接交给 Android Package Installer。

## 7. UI、窗口与渲染模型

### 7.1 从 WindowManager 到 X11

Activity 调用 `setContentView()` 后，窗口最终通过 `WindowManagerImpl.addView()` 进入 `WindowManagerGlobal`。Global 用 `IdentityHashMap<View, ViewRootImpl>` 防止同一个 View 被重复挂载，并负责 add/update/remove 的生命周期。

`ViewRootImpl` 是 UI 树和平台窗口之间的边界：

1. 保存根 View 和 `WindowManager.LayoutParams`。
2. 根据 X11 屏幕可用区域和 LayoutParams 测量窗口尺寸。
3. 创建 `XWindow`，把 View 绑定为根节点。
4. 收到 resize、鼠标、键盘、滚轮、最小化和关闭事件后，把事件转发到 View 树。
5. 通过 `Choreographer` 合并多次 `requestLayout()`/`invalidate()`，在 UI Looper 上执行一次遍历和呈现。

### 7.2 尺寸规则

`computeWindowSize()` 统一处理窗口尺寸：正数表示像素尺寸，`MATCH_PARENT` 填满显示区域，`WRAP_CONTENT` 在显示区域上限内以 `AT_MOST` 测量。最终尺寸被限制在 `[1, displaySize]`，因为 X11 不接受零尺寸窗口。非法负值会立即抛出异常。

窗口外部 resize 会重新测量和布局；内容调用 `requestLayout()` 会让尺寸变脏并触发下一次 traversal。当前实现以整个 X11 默认屏幕作为显示区域，还没有桌面面板扣除、Insets、gravity、windowing mode 或多显示器策略。

### 7.3 软件绘制

`renderToImage()` 使用 Java `BufferedImage(TYPE_INT_RGB)`，先以白色清屏，再调用 View 的 `draw(Canvas)`。`XWindow.nativePresent()` 将像素数组交给 JNI/X11。这样做的优点是渲染路径短、容易在 headless 测试中复用；代价是没有 GPU 合成，复杂场景的性能和色彩能力有限。

X11 事件由独立的单线程 executor 定时轮询，每 25ms 批量转发到 UI Handler。窗口最小化时取消触摸并暂停 traversal，恢复后重新调度；关闭时保证 native 窗口、事件线程、View 父引用和 Window.Callback 都被清理。

## 8. 进程与 IPC 设计

项目使用两条本地 TCP 协议模拟系统 IPC：

- AMS 默认监听 `127.0.0.1:10001`，请求包含操作码：`1` 启动 Activity，`2` 获取 Launcher 列表；响应以状态码开头，失败时附带截断后的错误文本。
- Zygote 默认监听端口 `10000`，传输版本号、包名、Activity 全名和 APK 路径。协议版本不匹配或字段为空会被拒绝。

端口和进程模式使用 JVM 属性配置：

```bash
-Dframework.ams.port=10001
-Dframework.zygote.port=10000
-Dframework.process.mode=spawn   # 默认；可选 fork
-Dframework.root=/path/to/repository
```

协议使用显式的 `ApplicationStartInfo`，而不是拼接命令行字符串，减少空格、路径和参数边界问题。spawn 模式更适合桌面调试和跨平台运行；fork 模式更接近 Android 的 Zygote 思路，但依赖 JNI 和 Linux 进程语义。

## 9. 线程模型

- SystemServer 主线程：创建系统 Context、运行主 Looper，并承载系统服务初始化。
- AMS 请求线程：阻塞等待客户端连接，串行处理启动和 Launcher 查询。
- PMS 扫描线程池：并行解析包，主线程按确定顺序注册。
- 每个 ActivityThread：一个应用进程和一个主 Looper，Activity 生命周期与 View 操作集中在该线程。
- Choreographer Handler：把 traversal 合并到应用主 Looper。
- XWindow 事件线程：轮询 native 事件，再通过 Handler 切回 UI 线程。

该模型有意保持“UI 单线程、后台工作显式”的约束。新增异步功能时，应避免从 PMS/AMS 工作线程直接修改 View 树。

## 10. 构建、运行与验证

环境要求来自仓库 README：Ubuntu 20.04、OpenJDK 8（更高版本需确认 JNI/JVM 路径）和 IntelliJ IDEA。Linux 下从仓库根目录执行：

```bash
./build.sh
```

只编译不启动：

```bash
./build.sh --build-only
```

只构建应用：

```bash
bash packages/apps/build.sh
bash packages/apps/build.sh settings camera
```

单应用入口：

```bash
bash packages/apps/launcher/build.sh
# 或
powershell -ExecutionPolicy Bypass -File packages/apps/launcher/build.ps1
```

窗口尺寸回归测试示例：

```bash
FRAMEWORK_CLASSES=out/framework/classes
javac -cp "$FRAMEWORK_CLASSES" -d /tmp/window-tests tests/com/android/wm/WindowSizeTest.java
java -cp "$FRAMEWORK_CLASSES:/tmp/window-tests" com.android.wm.WindowSizeTest
```

仓库还包含浏览器、中文输入、Multi-Activity、电子书、3D 场景和应用集成 smoke 测试。推荐验证顺序是：先 `--build-only`，再执行纯 Java/headless 测试，最后在有 `DISPLAY` 的 Linux 会话中运行完整框架和 X11 测试。

## 11. 扩展指南

### 新增应用

1. 在 `packages/apps` 创建目录和 `AndroidManifest.xml`。
2. 添加 `src/` 下的 Java 源码，并让 Activity 继承 Framework 的 `com.android.app.Activity`。
3. 在 Manifest 声明组件、label、图标和 Intent category。
4. 运行 `bash packages/apps/build.sh <name>` 或根目录 `./build.sh`。
5. 重启 Framework；PMS 会重新扫描，Launcher 会按声明自动显示。

### 新增控件或布局

优先沿用 `View` → `ViewGroup` → `WindowManagerGlobal` → `ViewRootImpl` 的边界。测量逻辑放在 View，窗口尺寸规则放在 ViewRootImpl，native/X11 调用集中放在 `XWindow`，避免控件直接依赖 JNI。

### 新增系统服务

在 `SystemServer` 中确定启动阶段和依赖关系；需要客户端访问时定义独立协议和客户端类；不要把包扫描、进程创建或窗口绘制逻辑塞进 SystemServer。服务应暴露小而明确的接口，并为端口、协议版本和失败响应提供测试。

### 替换进程启动策略

保持 `ApplicationStartInfo` 作为稳定边界。可以替换 `DesktopProcess` 的 spawn 实现，或扩展 `ZygoteServer` 的 fork 实现，而不改变 AMS/PMS 和 ActivityThread 的调用关系。

## 12. 当前限制与后续演进方向

优先级较高的演进方向包括：

- 将 AMS 的 Activity 记录、任务栈和 `ActivityTaskManagerService` 从空壳扩展为可查询状态机。
- 为 WMS 增加窗口 token、层级、gravity、Insets、多显示器和真正的 relayout 处理。
- 将 X11 事件轮询改为可配置的事件源，并引入真实帧时钟或可控的测试时钟。
- 增加包版本、更新、卸载、权限和持久化设置模型。
- 为 Framework 和每个应用补充统一的日志、错误码和协议兼容策略。
- 在 CI 中分别运行 headless 测试和需要 X11 的测试，避免图形环境差异掩盖逻辑回归。
- 逐步修正文档与旧源码中的编码混乱，统一 UTF-8，并把稳定的设计约束写成测试。

这些方向都应继续遵循当前项目的核心原则：先明确边界，再通过小型可验证模块增加能力；让应用依赖 Framework 的公开行为，而不是依赖 native 实现细节。

## 13. 关键文件索引

| 关注点 | 文件 |
|---|---|
| 总构建与启动 | `build.sh`, `base/app_main.cpp` |
| 系统服务启动 | `base/com/android/server/SystemServer.java` |
| 包扫描与组件查询 | `base/com/android/pm/PackageManagerService.java` |
| Activity 启动与客户端协议 | `base/com/android/am/ActivityManagerService.java`, `base/com/android/am/ActivityManagerClient.java` |
| 进程启动 | `base/com/android/os/Process.java`, `ZygoteServer.java`, `ZygoteProcess.java`, `ApplicationStartInfo.java` |
| 应用生命周期 | `base/com/android/app/ActivityThread.java`, `Activity.java`, `LoadedApk.java` |
| 窗口与布局 | `base/com/android/view/WindowManagerGlobal.java`, `WindowManagerImpl.java`, `ViewRootImpl.java` |
| X11 与输入 | `base/com/android/wm/XWindow.java`, `base/com/android/XWindow.c` |
| 桌面 | `packages/apps/launcher/src/com/packages/launcher/Launcher.java` |
| 应用打包 | `packages/apps/build.sh`, `packages/apps/README.md` |
| 回归测试 | `tests/`, 各应用下的 `tests/` |

---

文档版本：基于当前工作区源码整理。代码行为发生变化时，应优先更新对应章节和关键文件索引。
