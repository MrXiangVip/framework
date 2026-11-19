# Android 一帧图像从产生到显示

本文结合 `E:\8668`（当前环境路径 `/mnt/e/8668`）中的 Android 源码，说明一帧图像如何从应用状态变化，经过 View 遍历、GPU 绘制、BufferQueue、SurfaceFlinger 和 HWC，最后显示到屏幕。

```text
VSYNC
  ↓
Choreographer.doFrame()
  ↓
ViewRootImpl.scheduleTraversals()/performTraversals()
  ↓
View.draw()、RenderNode、HWUI RenderThread、Skia/GPU
  ↓
GraphicBuffer / BufferQueueProducer.queueBuffer()
  ↓
SurfaceFlinger 获取 Layer buffer 并提交事务
  ↓
CompositionEngine：GPU client composition 或 HWC composition
  ↓
HWC validateDisplay() / presentDisplay()
  ↓
Display Controller 扫描输出
  ↓
面板显示
```

## 1. VSYNC 触发一帧

显示硬件按刷新周期产生 VSYNC。Android 的 `Choreographer` 通过显示事件接收器得到 VSYNC 时间和 frame interval，然后进入 `doFrame()`。

源码：`frameworks/base/core/java/android/view/Choreographer.java`

```java
void doFrame(long frameTimeNanos, int frame,
        DisplayEventReceiver.VsyncEventData vsyncEventData)
```

`doFrame()` 会记录原始 frame time，更新 frame timeline，并根据当前帧是否已经安排、VSYNC 抖动和 buffer stuffing 情况决定继续执行、修正动画时间或延迟一帧。源码中的 `mFrameScheduled` 检查表示：没有待处理工作的 VSYNC 不会强行执行完整绘制。

这一阶段只是在应用线程上安排“这一帧应该做什么”，还没有把像素送到屏幕。

## 2. 状态变化触发 Traversal

当 View 内容、尺寸或可见性发生变化时，`ViewRootImpl` 会请求一次遍历。核心代码在：

`frameworks/base/core/java/android/view/ViewRootImpl.java`

```java
void scheduleTraversals() {
    if (!mTraversalScheduled) {
        mTraversalScheduled = true;
        mTraversalBarrier = ...postSyncBarrier();
        mChoreographer.postCallback(
                Choreographer.CALLBACK_TRAVERSAL,
                mTraversalRunnable, null);
        notifyRendererOfFramePending();
    }
}
```

这里做了三件事：防止同一时刻重复安排遍历；给消息队列增加同步屏障；把遍历回调交给 `Choreographer`，等待 VSYNC 时机执行。

VSYNC 到来后，`doTraversal()` 移除同步屏障并进入 `performTraversals()`：

```java
void doTraversal() {
    if (mTraversalScheduled) {
        mTraversalScheduled = false;
        ...
        performTraversals();
    }
}
```

## 3. `performTraversals()` 完成测量、布局和绘制

源码位置仍然是：

`frameworks/base/core/java/android/view/ViewRootImpl.java`

```java
private void performTraversals()
```

该函数会检查宿主 View、窗口可见性和尺寸变化，计算窗口 frame，必要时执行：

```text
measure → layout → draw
```

- `measure` 确定 View 的测量尺寸。
- `layout` 确定 View 在父容器中的位置。
- `draw` 绘制背景、文字、图片和子 View。

它还会处理 Surface 是否新建、窗口尺寸是否改变、是否需要完整重绘，以及因为同步或不可见状态跳过本帧的情况。因此，`performTraversals()` 是 Java View 树进入渲染管线的关键入口。

## 4. HWUI、RenderNode 和 GPU 生成应用 buffer

硬件加速实现位于：

`frameworks/base/libs/hwui/`

其中 `renderthread/` 保存 RenderThread 相关代码。View 的绘制操作会记录为 HWUI/RenderNode 绘制内容，再由 RenderThread 和 Skia 渲染到 GraphicBuffer。GPU 模式下，应用不会直接写物理显示内存，也不会直接调用显示控制器。

