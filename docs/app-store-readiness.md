# App Store 上架前代码检查

检查日期：2026-09-08。基线：v0.55 (19)，验证分支 `codex/v055-validation`。
本报告是代码静态检查与测试证据清单，不是 App Store 审核通过声明。

## 已核对的配置

- iOS Release 使用 `com.phanstal.questandreward`，版本 0.55 (19)，最低 iOS 15.0，仅 iPhone；Archive Action 为 Release。
- Swift StoreKit 2 位于平台边界，共享业务继续采用 Compose/KMP/Room v8。
- 订阅产品 `quest_reward_monthly`；验证交易签名、产品、过期、撤销及升级状态；启动、前台和交易更新重新核验，恢复调用 AppStore.sync。
- StoreKit 测试配置在测试 target 资源和 Debug Run scheme 中；不能替代 App Store Connect 产品配置。
- Info.plist 配置 Compose 所需帧率开关、Documents 文件共享；未声明摄像头、定位等权限。
- 图标已铺底并实际验证为 1024×1024、Format24bppRgb，无 alpha 通道；正式 Archive 校验仍待完成。

## 尚未完成的代码和验收项

| 项目 | 代码证据 / 实际缺口 | 下一步 |
| --- | --- | --- |
| 完整 UI 验收 | 最新完成的 34185637618 已使用 Xcode 26.2，KMP 和 StoreKit 8/8 通过；UI 在付费墙文字节点 not hittable 失败 | 已推送按可见 frame 操作的修复，34186615415 运行中；不省略金额和业务断言 |
| 试用资格 | 已从真实 StoreKit offer/eligibility 生成；不符合资格显示 Subscribe。Xcode 26.2 StoreKit 8/8 通过，Android 不符合资格文案测试通过 | 继续完整 UI 验收及真实沙盒核验 |
| 订阅说明和法律链接 | 已增加续订说明、Privacy Policy、Apple 标准 EULA 和打开失败提示；政策按实际本地存储/历史保留写入 docs/privacy-policy.md | 发布合并后检查主分支公开政策 URL；所有者确认实际隐私披露与商店资料 |
| Privacy manifest | 已添加 PrivacyInfo.xcprivacy 和自有 UserDefaults 的 CA92.1 用途，并加入 Xcode 应用资源 | 清点最终静态链接依赖的 required-reason API、验证 Archive 隐私报告 |
| 导入交互 | 已改为系统 JSON 文件选择器，取消不导入；复用现有完整校验和原子恢复，测试增加真实文件选择 | 尚待 iOS 编译与实际导入验收 |
| 订阅响应时限 | 已改为只恢复一次的 continuation 竞速；商品、资格、权益、恢复请求各 15 秒调用方上限，迟到结果忽略；添加相关原生测试 | 尚待测试，并需真机沙盒覆盖断网、认证弹窗与恢复前台 |
| 系统版本覆盖 | 当前 CI 为 iPhone 16 / iOS 18.5，部署最低版本为 15 | 当前系统及最低支持版本兼容性尚未完整验收 |
| 正式构建 | 已增加无签名 Release device Archive 和 manifest 检查门禁，UI 尚未通过所以尚未执行 | 通过完整模拟器门禁后执行；账号就绪后签名 Archive、验证及 TestFlight |
| 上传 SDK 要求 | CI 已显式选择 Xcode 26.2 / iOS 26 SDK；34185637618 实际完成 Kotlin/Compose/Swift 编译与 StoreKit 8/8 | 继续完整 UI、Release 构建与运行验证；最低部署仍是 iOS 15 |
| 上架图标 | 已输出并验证无 alpha 的 1024×1024 RGB 图标 | 验证正式包资产 |

## 需要所有者提供的资料

Apple Developer 会员及 Team；App Store Connect 同 ID 月订阅、美国区 $1.99 价格和 7 天试用；协议、税务、收款；CI 签名与上传凭证（仅存 Secrets）；真实隐私政策和支持地址、商店及隐私披露资料；TestFlight 真机测试人员。

这些资料不阻挡模拟器验收。当前没有签名 IPA，也不具备“仅填账号即可上线”的证据。

Apple 上传 SDK 与 required-reason API 要求来源：[Upcoming Requirements](https://developer.apple.com/news/upcoming-requirements/)，本轮实际读取日期 2026-09-08。

## 测试覆盖边界

- 当前 XCUITest 单一长流程包含引导、每日提醒、Weekly 展示、免费商店锁定、订阅、Daily 自定义任务编辑完成、奖励编辑购买使用出售、心愿切换、备份重置恢复、删除及重启权益。
- 源码中存在测试步骤不代表执行通过；该长流程会在第一个失败处停止。
- 原生 StoreKit 测试覆盖恢复购买、过期和退款降级，但尚未通过 UI 操作验证全部对应情形。
- 本地已扩展月任务频次、Deposit、免费高余额购买锁定的 iOS UI 步骤，尚待实际运行；无效备份回滚仍只有共享/Android 规则覆盖，不能称为 iOS UI 已验收。
- Android 本轮 JVM test 成功（未变更项复用缓存），Room 30/30、Compose 20/20、lintDebug、assembleDebug 成功；API 33 安装冷启动成功、crash buffer 为空。
- 当前 Android APK：0.55 (19)，28,398,842 字节，v2 签名有效；SHA-256 `6C7CD2F1D66952BBA7E52B4C5362FCACF2FC4D5B196D29A72029DE23D0D725F1`。
