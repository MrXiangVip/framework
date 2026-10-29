# Browser / Atlas

这是当前 `com.android.*` 桌面框架的单标签浏览器，入口为
`com.packages.browser.MainActivity`，Launcher 根据 Manifest 自动发现。

## 已实现

- 地址栏、域名自动补全、关键词搜索、首页。
- HTTP/HTTPS 网页、真实 Chromium HTML/CSS/JavaScript 渲染。
- 前进、后退、刷新、停止加载、加载错误提示。
- 键盘、XIM 提交的中文和表情、鼠标点击/拖动/悬停、右键事件、滚轮。
- 窄窗口布局、网页尺寸同步、HTML 下拉框离屏图层合成。
- `Ctrl+L` 编辑地址，`Ctrl+A` 全选地址，`Ctrl+R` / `F5` 刷新。
- 进程异常提示、启动超时检测、窗口分离后关闭宿主、父进程管道关闭后释放 CEF。

`target="_blank"` 链接在当前标签打开。JCEF 在离屏模式中会提前取消弹窗，
因此宿主为文档安装点击/表单监听，把 `_blank` 改为 `_self`；脚本弹窗不开放。
多标签、下载管理、书签、文件上传、权限申请界面和完整的右键菜单尚未实现。
当前每个窗口使用独立会话目录，不跨窗口共享 Cookie/登录状态。

## 架构

```text
BrowserView（框架 UI、地址编辑、输入、BufferedImage 绘制）
    -> BrowserController（地址/搜索策略）
    -> BrowserEngine（宿主生命周期、输入队列、状态与像素接收）
    -> 私有 stdin/stdout 二进制 IPC
    -> CefHost（独立 Java 17 JVM，CEF/JCEF 原生库、网页执行）
    -> FrameworkOsrBrowser（无独立浏览器窗口的 BGRA 离屏画面）
```

本项目不是 Android 系统，不能直接接入 `android.webkit.WebView`。
APK 只依赖框架与 Java 标准库；JCEF 和 CEF 保留在独立宿主进程中，
不污染框架 Java 8 的类路径，也不使用 AWT/Swing 创建第二个浏览器窗口。
宿主使用 AWT 事件线程以及事件对象与 JCEF 对接。

协议带长度和尺寸检查；命令队列有界，画面只保留最新帧。
绘制与原生缓冲区通过拷贝隔离，后台接收线程不会直接操作 View。
Unicode 提交通过 Chromium `Input.insertText` 完成，避免 Linux JCEF 的 CHAR
事件把 UTF-16 代理对拆坏。没有开启远程调试端口。

## Linux / WSL 构建运行

当前固定运行时支持 **Linux x86_64**。需要项目原有 Java 8/JNI/X11 构建环境，
以及完整 JDK/JRE 17（包含 `libawt_xawt.so`）、Python 3.8+。
宿主也需要 GTK 3、NSS、GBM、ALSA 等 Chromium 系统库；可用
`ldd out/system/lib/browser/bin/lib/linux64/libcef.so` 检查缺失依赖。
已在 Ubuntu 24.04 / WSL、Java 8 框架 + Java 17 宿主上验证。

安装 Java 17 后，保持项目默认 `java`/`javac` 指向 Java 8；浏览器安装脚本直接使用
`/usr/lib/jvm/java-17-openjdk-amd64/bin/`，无需切换框架 Java 版本。

总构建脚本 `build.sh` 和 `packages/apps/build.sh browser` 现在会自动准备浏览器运行时；完整且匹配当前源码的运行时会直接复用。

在仓库根目录执行：

```bash
# 可按发行版安装；必须包含完整的图形 JRE，而非只有 headless JRE。
sudo apt install openjdk-17-jdk openjdk-17-jre

# 首次下载并安装 CEF，编译独立宿主和浏览器 APK。
bash packages/apps/browser/build.sh

# 编译框架/JNI/所有 APK，启动桌面，在 Launcher 中点击 Browser。
bash build.sh
```

运行时首次下载约 151 MiB，展开约 364 MiB。版本固定为
`jcefmaven/jcefbuild` 的 `1.0.71`，下载分块缓存并验证固定 SHA-256。
再次构建使用缓存，无需联网。应用启动不下载引擎。

已有离线压缩包时：

```bash
bash packages/apps/browser/setup.sh --archive /path/to/linux-amd64.tar.gz
bash packages/apps/build.sh browser
```

产物：

```text
out/system/app/browser/browser.apk
out/system/lib/browser/host.jar
out/system/lib/browser/bin/jcef.jar
out/system/lib/browser/bin/lib/linux64/...
out/system/lib/browser/java.path
out/system/lib/browser/runtime.json
out/system/lib/browser/licenses/...
```

部署需要同时保留 APK 和运行时目录。使用独立 JDK 路径时，构建前设置
`BROWSER_JAVA_HOME=/path/to/full-jdk-17`；移动到另一台机器后重新执行 setup，
或更新 `java.path`。运行时位置可由 `FRAMEWORK_BROWSER_RUNTIME` 环境变量或
`-Dframework.browser.runtime=...` 指定；宿主 Java 可由
`-Dframework.browser.java=/path/to/java` 覆盖。

Windows PowerShell 可以运行 `packages/apps/browser/build.ps1` 打包 Java UI，
Chromium 宿主和完整桌面仍在 Linux/WSL 中运行。

## 验证

```bash
# 需要 Xvfb；使用隔离虚拟显示和本地 HTTP 测试站点，不操作现有桌面。
python3 tests/browser_cef_smoke.py
python3 tests/browser_app_smoke.py
```

第一组验证真实 Chromium 的 JavaScript、BGRA、鼠标、Unicode、导航、滚动、
缩放、网络失败和正常退出。第二组加载实际 APK，在 Java 8 框架的原生 X11
窗口中验证绘制、点击、修饰键、右键、滚轮和窗口生命周期。

纯 Java 测试为 `tests/com/packages/browser/BrowserCoreTest.java` 和
`tests/com/android/app/BrowserAppTest.java`；后者会生成 `out/browser-preview.png`。
集成测试生成 `out/browser-webpage-preview.png` 和 `out/browser-app-integration.log`。

## 日志与会话

会话目录默认在 `~/.local/share/framework-browser/session-*`，包含 `host.log`、
`chromium.log` 和该会话缓存。可通过 `-Dframework.browser.profile=...` 改变根目录。
为保留故障信息，关闭窗口后不自动删除会话目录；不需要时可自行清理。

此浏览器没有修改 Weston 或系统剪贴板配置。若 WSLg 显示服务自身退出，
仍需修复 WSLg/Weston；应用内进程隔离不能替代显示服务修复。

## 第三方许可

CEF/JCEF 来源于 <https://github.com/jcefmaven/jcefbuild/releases/tag/1.0.71>，
归档中的许可文件随运行时复制到 `licenses/`。发布包含 Chromium 的产物时应一并
分发相关许可；版本和下载摘要记录在 `runtime.json`。升级时需同时更新固定版本、
大小、摘要并重新运行集成测试。

## 中文字体

浏览器使用 framework 统一提供的 Noto Sans CJK SC 和 Noto Emoji，字体和授权位于 base/res/fonts，无需系统预装中文字体。标题栏与地址栏通过 FontManager 使用公共字体；Chromium 的 fonts.conf 引用同一份 framework 字体目录并保留系统字体。网页编码仍由 HTTP 响应头与网页声明决定，不强制改成 UTF-8。重新构建后关闭并重开浏览器即可加载字体。
