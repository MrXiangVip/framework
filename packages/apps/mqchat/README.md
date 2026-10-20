# MQChat：基于 MQTT 的 UUID 私聊

本应用运行在此仓库的桌面 Java 框架中，支持 Windows / WSL；APK 是包含 Java 类、资源和原生 SQLite 库的应用包。

## 使用

1. 启动 MQChat，点击右上角「连接」。默认使用 `broker.emqx.io:8883`，开启 TLS 与服务器证书校验。
2. 底部点击「我的」查看圆形头像、昵称与 13 位展示码。点击「编辑资料」修改昵称、查看完整 UUID；头像栏输入图片完整路径，点击「保存资料」。PNG / JPEG 会自动裁剪压缩为 64×64 图片。
3. 双方至少连接一次以发布展示码目录。在「通讯录」点击右上方「添加好友」，输入对方展示码或完整 UUID，再点击「发送好友申请」。申请会保留在服务器，对方上线后可收到。
4. 对方打开申请，选择同意或拒绝。同意后双方保存好友，自动订阅好友状态与共同私聊 Topic。点击好友进入对话。
5. Enter 发送；Shift + Enter 换行。离线消息先存入 SQLite，重连后每 10 秒重试；收到对方回执后显示「已送达」。MQTT PUBACK 只显示「已发送」，不当作对方已经收到。

通讯录支持按昵称、展示码或 UUID 搜索，点击联系人进入私聊或处理好友申请。顶部「连接 / 断开」控制 MQTT 连接，底部「通讯录 / 我的」切换页面。当前 UUID 账号没有密码登录，参考图中的第二个资料菜单用作「编辑资料」。

昵称、头像、UUID 和密钥重启后保持不变。联系人包括「待对方同意」「新好友申请」「已成为好友」「已拒绝」状态。

## 数据和换设备

默认数据库位置：`~/.mqchat/mqchat.sqlite`。可用 JVM 属性 `framework.mqchat.dataDir` 为不同账号指定独立目录。

在「我的 → 备份迁移」填写 `.sqlite` 文件路径，点击「导出备份」。新设备打开相同页面，填写备份路径，点击「导入备份」，再点击「确认恢复」。

备份包含自己的 UUID、身份密钥、昵称头像、好友和申请、聊天记录、待发送消息。恢复会断开网络并替换当前账号资料；校验完成后在同一 SQLite 事务中恢复，失败时保留原数据。恢复后点击连接即可重新订阅好友并继续发送待送达消息。换设备时停止旧设备登录，同一身份使用固定 MQTT Client ID，不能同时运行两个副本。

备份通过 SQLite `VACUUM INTO` 生成一致快照，临时文件完成后再原子替换目标文件。导出文件包含私钥，应自行保管；Linux 上数据库和导出备份限制为当前用户读写。导入支持最多 128 MB、5,000 个联系人、20,000 条消息与 5,000 条待发送记录。

WSL 中请填写 Linux 路径，例如 `/home/用户名/avatar.png`、`/mnt/c/Users/用户名/backup.sqlite`。

## Topic 与协议

命名空间：`mqchat/p2p/v2/`。所有发布均使用 QoS 1。

| Topic 后缀 | 用途 | Retain |
| --- | --- | --- |
| `directory/{展示码}` | 展示码到 UUID、昵称、头像、公钥的资料映射 | 是 |
| `users/{UUID}/status` | 在线 / 离线、心跳与当前连接会话 | 是 |
| `users/{接收方UUID}/inbox/{发送方UUID}` | 好友申请、同意、拒绝；按发送方分别保留，避免多人申请互相覆盖 | 是 |
| `chat/{SHA256(排序后的双方UUID)}` | 双方共同订阅的私聊消息与送达回执 | 否 |

展示码由 UUID 的 SHA-256 前 65 位编码为 13 位 Crockford Base32，排除易混淆字母。收到目录信息时重新计算展示码，完整 UUID 寻址同时校验 UUID。65 位摘要并非数学上无碰撞的全局注册服务；如果已有好友的公钥不匹配，会拒绝替换资料。

上线发布 retained 状态，10 秒更新一次；连接声明 retained 离线遗嘱，异常退出也能通知好友。45 秒无新心跳视为离线。旧连接的遗嘱按连接会话区分，避免覆盖新连接的在线状态。每次重连重新订阅本人的申请收件箱、好友状态和已接受好友的私聊 Topic，并重新发布资料与未完成的申请/接受结果。