此处得到的是**应用窗口自己的图像 buffer**。它还要经过 BufferQueue 和 SurfaceFlinger，才能成为屏幕最终画面的一部分。

## 5. BufferQueue：应用生产，SurfaceFlinger 消费

相关源码：

- `frameworks/native/libs/gui/Surface.cpp`
- `frameworks/native/libs/gui/BufferQueueProducer.cpp`
- `frameworks/native/libs/gui/BufferQueueConsumer.cpp`

应用侧通过 GraphicBufferProducer 申请和提交 buffer。`BufferQueueProducer::dequeueBuffer()` 负责取得一个可写 slot：

```cpp
status_t BufferQueueProducer::dequeueBuffer(...)
```

它会检查队列是否 abandoned、producer 是否连接、是否有可用 buffer，并等待或分配 GraphicBuffer。

绘制完成后，应用调用 `BufferQueueProducer::queueBuffer()`：

```cpp
status_t BufferQueueProducer::queueBuffer(
        int slot, const QueueBufferInput& input,
        QueueBufferOutput* output)
```

`QueueBufferInput` 携带 acquire fence、时间戳、裁剪区域、缩放模式、transform、damage 和 HDR 信息。acquire fence 表示 GPU 何时完成对 buffer 的写入。

提交后，源码通过 `frameAvailableListener->onFrameAvailable(item)` 通知消费者有新帧。于是数据关系是：

```text
应用 / GraphicBufferProducer
    dequeueBuffer → GPU 写入 → queueBuffer
                                      ↓
                               onFrameAvailable
                                      ↓
                          SurfaceFlinger / Consumer
```

这里的 buffer 仍代表一个窗口或 Layer，不代表整块屏幕。

## 6. SurfaceFlinger 接收并组织 Layer

核心源码：

- `frameworks/native/services/surfaceflinger/SurfaceFlinger.h`
- `frameworks/native/services/surfaceflinger/SurfaceFlinger.cpp`
- `frameworks/native/services/surfaceflinger/Layer.cpp`
- `frameworks/native/services/surfaceflinger/FrontEnd/`

SurfaceFlinger 管理应用窗口、状态栏、导航栏、Launcher、视频 Surface 等 Layer。每个 Layer 可能有自己的 BufferQueue 和 buffer。SurfaceFlinger 在合适的 VSYNC 阶段 latch 最新可用 buffer，并把窗口事务和 Layer 状态提交到绘制状态。

事务提交入口之一是：

```cpp
void SurfaceFlinger::commitTransactions()
```

该函数清理事务标志、调节 VSYNC，并调用：

```cpp
commitTransactionsLocked(clearTransactionFlags(eTransactionMask));
```

`commitTransactionsLocked()` 处理显示事务、Layer 增删、可见区域失效和输入窗口信息更新，最后进入更具体的提交流程。它解决的是“哪些 Layer 在哪里、以什么顺序和属性参与这一帧”，不是单个应用 View 的绘制。

## 7. CompositionEngine 决定如何合成

相关目录：

`frameworks/native/services/surfaceflinger/CompositionEngine/`

主要文件包括：

- `CompositionEngine/src/Output.cpp`
- `CompositionEngine/src/RenderSurface.cpp`
- `CompositionEngine/include/compositionengine/`

CompositionEngine 根据可见区域、Layer 顺序、变换、透明度和显示设备能力，选择合成路径：

```text
GPU client composition
或
Hardware Composer / Overlay composition
或二者混合
```

如果需要 GPU client composition，`RenderSurface::dequeueBuffer()` 会为显示输出取得目标 buffer，RenderEngine 将多个 Layer 绘制到这个目标 buffer，随后 `RenderSurface::queueBuffer()` 提交合成结果。

这与应用自己的 BufferQueue 是两个不同角色：

- 应用 BufferQueue：应用生产窗口 buffer，SurfaceFlinger 消费。
- 显示输出 BufferQueue：SurfaceFlinger 生产合成结果，显示输出消费。

## 8. HWC 验证和提交显示帧

相关源码：

