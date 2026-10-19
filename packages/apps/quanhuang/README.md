# 拳皇 · Arcade Duel

基于本项目 Java 框架和 X11 窗口的单机格斗小游戏，使用原创角色 SPARK、IRON 和 Java2D 绘制素材。

- 与电脑对战，三局两胜；每局 60 秒，血量归零或倒计时结束结算。
- 拳击、踢腿、跳跃、防御和能量必杀技；命中与格挡积攒能量，必杀技消耗 40 点。
- 攻击有前摇、有效判定和收招时间，同一次攻击只造成一次伤害。
- 每个回合重置血量和能量；平局不计胜场，继续下一回合。
- 暂停、重新开始、窗口缩放；失去窗口焦点自动暂停。

| 操作 | 按键 |
| --- | --- |
| 左右移动 | A / D 或左右方向键，支持按住 |
| 跳跃 | W、空格或上方向键 |
| 拳击 / 踢腿 | J / K |
| 防御 | 按住 L |
| 能量必杀技 | U |
| 暂停 / 继续 | P 或 Esc |
| 重新开始 | R；比赛结束也可按 Enter |

屏幕按钮支持鼠标操作。键盘可同时移动和攻击；鼠标采用框架的单指针输入。

在项目根目录运行 `bash build.sh`，游戏会自动打包，并在 Launcher 的应用分页中显示“拳皇”。
单独打包：

```sh
bash packages/apps/quanhuang/build.sh
```

Windows：`powershell -File packages/apps/quanhuang/build.ps1`。
产物：`out/system/app/quanhuang/quanhuang.apk`。

本游戏独立实现格斗规则，不依赖 ROM、模拟器或外部游戏素材。目前为单人对战，没有联网或角色选择。
固定步长模拟与绘制分离；游戏线程仅投递消息，状态和画面在应用 UI 线程更新。
原生输入桥补充 `KeyEvent.UP`，支持正确结束按键长按。

测试：`tests/com/packages/quanhuang/FightEngineTest.java` 验证战斗规则；
`tests/com/android/app/QuanhuangTest.java` 验证实际 APK 的界面、键鼠控制和桌面注册；
`tests/quanhuang_x11_smoke.py` 验证原生窗口中的动画、移动、松键、攻击与暂停。

## Sound effects

Original synthesized arcade effects accompany punches, kicks, hits, blocked hits,
special moves, jumping/landing, round start, FIGHT, KO, timeout, draws, and match
victory/defeat. The computer uses the same combat events. Rejected attacks and
repeated rendered frames do not replay effects.

Press **M** or click **SFX ON / SFX OFF** in the top toolbar to mute/unmute.
Pause and focus loss discard queued/playing effects; restarting clears the old
round's sounds. Closing the window releases the audio device. Playback and mixing
run on a separate daemon thread with up to eight simultaneous effects.

Java Sound plays mono 22050 Hz / 16-bit PCM directly; no FFmpeg or external audio
assets are required. If the system has no audio output, gameplay continues and
the toolbar shows **NO AUDIO**. To start muted, add
`-Dframework.quanhuang.sound=false` to the application's JVM options.

Audio regression: "FightAudioTest" verifies combat cues, PCM, mute/pause/restart,
partial writes, device failure and asynchronous closure. The optional Linux
"python3 tests/quanhuang_audio_smoke.py" check records real output through a
PulseAudio monitor and requires FFmpeg for the test recorder only.
