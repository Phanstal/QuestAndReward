# App Store 上架前代码检查

检查日期：2026-09-08。基线：v0.55 (19)，验证分支 `codex/v055-validation`。
本报告是代码静态检查与测试证据清单，不是 App Store 审核通过声明。

## 已核对的配置

- iOS Release 使用 `com.phanstal.questandreward`，版本 0.55 (19)，最低 iOS 15.0，仅 iPhone；Archive Action 为 Release。
- Swift StoreKit 2 位于平台边界，共享业务继续采用 Compose/KMP/Room v8。
- 订阅产品 `quest_reward_monthly`；验证交易签名、产品、过期、撤销及升级状态；启动、前台和交易更新重新核验，恢复调用 AppStore.sync。
- StoreKit 测试配置在测试 target 资源和 Debug Run scheme 中；不能替代 App Store Connect 产品配置。
- Info.plist 配置 Compose 所需帧率开关、Documents 文件共享；未声明摄像头、定位等权限。
- System.Drawing 实际读取图标为 1024×1024、Format32bppArgb，逐像素检查发现非 255 alpha；需要消除透明像素及 alpha 通道，再进行资产编译和 Archive 校验。

## 尚未完成的代码和验收项

| 项目 | 代码证据 / 实际缺口 | 下一步 |
| --- | --- | --- |
| 完整 UI 验收 | 上轮 34141752940 原生 StoreKit 8/8 通过；UI 在键盘 Next 的 XCTest 操作失败，后续场景未运行完 | 5fcd2d3 修复已推送，运行 34181433617 验证；不得省略断言换取通过 |
| 试用资格 | FamilyQuestScreen 付费墙固定显示 7-day free trial 和 Start Free Trial；SubscriptionManager 未读取 introductory offer eligibility | 以真实资格显示试用或普通订阅，补充不符合试用资格测试 |
| 订阅说明和法律链接 | 付费墙已有本地化价格、恢复购买；没有隐私政策、使用条款入口，也没有完整自动续订说明 | 确认真实公开隐私政策地址及适用条款后接入；不能填写占位网址冒充完成 |
| Privacy manifest | 仓库未发现 PrivacyInfo.xcprivacy；iosMain 使用 NSUserDefaults | 清点应用及最终依赖包 required-reason API，补充适用声明并验证最终 Archive |
| 导入交互 | IosDocumentsBackupActions 只读取 Documents 中字典序最新 quest-backup-*.json，没有文件选择器 | 当前测试只能验证此流程；不能称为任意文件选择导入已验收。与 Android 交互差异需在最终验收中明确 |
| 订阅响应时限 | Product 请求使用 task-group 15 秒计时；取消仍依赖 StoreKit 子任务配合，entitlement/restore 无独立有界等待 | 真机/沙盒覆盖断网、慢响应和恢复前台；不把计时器当作已证明的硬超时 |
| 系统版本覆盖 | 当前 CI 为 iPhone 16 / iOS 18.5，部署最低版本为 15 | 当前系统及最低支持版本兼容性尚未完整验收 |
| 正式构建 | 工作流仅构建 Debug Simulator，尚无 Release device Archive 门禁 | 增加/执行 Release device 构建检查，账号就绪后签名 Archive、验证及 TestFlight |
| 上架图标 | 实际 PNG 含透明像素 | 保留图案并铺实色背景，输出无 alpha 图标后验证正式包 |

## 需要所有者提供的资料

Apple Developer 会员及 Team；App Store Connect 同 ID 月订阅、美国区 $1.99 价格和 7 天试用；协议、税务、收款；CI 签名与上传凭证（仅存 Secrets）；真实隐私政策和支持地址、商店及隐私披露资料；TestFlight 真机测试人员。

这些资料不阻挡模拟器验收。当前没有签名 IPA，也不具备“仅填账号即可上线”的证据。
