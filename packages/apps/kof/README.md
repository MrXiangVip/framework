# KOF · The King of Fighters

本项目 Java / X11 框架上的单人街机格斗游戏，入口为 `com.packages.kof.MainActivity`。

## 游戏流程

- 启动进入选角，点击头像、按 A / D、左右方向键或数字 1–0，选择 10 个角色之一。
- 点击 CONFIRM 或按 Enter 开始；电脑自动选择不同角色。
- 每场一局，限时 60 秒。血量归零立即结算，超时比较剩余血量，相同则平局。
- 结算显示胜负，3.5 秒后自动返回选角；也可点击按钮或按 Enter 提前返回。
- 新对战重置血量、能量、位置和倒计时。角色有不同配色、移动速度和伤害加成。

| 操作 | 按键 |
| --- | --- |
| 移动 | A / D 或左右方向键，支持按住 |
| 跳跃 | W / 空格 / 上方向键 |
| 拳击 / 踢腿 | J / K |
| 防御 | 按住 L |
| 必杀技 | U，消耗 40 能量 |
| 暂停 / 继续 | P |
| 返回选角 | Esc |
| 音效开关 | M |

屏幕按钮可用鼠标操作。框架只有一个鼠标指针，组合操作推荐使用键盘。
失去窗口焦点会自动暂停并释放按键。窗口缩放时保持横屏比例并同步适配点击坐标。

## 美术与实现

`res/drawable/choice.jpeg` 用作选角背景和头像来源；`battle.jpeg` 的机场夜景区域用于战场。
两张参考图片是静态插画，没有角色动作序列，因此可操作的角色采用 Java2D 绘制的简化动画人物，
提供待机、移动、出拳、踢腿、跳跃、防御、必杀、受击和倒地效果，并非原版拳皇精灵素材。

`GameSession` 管理选角、战斗、结算循环；`FightEngine` 采用固定 60 Hz 步长模拟，
攻击分前摇、命中期和收招，每次攻击最多命中一次。`KofView` 通过 UI 消息更新、绘制和分发输入。
格斗与合成音效代码基于本仓库 `quanhuang` 应用，独立打包，不依赖另一个 APK。
没有音频设备时仍可正常游戏；使用 `-Dframework.kof.sound=false` 可默认静音。

## 构建

在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File packages/apps/build.ps1 kof
```

Linux：

```sh
bash packages/apps/build.sh kof
```

产物：`out/system/app/kof/kof.apk`。重启框架后，桌面自动发现 **The King of Fighters** 应用。
原生 X11 窗口需要项目原有 Linux 运行环境；APK 加载、绘图和玩法检查可在 Windows 无界面运行。

## 验证

先构建游戏，再在 PowerShell 中执行：

```powershell
javac -encoding UTF-8 -cp 'out/framework/classes;libs/*' -d out/kof-tests tests/com/android/app/KofTest.java
java '-Djava.awt.headless=true' -cp 'out/kof-tests;out/framework/classes;libs/*' com.android.app.KofTest
```

测试直接加载实际 APK，覆盖桌面注册、资源、选角、输入、战斗、暂停、KO、超时、平局、自动返回和窗口尺寸适配。
渲染预览生成在 `out/kof-*-preview.png`。