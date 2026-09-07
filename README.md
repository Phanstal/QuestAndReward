# QuestAndReward

一个同时支持 Android 和 iOS 的本地优先游戏化任务与奖励应用。项目使用 Kotlin Multiplatform、Compose Multiplatform 和 Room KMP，共享业务规则、数据层、ViewModel 与界面；平台工程负责启动、文件访问、系统偏好与订阅适配。

当前开发版本为 `v0.55`（Android `versionCode = 19`，iOS `CURRENT_PROJECT_VERSION = 19`），Room 数据库版本为 `v8`。

## 当前功能

- 首次启动依次展示品牌欢迎页和四步功能引导，完成后进入主界面
- Landing 标题 “Level Up Your Life” 固定单行显示，在手机宽度下不会因自动换行破坏视觉层级
- 主界面以 Figma Make `t=7USLGNgZqfnofwom-1` 为唯一 UI 基准：英文 Quests/Store/Rewards/Stats 导航、紧凑奖励商店和 Premium 升级提示
- 免费态可查看默认 Coffee 心愿，锁定任务编辑、新增任务、心愿目标编辑、自定义奖励和 Coffee 购买；即使余额高于价格也不会调用兑换命令
- iOS 使用 StoreKit 2 核验 `quest_reward_monthly` 的当前 entitlement、交易更新和恢复购买；仅 verified、未撤销、未过期的交易解锁 Premium。月费为 `$1.99/month`，包含 7 天免费试用且无年付
- Android 保留会话级演示订阅，未接入 Google Play Billing；关闭进程后恢复免费态
- 任务、商店、物品栏和统计四个 Compose 页面
- 新增、编辑、删除和完成自定义、每日、每周、每月任务，周期按设备当地时间 04:00 切换
- 每日和每周任务的截止时间使用 5 分钟步进选择器；每周可选择执行日；每月任务可设置每月完成 `1-8` 次。排期用于记录和展示，不限制任务完成时间
- 月任务编辑器提供两行 `1×` 至 `8×` 直选按钮；任务与奖励 emoji 选择器每行 8 个
- 完成任务获得金币和经验，撤销完成会扣回对应金币与经验
- 商店奖励可设为或取消心愿；设置目标时按奖励价格的 10%（向下取整）扣除 Deposit，目标固定显示在任务页并展示余额进度
- 心愿奖励兑换时只扣除 `cost - deposit`，额外奖励 100 金币；取消或切换目标不退还旧 Deposit，允许余额变为负数
- Premium 心愿目标在任务页使用浅色进度卡，展示 Deposit、剩余金币和预计天数；每日提醒使用居中详情卡，可选择 “Got it” 或 “Go Complete Quests →”
- 新增、编辑、软删除和购买奖励；编辑器删除会立即关闭并提交软删除命令，不再增加二次确认
- 商店前三个目录奖励使用铜、银、金边框，第三个带金色光晕；心愿奖励满足余额时显示 “🎉 Redeem”
- Rewards 使用固定两列网格；奖励可在深色确认卡中使用，或按原价 70% 出售
- 奖励购买必须先具备 Premium 权限，再检查金币余额和奖励剩余库存；物品栏没有持有数量或容量限制
- Stats 展示 Balance、Total Earned、Done Today、All Time、最近完成记录、Quest Harvest Board 和 Wish Savings Board；完成记录不提供 UI 撤销入口
- Stats 可导入、导出完整 JSON 备份 v3（兼容读取 v2）；选择导入文件后立即进行完整校验与原子恢复，不再显示二次确认
- 待同步事件 NDJSON 的底层接口继续保留以兼容未来同步，但当前版本已无用户界面入口
- 全新数据库初始化 3 条英文预置任务与 3 个奖励，初始金币和 Total Earned 均为 120；Coffee（Specialty Coffee，500 金币）默认成为 Deposit 为 0 的愿望目标
- v0.55 目录迁移仅在没有愿望时补齐一次 Coffee，不覆盖已有愿望；用户此后主动取消不会在下次启动被重新创建。数据重置会恢复可用的标准 Coffee 愿望并清除进度
- 任务进度、愿望卡和每日愿望提醒均使用 500ms FastOutSlowIn 缓动，并把显示值限制在 `0..1`
- Android 与 iOS 共用同一套 Compose 页面、Application Service、周期规则、Room schema 和备份协议
- Android 使用 Storage Access Framework 选择导入/导出文件；iOS 将备份放在应用 Documents，并导入其中最新的 `quest-backup-*.json`

## 模块