- `frameworks/native/services/surfaceflinger/DisplayHardware/HWC2.cpp`
- `frameworks/native/services/surfaceflinger/DisplayHardware/ComposerHal.h`
- `frameworks/native/services/surfaceflinger/DisplayHardware/HidlComposerHal.cpp`
- `frameworks/native/services/surfaceflinger/DisplayHardware/AidlComposerHal.cpp`

HWC 通常先验证合成方案，再提交显示：

```text
validateDisplay()
    ↓
确认哪些 Layer 可由 Overlay 直接显示
确认哪些 Layer 需要 GPU client composition
    ↓
presentDisplay()
    ↓
返回 present fence
```

在 `HWC2.cpp` 中，`Display::present()` 调用：

```cpp
auto intError = mComposer.presentDisplay(mId, &presentFenceFd);
*outPresentFence = sp<Fence>::make(presentFenceFd);
```

`presentFence` 表示显示设备完成或接受该帧的同步点。HIDL 和 AIDL Composer HAL 最终会把 `validateDisplay()`、`presentDisplay()` 转成对应硬件 Composer 服务调用。

## 9. Fence 保证各阶段不会互相覆盖

一帧并不是“函数调用返回就立即显示”。多个参与者需要通过 fence 协调：

```text
GPU 写应用 buffer
    → acquire fence
SurfaceFlinger 读取应用 buffer
    → release fence
显示硬件读取合成 buffer
    → present fence
```

Fence 保证消费者不会在生产者完成前读取，也保证生产者不会在显示硬件仍使用 buffer 时覆盖它。BufferQueue 中的 buffer slot、acquire fence、release fence 和 frame timestamps 共同维护这条生命周期。

## 10. 完整源码调用链

```text
frameworks/base/core/java/android/view/Choreographer.java
  Choreographer.doFrame()
        ↓
frameworks/base/core/java/android/view/ViewRootImpl.java
  scheduleTraversals()
  doTraversal()
  performTraversals()
        ↓
frameworks/base/libs/hwui/renderthread/
  RenderThread / RenderNode / Skia / GPU
        ↓
frameworks/native/libs/gui/Surface.cpp
frameworks/native/libs/gui/BufferQueueProducer.cpp
  dequeueBuffer()
  queueBuffer()
  onFrameAvailable()
        ↓
frameworks/native/services/surfaceflinger/SurfaceFlinger.cpp
  commitTransactions()
  commitTransactionsLocked()
        ↓
frameworks/native/services/surfaceflinger/CompositionEngine/
  Output / RenderSurface
        ↓
frameworks/native/services/surfaceflinger/DisplayHardware/HWC2.cpp
  validateDisplay()
  presentDisplay()
        ↓
Composer HAL → Display Controller → 面板
```

## 11. 最容易混淆的两个“绘制”

**应用绘制**是 `ViewRootImpl`、HWUI 和 GPU 把一个应用窗口画进自己的 GraphicBuffer。应用在这一阶段只知道自己的内容。

**系统合成**是 SurfaceFlinger 把所有可见窗口 Layer 组合成最终显示内容。它可能使用 GPU，也可能让 HWC Overlay 直接处理部分 Layer。

所以应用的 `draw()` 返回，并不表示像素已经出现在屏幕上。之后仍要经过 queueBuffer、SurfaceFlinger latch、合成、HWC present 和显示扫描。

## 12. 常见现象对应的源码阶段

| 现象                           | 优先检查的源码阶段                                                             |
|--------------------------------|--------------------------------------------------------------------------------|
| 点击后界面没有更新             | `Choreographer.doFrame()`、`ViewRootImpl.scheduleTraversals()`、主线程是否阻塞 |
| `performTraversals()` 很慢     | View 测量、布局、`draw()` 或窗口尺寸变化                                       |
| GPU 绘制完成但屏幕仍显示旧内容 | `BufferQueueProducer::queueBuffer()`、`onFrameAvailable()`、acquire fence      |
| 多窗口合成耗时高               | SurfaceFlinger、CompositionEngine、RenderEngine 或 HWC 合成选择                |
| 一直掉帧                       | VSYNC 时间线、主线程/RenderThread 超时、BufferQueue 堵塞或 fence 延迟          |
| 视频与 UI 叠加异常             | HWC2 Layer 类型、Overlay 能力、dataspace 和 transform                          |

