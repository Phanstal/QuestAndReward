# App Store 上架前代码检查

检查日期：2026-09-09。版本 v0.55 (19)，KMP / Compose / Room v8 架构不变。

## 已完成的验收

[完整 CI #37](https://github.com/Phanstal/QuestAndReward/actions/runs/34311554513) 在提交 `b428abb` 上全部通过。

- Xcode 26.2、iPhone 16 / iOS 26.2：StoreKit 9/9、完整 UI 流程 1/1；xcresult 摘要 10 passed、0 failed、0 skipped。
- UI 覆盖首次引导、默认 Coffee 提醒、订阅购买、退款后的免费高余额锁定、任务新增编辑完成、奖励新增编辑购买使用出售、Deposit、月任务两次完成、导出、重置取消及确认、系统文件选择导入恢复、软删除、四页导航和重启后 Premium 恢复。
- KMP 测试通过；无签名设备 Release Archive 成功并核验 PrivacyInfo.xcprivacy；公开隐私政策 HTTP 检查通过。
- arm64/x86_64 Simulator 合并、版本与启动 plist 核验、安装冷启动及崩溃报告检查通过。Simulator ZIP 不是 IPA；x86_64 已编译并核验架构，实际运行验收在 arm64 上完成。
- Android API 33：Compose 20/20、Room 30/30；`test lintDebug assembleDebug` 成功，未修改单测复用缓存。Lint 0 errors、42 warnings（35 GradleDependency、6 AndroidGradlePluginVersion、1 OldTargetApi）。
- Android APK v2 签名验证、覆盖安装、冷启动通过，崩溃日志为空。

## 本轮修复原因

- 输入框中间点击不能保证光标在末尾；长按空白处也未出现 Select All。测试改为右端点击、退格、确认旧值为空，再输入并精确核验新值。
- Files 选择器中文件名点击后 picker 仍打开。测试改点文件 cell 缩略图，并要求出现 Backup imported.，再检查恢复的任务；没有改动业务恢复事务。
- lipo 输入参数顺序错误已修复；完整门禁默认执行，显式预发布模式仍与正式验收区分。

## 产物校验

| 产物 | 大小 | SHA-256 |
| --- | --- | --- |
| Android Debug APK | 28,399,816 bytes | `DA9B9F8F6EE714C9C6A164739DD1AB35E4583663982C872042927D1041300B3D` |
| #37 iOS Simulator ZIP | 40,793,654 bytes | `39B2D564F258D9DB731CE06FA67E1BAAD19A3FCCD02635959519B24DE7C324B0` |

ZIP 已下载，本地 SHA-256 与 CI 日志一致。#34 的旧预发布 ZIP SHA 为 `729335f4552de99e7453fea67c4cb4e604b0b7e040cc920d06698eac510d7cdc`，不要与本轮混淆。

## 尚未完成的 App Store 项目

- Apple Developer Team、证书、provisioning profile、签名 Archive/IPA、TestFlight 上传与真机验收。
- App Store Connect 创建 `quest_reward_monthly`，配置美国区 $1.99 月费、7 天试用、协议、税务、收款及真实沙盒测试。
- 最低 iOS 15 的运行验证及真实设备/网络中断/Apple 认证交互覆盖；本轮模拟器通过不代表所有系统版本通过。
- 最终静态链接依赖的 required-reason API 清点和隐私报告审核。现有 manifest 声明自有 UserDefaults CA92.1、无跟踪；plist 格式验证不等于完整隐私审核。
- 所有者确认隐私披露、支持联系方式、商店截图与文案等上架资料。

现阶段可以进行本地模拟器测试和开发者账号/商店配置准备，但不能宣称已经可以直接提交 App Store 上线。