- `app`：共享 Compose UI、ViewModel、应用装配，以及 Android/iOS 平台入口
- `application`：共享用例编排、命令超时、统一错误、健康状态和导出端口
- `domain`：共享纯 Kotlin 模型、Repository 契约、事件和业务规则
- `data`：共享 Room KMP schema、事务和 Repository 实现，以及平台数据库路径/偏好适配
- `sync`：共享事件、完整备份 JSON 编解码，以及未来同步传输契约
- `iosApp`：SwiftUI iOS 宿主、StoreKit 2 平台适配、Xcode project、StoreKit 测试配置、Info.plist 和 AppIcon

主要依赖方向为 `Compose/ViewModel -> Application -> Domain`。`data` 实现 Domain Repository，`sync` 实现事件与备份编解码端口并声明同步传输契约，两者只在 App Composition Root 中装配。当前 App 没有装配 `SyncTransport` 的网络实现。

每次业务写入都携带 `idempotencyKey` 和 `traceId`，并在同一个 Room 事务中更新本地投影、追加积分账本、写入事件及保存命令结果。相同幂等键的重试直接返回首次结果。

事件编解码器为未来 NAS 网关提供以下设备隔离的单事件目标路径；当前 UI 实际导出的是一个 NDJSON 文件，并不会直接写入该目录：

```text
events/<device-id>/<event-id>.json
```

## Android 构建

需要 JDK 17、Android SDK 35 和 Build Tools 34.0.0。应用最低支持 Android 8.0（API 26），targetSdk 为 33：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat test lintDebug assembleDebug
```

Debug APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以直接用 Android Studio 打开本目录并运行 `app` 配置。

## iOS 构建

iOS 需要 macOS、Xcode 16.x 和 JDK 17。打开 `iosApp/iosApp.xcodeproj`，选择共享 `QuestAndReward` scheme 后即可在模拟器使用仓库内的 `QuestAndReward.storekit` 测试购买和恢复；真机运行需为 target 选择 Apple Developer Team。Xcode Build Phase 会自动构建并链接共享 Kotlin framework。

完整的环境安装、共享测试、模拟器运行、真机签名、archive 和 IPA 导出步骤见 [`docs/macos-ios-build-guide.md`](docs/macos-ios-build-guide.md)。Windows 不能运行 Kotlin/Native Apple 链接器或 Xcode；仓库通过 GitHub Actions 的 macOS runner 执行无签名 Simulator 构建和测试，真机或 App Store 包仍需 Apple Developer 签名环境。

## v0.55 验证状态

v0.55 架构静态门禁已经完成；自动化、Android 模拟器和 GitHub Actions iOS Simulator 结果将在实际执行后回填。未回填前不把本节视为构建或发布通过证明。

iOS CI 会执行 Kotlin/Native 测试、StoreKit 原生测试和 XCUITest，随后分别构建 arm64 与 x86_64 Simulator 应用、合并 Mach-O、安装冷启动并扫描 `QuestAndReward` 崩溃报告。成功后上传的 ZIP 是未签名 Simulator `.app`，不是 IPA。

最终 Android Debug APK 信息（测试后回填）：

```text
app/build/outputs/apk/debug/app-debug.apk
package=com.familyquest.app
versionName=0.55
versionCode=19
minSdk=26
targetSdk=33
size=pending
SHA-256=pending
signature=pending
```

iOS Simulator ZIP 信息：

```text
release asset=QuestAndReward-v0.55-ios-simulator.zip
CFBundleIdentifier=com.phanstal.questandreward
CFBundleShortVersionString=0.55
CFBundleVersion=19
CFBundleExecutable=QuestAndReward
CFBundlePackageType=APPL
architectures=x86_64, arm64
size=pending
SHA-256=pending
signing=unsigned iOS Simulator app (not IPA)
```

门禁命令为：

```powershell
.\gradlew.bat test lintDebug assembleDebug
.\gradlew.bat :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

## v0.55 数据保存与同步

结论：v0.55 没有自动或双向同步。Android 与 iOS 均采用 **Room/SQLite 本地持久化 + 事务 outbox + 用户手动导入/导出完整备份**，没有连接 NAS、SVN 或其他远端服务。StoreKit entitlement 仅控制 iOS Premium UI 权限，不进入 Room 业务数据或同步协议；设备上的 Room 数据库仍是唯一业务事实源。

### 本地写入

每个业务命令都携带 `idempotencyKey` 和 `traceId`。Repository 在同一个 Room 事务中更新任务、账本等本地投影，追加不可变事件，并保存命令结果；同一个幂等键的重试不会重复产生业务结果。