消息使用带版本的二进制信封，包含消息 UUID、双方 UUID、请求关联 ID、时间、资料和 RSA 签名。私聊正文用随机 AES-128-GCM 密钥加密，密钥通过接收方 RSA-2048 OAEP-SHA256 公钥封装。接收方验证已保存的好友公钥、目标 UUID 和 Topic；聊天按消息 UUID 在 SQLite 去重。第三方即使订阅相同公共 Topic，也不能用自己的私钥解密正文。

公共服务器上的展示码、个人资料和状态仍是公开元数据。首次添加使用目录提供的公钥，后续固定该公钥；这不是中心化实名认证，也没有实现前向保密。服务器 retained 数据不代替本地 SQLite 备份。

## 实现

- `PeerProtocol.java`：身份、展示码、Topic 派生、签名和加密。
- `PeerMqtt.java`：多 Topic / 单层通配符订阅、QoS 1、遗嘱、重连、TLS。
- `PeerStore.java`：真实 SQLite 表、好友公钥固定、消息去重、持久发送队列与备份恢复。
- `PeerSession.java`：申请 / 同意 / 拒绝、资料发现、在线状态、心跳、私聊送达状态机。
- `PeerView.java`：好友与申请列表、个人资料、头像、私聊、备份迁移。
- `ChatText.java` 与 `res/fonts`：内置 CJK / Emoji 字体，保持中文输入和显示。

旧的公共频道界面保留为 `legacy_channel.xml` 和 `ChatView.java`，新应用入口使用 `PeerView`。

## 构建

```powershell
powershell -ExecutionPolicy Bypass -File packages/apps/build.ps1 mqchat
```

```bash
bash packages/apps/build.sh mqchat
# 或重新构建并启动整个框架
bash build.sh
```

产物：`out/system/app/mqchat/mqchat.apk`。应用的 `libs/*.jar` 会解包到 APK，包含 Windows / Linux SQLite 原生库；运行时无需额外安装 sqlite3 或配置 JDBC classpath。依赖版本、来源与校验值见 `libs/README.md`。

WSL 中文输入需要 Fcitx5：`sudo apt install fcitx5 fcitx5-chinese-addons`。框架启动脚本已自动设置 XIM 并启动 X11 模式输入法；点击输入框按 Ctrl + Space 切换拼音。

## 验证

模型集成测试使用三个临时账号和真实 MQTT Broker，覆盖身份持久化、双向好友申请、离线申请、昵称头像、状态遗嘱、加密、双向私聊、送达回执、离线重试、去重、密钥固定、备份及带待发送消息的设备迁移。

```powershell
javac -encoding UTF-8 -source 8 -target 8 -cp 'out/system/app/mqchat/mqchat.apk;out/framework/classes;libs/*' -d out/mqchat-peer-tests tests/com/packages/mqchat/PeerSessionTest.java
# 本机 MQTT Broker 默认 127.0.0.1:18887，可设置 -Dtest.broker=... -Dtest.port=...
java '-Djava.awt.headless=true' -cp 'out/mqchat-peer-tests;out/system/app/mqchat/mqchat.apk;out/framework/classes;libs/*' com.packages.mqchat.PeerSessionTest
# 在默认 EMQX TLS 公共服务器测试随机临时账号
java '-Djava.awt.headless=true' -cp 'out/mqchat-peer-tests;out/system/app/mqchat/mqchat.apk;out/framework/classes;libs/*' com.packages.mqchat.PeerSessionTest public

javac -encoding UTF-8 -source 8 -target 8 -cp 'out/framework/classes;libs/*' -d out/mqchat-peer-ui-tests tests/com/android/app/MqchatPeerViewTest.java
java '-Djava.awt.headless=true' -cp 'out/mqchat-peer-ui-tests;out/framework/classes;libs/*' com.android.app.MqchatPeerViewTest
```

Linux / WSL 将 classpath 的分隔符 `;` 改为 `:`。界面测试从真实 APK 加载全部 SQLite 依赖，验证 12 项操作并生成好友、资料、备份、私聊的 600 / 360 像素宽预览。