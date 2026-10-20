# MQChat

基于本仓库 Java / X11 框架的 MQTT 聊天应用，入口 `com.packages.mqchat.MainActivity`。
无需账户、独立聊天后端或额外 JAR，使用 EMQX 免费公共服务器。

## 开始聊天

1. 重启框架，从桌面打开 **MQChat**。
2. 填写昵称；将默认 Topic 改为你和对方约定的完整频道名，例如 `mqchat/room/your-room-2026`。
3. 双方填写**完全相同、区分大小写**的 Topic，并点击“连接频道”。
4. 状态变为在线后发送消息。支持同一频道内的多个客户端，昵称相同也能区分发送者。

默认服务器：`broker.emqx.io:8883`，TLS、MQTT 3.1.1、匿名连接。
服务器信息来源：[EMQX 官方公共 MQTT 服务](https://www.emqx.com/zh/mqtt/public-mqtt5-broker)。
公共频道可被其他订阅者访问；TLS 保护传输，不提供频道访问权限或端到端加密。

- Enter：发送；Shift+Enter：换行。
- Tab：切换输入框；Ctrl+A：全选；方向键 / Home / End：移动光标。
- 支持中文、emoji，以及按完整 Unicode 字符删除。
- 聊天记录可拖动滚动，或使用“较早”“最新”、Page Up / Page Down。
- 连接期间昵称和 Topic 锁定。先断开，再修改并重新连接。
- 网络中断后自动按 1–20 秒间隔重连、重新订阅；窗口关闭后释放连接。

## 消息与范围

- 发布和订阅使用同一个精确 Topic，不接受 `+`、`#` 和共享订阅。
- 发布 QoS 1、retain=false，订阅 QoS 1；启用 MQTT clean session。
- 自己发出的消息先显示“发送中”，收到服务器 PUBACK 后显示“已发布”。这表示服务器确认接收，**不是对方送达或已读回执**。
- 收到本客户端的消息回环和重复消息 ID 时，不重复显示气泡。
- 断网时不接收新的发送任务，输入保留；已提交但未获得确认的消息标记“未确认”，不自动重发，以避免意外重复。
- 本次会话最多保留 300 条消息，重启不保存；切换 Topic 清空原频道消息。没有离线消息补发和历史同步。
- 单条消息最多 2000 个 Unicode 字符，昵称最多 24 个；发送队列及未确认消息总计最多 64 条。
- 拒绝超限、非法 UTF-8 或损坏的应用消息，忽略 retained 消息；接收事件队列有容量限制。

发送数据为 UTF-8、换行分隔的版本化信封：

```text
MQCHAT/1
<message UUID>
<sender UUID>
<Unix timestamp in milliseconds>
<Base64(UTF-8 nickname)>
<Base64(UTF-8 message body)>
```

也能接收 MQTTX 等客户端发布的普通 UTF-8 文本，显示为“MQTT 用户”。
身份字段用于界面区分，不进行身份认证。

## 构建

仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File packages/apps/build.ps1 mqchat
```

Linux：

```sh
bash packages/apps/build.sh mqchat
```

产物：`out/system/app/mqchat/mqchat.apk`，桌面自动发现应用，无需修改 Launcher。
本仓库 APK 是 Java 类和资源的归档，供自有框架运行；不是手机 Android 的 DEX APK。
原生窗口依赖项目原有的 Linux / X11 环境，无界面测试可在 Windows 上运行。

## 配置服务器

默认使用 EMQX TLS。可通过应用 JVM 属性切换到自建或测试服务器：

```text
-Dframework.mqchat.host=broker.emqx.io
-Dframework.mqchat.port=8883
-Dframework.mqchat.tls=true
```

TLS 使用 JVM 默认信任库并验证服务器证书及主机名。普通 TCP 测试可设置 `tls=false`、`port=1883`。

## 实现

- `ChatView`：输入、连接控制、消息气泡、滚动和窗口生命周期。
- `MqttClient`：独立网络线程、TLS、CONNECT / SUBSCRIBE / PUBLISH / PUBACK、心跳、超时和重连。
- `ChatMessage`：消息封装、验证、发送者区分和普通 MQTT 文本兼容。

网络不在 UI 线程执行，回调投递到框架 Handler；切换频道、手动断开后，过期连接回调不会修改当前界面。
实现依据：[OASIS MQTT 3.1.1 规范](https://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html)。

## 测试

先构建应用，再执行以下 PowerShell 命令：

```powershell
javac -encoding UTF-8 -cp 'out/framework/classes;libs/*' -d out/mqchat-tests packages/apps/mqchat/src/com/packages/mqchat/MqttClient.java packages/apps/mqchat/src/com/packages/mqchat/ChatMessage.java tests/com/packages/mqchat/MqttClientTest.java tests/com/android/app/MqchatTest.java
java -cp 'out/mqchat-tests;out/framework/classes;libs/*' com.packages.mqchat.MqttClientTest
java '-Djava.awt.headless=true' -cp 'out/mqchat-tests;out/framework/classes;libs/*' com.android.app.MqchatTest
```

31 项协议与模型检查、28 项实际 APK / UI 检查通过。
测试使用独立编码的本地 broker，验证真实 socket 收发、topic 隔离、PUBACK、断线重连、Unicode、重复消息、输入、窗口缩放和关闭。

可选公共 EMQX 集成测试，使用随机 UUID Topic 和合成测试文本：

```powershell
java -cp 'out/mqchat-tests;out/framework/classes;libs/*' com.packages.mqchat.MqttClientTest public
```

9 项真实 EMQX TLS 连接与双向 Unicode 收发检查通过。
预览：`out/mqchat-preview.png`、`out/mqchat-narrow-preview.png`、`out/mqchat-wide-preview.png`。

## 字体与中文显示

APK 内置 Noto Sans CJK SC 和 Noto Emoji 字体，不依赖运行主机安装中文字库。
界面标签、输入框、光标测量及消息气泡使用同一套字体回退与测量，避免中文缺字、表情方框和光标错位。
常用表情以单色显示；未涵盖所有新版 emoji 组合。消息传输继续使用严格 UTF-8。
字体来源及许可证保存在 res/fonts/README.md、OFL-CJK.txt 和 OFL-Emoji.txt。

字体回归检查：

    javac -encoding UTF-8 -cp 'out/framework/classes;libs/*' -d out/mqchat-font-tests tests/com/packages/mqchat/ChatTextTest.java
    java '-Djava.awt.headless=true' -cp 'out/mqchat-font-tests;out/framework/classes;libs/*' com.packages.mqchat.ChatTextTest