Android 不同版本和厂商实现会改变具体类、线程和硬件路径，但源码体现的主原则保持一致：应用生产窗口 buffer，BufferQueue 传递它，SurfaceFlinger 管理并合成 Layer，HWC 和显示控制器负责最终提交与扫描。

##################
当前项目保留了 Android 的 View → ViewRootImpl → Canvas 结构，但绘制后半段使用的是 Java 软件绘制和 X11 窗口显示。

当前实际链路是：

View.invalidate() / requestLayout()
↓
ViewRootImpl.scheduleTraversals()
↓
Choreographer → Handler 消息
↓
ViewRootImpl.performTraversals()
↓
measure → layout → View.draw()
↓
Canvas / Graphics2D → BufferedImage
↓
XWindow.present()
↓ JNI
XPutImage() → XFlush()
↓
宿主桌面显示系统
↓
屏幕

与 Android 的主要区别如下：

| 阶段       | Android                                           | 当前项目                                    |
|------------|---------------------------------------------------|---------------------------------------------|
| 帧调度     | VSYNC 驱动 Choreographer                          | Handler 消息驱动，未接入显示 VSYNC          |
| View 遍历  | 按脏标记决定测量、布局和绘制                      | 每次 renderToImage() 都执行测量、布局、绘制 |
| 绘制实现   | 通常使用 HWUI、RenderNode、RenderThread、Skia/GPU | 使用 Graphics2D 软件绘制                    |
| 图像缓冲区 | GraphicBuffer，可在图形组件之间共享               | Java 堆中的 BufferedImage                   |
| 帧提交     | Surface、BufferQueue、事务等机制                  | JNI 把像素提交给 X11                        |
| 多窗口合成 | SurfaceFlinger 管理 Layer                         | 交给宿主 X11/桌面显示系统                   |
| 硬件提交   | HWC、显示驱动                                     | 由宿主系统完成                              |
| 同步机制   | acquire/release/present fence                     | 项目没有对应的图形 fence 管线               |

具体看代码，有四个关键点。

1. Choreographer 目前只是回调调度器。

Choreographer.java (base/com/android/view/Choreographer.java) 中，显示事件接收器和 doFrame() 调用仍被注释。实际执行的是：

case MSG_DO_SCHEDULE_CALLBACK:
    ((Runnable) msg.obj).run();
    break;

所以当前重绘随消息队列执行，没有按照屏幕刷新周期统一安排。

2. ViewRootImpl 在 UI 线程生成完整图片。

ViewRootImpl.java (base/com/android/view/ViewRootImpl.java:129) 的核心是：

mWindow.present(renderToImage(mView, mWidth, mHeight));

renderToImage() 会测量、布局，创建新的 BufferedImage，然后执行 view.draw(canvas)。绘制和提交占用当前 UI 线程，没有独立 RenderThread。

3. Canvas 使用软件绘制。

Canvas.java (base/com/android/graphics/Canvas.java) 内部使用：

graphics = image.createGraphics();

文字、图片和矩形通过 Graphics2D 画进图片，没有实现 RenderNode 绘制命令缓存或 Android HWUI 管线。

4. 项目绘制的终点是 X11。

XWindow.c (base/com/android/XWindow.c:280) 将像素转换为 XImage，再调用：

XPutImage(...);
XFlush(...);

XFlush() 只表示把 X11 请求发出去，不能证明这一帧已经显示。后续窗口合成和物理屏幕扫描由宿主系统负责。

另外，mini_binder 目前负责 Activity 启动等控制请求，没有参与图像 buffer 的传输或窗口合成。因此，当前项目主要实现了 Android 风格的 UI 前端，SurfaceFlinger、BufferQueue、HWC 所承担的显示后端尚未在项目内实现。

