# 同花顺自选 · 联网看盘与模拟交易

参考 img.png 实现的 Java2D / X11 应用，保留红白自选行情布局。内置中文字体，无第三方运行库。

## 联网行情

默认联网，每 5 秒通过 HTTPS 批量请求腾讯公开报价，后台单线程处理，通过 UI Handler 应用完整报价批次。点击指数下面的刷新状态，或按 F5，可手动刷新。窗口移除后停止请求；连接 / 读取均设置 5 秒超时，重复刷新合并为一个在途请求。

- 已映射 15 个品种：上证指数、恒生指数、纳斯达克、宁德时代、贵州茅台、招商银行、比亚迪、中国平安、十年国债 ETF、长鑫科技、理文化工、工商银行港股、中国中铁港股、摩根大通、高通。
- 列表与详情显示来源的报价时间。沪深 / 香港按当地时间，美股和纳斯达克按美东时间；刷新栏显示本机接收时间，两者含义不同。
- 公开报价可能延时，休市时可能保持上次交易日数据；5 秒为请求频率，不代表交易所推送频率。此接口并非本项目拥有的稳定服务或正式商业授权数据接口，接口变化可能导致刷新失败。
- 未映射的期指、金龙指数、美国国债等显示“未接入”与 --，不会把旧示例值充当联网数据。失败时保留最近有效报价并显示失败状态，后续自动重试。
- 五档仅展示来源实际返回的买卖价格和数量，空档显示 --，数量保持来源单位。指数或部分海外品种不一定提供五档。
- **分时、日 / 周 / 月 K 线与板块卡片仍是合成示意，已在界面标明，不是联网的真实走势或历史行情。**

来源页面：https://gu.qq.com/ 。报价请求与字段解析集中于 QuoteClient.java，方便替换行情供应商。解析只读取文本字段，不执行返回的 JavaScript；以响应变量与证券标识双重校验映射。

## 操作与模拟交易

自选增删、名称 / 代码搜索、排序、行情筛选、滚轮 / 拖动滚动、个股详情、操作指南和资产页。Ctrl+F 搜索，回车打开首个结果，Esc 关闭弹层 / 返回。

模拟账户初始资金 100 万，内置可交易沪深 A 股 100 股整数倍、零手续费、按确认报价模拟成交。不连接券商，不提交真实委托。联网模式收到有效报价后才能模拟成交；60 秒内未成功接收该证券报价则拒绝成交。确认期间报价变化时，先更新金额并要求再次确认。资产及持仓盈亏随报价更新；缺少持仓报价时显示估值待更新。

账户与自选文件：~/.local/share/framework-tonghuashun/account.properties。金额使用整数分，临时文件替换保存；失败回滚，不静默覆盖损坏账户。

配置 JVM 属性：

- framework.tonghuashun.data=/path/account.properties：指定账户文件。
- framework.tonghuashun.online=false：显式离线示例模式，仅用于演示 / 确定性测试；默认 true。

构建：bash packages/apps/tonghuashun/build.sh，或 Windows powershell -File packages/apps/tonghuashun/build.ps1。根目录通用构建自动发现应用；产物 out/system/app/tonghuashun/tonghuashun.apk，Launcher 自动发现。

Linux 回归：python3 tests/tonghuashun_smoke.py。额外验证真实网络和原生窗口中的自动刷新 / 停止：python3 tests/tonghuashun_smoke.py --live。联网测试不假设休市时价格必须变化。

MarketStoreTest 验证账户持久化；QuoteTest 验证字段、异常响应、时间 / 标识校验、旧报价、确认价格与刷新合并；TonghuashunTest 验证实际 APK；NativeTest / LiveTest 验证 X11 事件和真实行情更新链路。

字体：使用 framework 共享的 Noto Sans CJK SC 与 Emoji，SIL OFL 1.1 授权见 base/res/fonts/OFL-*.txt，APK 不再单独打包字体。图标由 tools/GenerateIcon.java 绘制，原参考图片不作为界面打包。