新事件写入 `events` 表时默认为 `synced = false`。事件包含 `schemaVersion`、`eventId`、`deviceId`、`traceId`、`idempotencyKey`、聚合类型与 ID、事件类型、发生时间、逻辑计数和 payload。这个事件表是为后续远端同步准备的事务 outbox，目前只在本地使用。

### 手动事件导出

底层仍可通过 Application 导出 `family-quest-events.ndjson`，每行是一条 `synced = false` 的事件，按发生时间、逻辑计数和事件 ID 排序。为对齐最新 Figma，当前 Stats 不再提供这一用户界面入口；该能力仅作为未来同步兼容接口保留。

导出只是把事件复制到用户选择的文件，不代表 NAS 或其他远端已经接收。当前代码没有把事件标记为已同步的方法，所以：

- 导出后待同步数量不会清零；
- 重复导出会再次包含之前导出的事件；
- 文件需要由用户自行保存或传输，应用不会自动上传。

### 完整 JSON 备份 v3

Stats 提供并列的 “Import Data” 和 “Export Backup” 入口。导出生成 `quest-backup-YYYY-MM-DD.json`，使用 `wish-force-backup` 格式和 `formatVersion = 3`。备份包含角色与当前选择、完整心愿目标（奖励、Deposit、提醒日期）、任务、奖励、完成记录、账本、兑换与物品状态、不可变事件和已处理命令，可用于恢复当前应用数据。

导入选择文件后立即执行结构、版本和引用完整性校验，并在单一 Room 事务中原子恢复；任何失败都会回滚并保留原数据库。完整备份 v3 兼容读取 v2；v2 缺少 Deposit 和提醒日期时按无 Deposit、无提醒日期处理。v0.3 的旧展示型 JSON 和 NDJSON 事件文件都不能通过 “Import Data” 恢复。Android 由系统文件选择器选择文件；iOS 从可通过 Files 访问的应用 Documents 中读取文件名排序最新的备份。

### 尚未实现

- `SyncTransport` 只有 `push/pull`、cursor 和 accepted event IDs 的接口定义，没有生产实现或 App 装配；
- 没有 HTTP、WebDAV、SMB、SVN 客户端、NAS 鉴权、WorkManager 后台任务或自动重试；
- 没有远端确认、游标持久化、事件拉取、静默回放和本地投影合并；
- 没有多设备冲突与收敛规则，现有幂等约束只保证单设备本地命令和事件不重复。

`android:allowBackup="true"` 只是允许 Android 系统参与系统级备份，不等于应用已经实现 NAS 同步。

### NAS 接入方案

SQLite 文件不得直接复制或提交到 NAS/SVN。后续应以不可变事件作为同步源，并增加一个位于 NAS 侧的网关：

1. Android/iOS 客户端在 Room 事务外读取 `synced = false` 的 outbox，通过 `SyncTransport.push` 批量发送事件。
2. NAS 网关按 `eventId`、`deviceId` 和 `idempotencyKey` 去重，将每条事件耐久保存为 `events/<device-id>/<event-id>.json`；如使用 SVN，则由网关执行 `update/add/commit`。
3. 只有网关保存或 SVN commit 成功后，才返回精确的 accepted event IDs；客户端再在本地事务中把这些事件标记为已同步。
4. 客户端使用持久化 cursor 调用 `SyncTransport.pull` 拉取其他设备的新事件。事件落库、静默更新投影和 cursor 推进必须在同一个 Room 事务中完成，回放不能再次生成 outbox。
5. 第二台设备接入前，必须先定义同一任务周期重复完成、并发编辑、删除 tombstone 和 seed 数据的确定性收敛规则，并增加双设备测试。

网络实现应由 Application 层编排，并由 Android WorkManager / iOS BGTaskScheduler 调度，使用明确超时、最多 3 次指数退避和熔断降级；只通过 HTTPS、受控局域网或 VPN 访问 NAS，客户端 token 分别存入 Android Keystore / iOS Keychain，SVN 凭据只保存在网关。

Room 数据库当前版本为 v8，显式注册 `Migration(1, 2)` 至 `Migration(7, 8)`，schema 输出到 `data/schemas`。`Migration(6, 7)` 为任务增加 `monthlyTargetCount`，`Migration(7, 8)` 增加按角色持久化的心愿目标表；迁移不使用破坏性回退，v7 -> v8 测试已在 API 33 模拟器通过。

## 工程规范

- 架构宪法：[`docs/architecture-spec.md`](docs/architecture-spec.md)
- AI 架构合规性自检：[`docs/architecture-compliance-report.md`](docs/architecture-compliance-report.md)
- macOS/iOS 编译指南：[`docs/macos-ios-build-guide.md`](docs/macos-ios-build-guide.md)
