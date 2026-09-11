# AI 架构合规性自检报告

## 当前候选：QuestReward v0.56 (20)，2026-09-11

本轮按 `architecture-spec.md` 完成静态检查后执行测试。✅ 可靠性：Pin 和旧押金退款复用命令超时、幂等键、Room 原子事务；导入退款也处于恢复事务中。✅ 扩展性：不新增平台业务实现或公共 API。✅ 可维护性：沿用 traceId 和兼容事件，私有 Coffee 恢复函数改为描述真实用途的名称。✅ 分层：共享 Compose 经 ViewModel/Application 调用，免费态只展示和 Pin seed Coffee，仍禁止购买。✅ 复用：沿用 wish goal、ledger、processed commands 和备份格式，未创建重复基础模块。Room v8、事件 v1、备份 v3 和应用技术标识不变。

新装及重置为空愿望；取消 Deposit 与兑换赠币；存量未结算 Deposit 通过账本返还一次，已有目标和历史保留。测试已取得 JVM 成功、Room 31/31、Compose 21/21；最终清理后的重跑、Lint、APK 与 iOS CI 尚待验证。以下 v0.55 内容为历史验收记录，不代表 v0.56 已通过 iOS 验收。

最终 Android 重跑：`test :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest lintDebug` 成功，Room 31/31、Compose 21/21、Lint 0 errors / 43 warnings；随后 `assembleDebug` 成功。APK 0.56 (20)、显示名 QuestReward、包名 com.familyquest.app、minSdk 26 / targetSdk 33，v2 签名通过，28,400,303 bytes，SHA-256 `1B661F52101ACE3370AB4CABDD4623B4EBA8C675C73928D55122FB8325098764`。API 33 模拟器覆盖安装成功，冷启动 Status: ok。iOS 本轮仍待 CI 实际结果。

检查日期：2026-09-08
检查对象：QuestAndReward v0.55 Kotlin Multiplatform 测试前候选版本
检查范围：Android/iOS 构建、共享 Compose、Domain/Application/Sync/Data、Room KMP v8、StoreKit 2 平台适配器、备份与事件兼容
适用基线：[architecture-spec.md](architecture-spec.md)  
阶段结论：v0.55 测试前静态架构门禁已完成，五项适用维度和追加检查均为 ✅，可以进入自动化、Lint、Android 模拟器和 GitHub Actions iOS Simulator 门禁。下方测试状态目前只写实际结果，未执行项不预填通过。

| 检查维度 | 规范要求 | 结果 | 本轮证据与结论 |
| :--- | :--- | :--- | :--- |
| 可靠性 | 超时、降级、幂等和事务一致性 | ✅ | `FamilyQuestService.execute` 继续以 `withTimeout` 保护命令并映射统一错误；写命令仍携带 idempotencyKey/traceId。共享 `RoomFamilyQuestRepository.processCommand` 继续以 Room KMP `immediateTransaction` 原子提交投影、账本、事件和 processed command。iOS 订阅启动核验、购买、恢复、交易更新和前台刷新统一回到 `Transaction.currentEntitlements`；只有 verified、未撤销、未过期且未升级的指定产品解锁。StoreKit 失败通过 `PremiumUiState.errorMessage` 显示并保持最后一次已验证权限，不伪造成功。 |
| 扩展性 | 核心依赖抽象并隔离平台变化 | ✅ | Domain/Application/Data/Sync 和 Room schema 未依赖 StoreKit；`SubscriptionManager.swift` 位于 Apple 宿主边界，经 `IosPremiumBridge` 只向共享 Compose 注入 UI 权限状态。Android Composition Root 继续提供会话级演示状态，未把 Google Play Billing 或 Apple 类型带入 commonMain。04:00 周期、月度次数、奖励和备份规则没有复制到 Swift。 |
| 可读性 | traceId、统一错误和可诊断边界 | ✅ | 业务错误仍由 `ErrorCodeEnum` 与 `ApplicationResult` 统一处理；StoreKit 的用户可见错误仅暴露稳定文案，不暴露底层异常或交易详情。静态扫描未发现生产代码中的 System.out、printStackTrace、GlobalScope、Thread.sleep、TODO 或 FIXME。订阅权限是 UI 平台能力，不写业务事件；NAS 网关尚不存在，MDC 条款暂不适用。 |
| 模块解耦 | Compose/ViewModel -> Application -> Domain <- Data/Room | ✅ | app 只在 Composition Root 装配 Infrastructure；共享 UI/ViewModel 不引用 DAO、Room、StoreKit 或 SyncTransport。Swift `SubscriptionManager` 只调用 Kotlin `IosPremiumBridge`，共享 `FamilyQuestScreen` 只消费 `PremiumUiState` 和回调。默认 Coffee 仍由 Data Repository 在现有种子/重置事务中维护，并记录兼容的 `WISH_GOAL_UPDATED` 事件。 |
| 杜绝重复造轮子 | 检索并复用稳定实现 | ✅ | 已扫描 common、shared-kernel、base 和 Util/Helper/Converter/Client 命名，没有可复用的自有公共层。订阅直接使用 StoreKit 2 与 StoreKitTest；状态分发复用 StateFlow/ObservableObject；动画复用 Compose `animateFloatAsState`、`tween` 和 `FastOutSlowInEasing`，没有另建计时器或平行业务实现。 |

## 追加检查

### v0.56 QuestReward 静态门禁（2026-09-11）

- ✅ 按 PDF 和用户确认：免费用户仅能置顶种子 Coffee；新增置顶不扣押金，兑换不再生成 100 金币奖励；保留历史事件和旧押金字段兼容备份。
- ✅ 押金退款复用现有 Room 事务/账本/幂等标记及 WISH_GOAL_UPDATED 事件；启动仅执行一次，旧备份恢复在同一恢复事务中退回尚未抵扣押金。失败保持原子回滚，不改 schema v8。
- ✅ 首次安装及重置不创建默认心愿；共享 UI/ViewModel 仍通过 Application 调用，品牌显示改 QuestReward，原包标识/StoreKit 产品/数据库路径保留以支持覆盖升级。
- ✅ 复用现有种子恢复、备份和 Pin 命令，不增加平行业务层；补充退款幂等和无额外奖励断言。本节仅为测试前静态结论，v0.56 测试结果未预填。

### 2026-09-09 最终实际结果（优先于下方历史记录）

- ✅ 完整运行 34311554513 / b428abb 全绿：KMP、StoreKit 9/9、完整 UI 1/1（425.029 秒）、无签名 Release Archive、隐私政策网页、arm64/x86_64 Simulator、安装冷启动、plist 和打包检查通过。已下载 xcresult 摘要确认 10 passed / 0 failed / 0 skipped。
- ✅ 当前 Android test/lintDebug/assembleDebug 成功，Compose 20/20、Room 30/30 重跑通过；APK v2 签名有效，安装冷启动成功，crash buffer 为空。Lint 0 错误、42 条升级类及 target SDK 警告，非零警告。
- ✅ 本轮最终产物大小和 SHA-256 见 app-store-readiness.md，iOS ZIP 本地哈希与 CI 日志一致。生产源码未因输入/文件选择测试修复改变，所有既有业务断言保留，未使用数据库或订阅测试后门。
- ⏳ 未完成项目仍为 Apple 签名/真实商店及真机、最低系统运行覆盖和最终依赖隐私审核；不能将无签名 Archive 与模拟器验收当成 App Store 可直接上线。

- 34308557031：原生 StoreKit 9/9 通过；UI 已通过编辑字段清空/替换、任务完成、高余额退款锁定、奖励使用出售、月任务两次完成、导出及重置。失败截图仍为 Files picker，JSON 文件 cell 可用；没有进入恢复后的任务页。✅ 本轮仅调整测试点击真实文件 cell 的缩略图并等待 Backup imported.，成功后才导航和断言恢复数据；不改生产导入逻辑，不放宽原有历史/任务断言。独立静态门禁通过后重跑完整验收。

- ✅ #35 证据核查及输入修复静态门禁：已下载实际截图，金额文本在左端、长按字段中心位于空白处，截图无选择菜单；金额框完整可见且获得焦点。测试改为点击字段右端定位，再退格并独立等待 label 为空，最后输入并保留精确内容断言。仅 XCUITest 与报告变化，无生产/数据库/领域/订阅修改；效果必须经新一轮完整 CI 验证。保留用户 log/ 文件，不提交崩溃报告。

- ✅ 文本替换修复静态门禁：34222249523 在重命名时最终 label 为 AccepVerified Quest，证明不是单纯等待不足。旧 fill 假设点击后光标位于末尾，按全文长度退格；本轮改为真实长按并要求系统 Select All 出现、点击后输入，再保留 10 秒精确内容校验。未使用测试后门或放宽金额/业务断言，生产代码与数据架构不变。打包运行 34304117045 已先启动，UI 修复在后续独立完整验收中验证。

- ✅ 打包修复静态门禁：34237186450 日志明确将二进制路径解析为 verify_arch 的架构名。本轮将 lipo 输入文件放在 -verify_arch 之前，保留 arm64/x86_64 校验、安装冷启动、plist 核验及预发布标识；不涉及生产代码、数据或架构变化。先重新运行预发布打包，完整 UI 修复单独验证，不能把测试包发布视为全功能通过。

- ✅ 用户明确要求先发布 v0.55 供 Mac 手动测试。本轮仅新增显式 preview_release 工作流输入：默认完整门禁不变；预发布仍运行 KMP、双架构构建、安装冷启动和版本/启动 plist 检查，但跳过完整 UI 与设备 Archive，产物及 GitHub Release 标为 prerelease 并披露未完成验收。生产源码、Room、订阅及领域边界不变；此授权不等于正式发布门禁通过。CI 写权限仅用于已授权的预发布上传。

- 34215391068：StoreKit 9/9 通过（4.827 秒）；UI 在 task-reward 可见高度 13pt 的断言失败。金额框 y=429..442，系统输入栏 y=517，旧滑动起点 y=487 没有按实际抽屉视口定位。✅ 本轮复用 BottomDrawer，只在 IME padding 内侧、滚动区域外侧增加 editor-viewport 标识；XCUITest 的输入和按钮滚动统一限定在其真实 frame 内，并支持向上方目标回滚。未改变页面布局、业务规则、Domain/Application/Room/事件，继续保留 48pt 可见高度、精确输入值和完整业务断言。测试前静态门禁通过，效果待 CI 验证。

- 34192190785：StoreKit 原生 9/9 通过（5.844 秒），UI 创建 500 金币任务成功，重命名后的即时字段断言失败；同一失败日志中 task-title 已为 Verified Quest。✅ 本轮仅让已有 fill 内容断言等待可访问性状态更新，10 秒上限且仍要求精确匹配；不修改生产代码、订阅、业务规则、Room 或事件。静态门禁通过后重新执行 CI；完整 UI 尚未通过。

- 34187777245：KMP 与模拟器启动通过；Swift 编译报 Transaction 同时匹配 StoreKit/SwiftUI，原生与 UI 测试均未执行。✅ 修复仅在平台适配器参数使用 StoreKit.Transaction 全限定类型，无业务、权限规则、数据库或接口变化；静态门禁通过，连同独立 iOS 备份测试进入下一轮验证。

- ✅ iOS 备份事务测试静态门禁：复用现有 Room builder、Repository、IosPreferencesStore 和 Android 已验证的冲突场景；仅 iosTest 增加真实 SQLite 回滚验证，无生产 API、schema 或业务规则变化。每次使用 UUID 命名的临时数据库和独立偏好 suite，清理仅限该测试生成的三个文件及两个 suite。断言失败后完整备份快照、选择角色不变，实际结果待 macOS CI 验证。
- 34187777245 已实际通过 KMP 和 iPhone 16 / iOS 26.2 启动，StoreKit/UI 尚在执行；不能将运行中状态计为通过。

| 检查项 | 结果 | 本轮证据与结论 |
| :--- | :--- | :--- |
| 产品与版本命名 | ✅ | Gradle 根项目、iOS target/scheme/framework、Android/iOS 显示名均为 QuestAndReward。Android 为 0.55 (19)；iOS MARKETING_VERSION = 0.55、CURRENT_PROJECT_VERSION = 19。Android applicationId 暂保留 com.familyquest.app，用于覆盖升级；iOS bundle id 为 com.phanstal.questandreward。 |
| KMP 源集边界 | ✅ | Domain、Application、Sync、Room entities/DAO/repository、ViewModel 和 Compose 页面位于 commonMain。androidMain 只增加会话级 Premium 注入；iosMain 只增加状态桥；Apple 的 Product、Transaction、AppStore 和 SKTestSession 类型全部局限在 iosApp Swift target/测试 target。 |
| Room v8 与迁移 | ✅ | 数据库仍为 v8，v1→v8 迁移 SQL 已转换为 Room KMP SQLiteConnection，未启用破坏性迁移。Android 数据库文件仍为 family-quest.db，偏好 suite/key 也保持不变；iOS 使用同一 schema 新建本地数据库。旧 schema JSON 全部保留。 |
| 时间与周期 | ✅ | TaskRecurrenceRules 使用 Instant/TimeZone/LocalDate 的 KMP 类型；每日 04:00、周一 04:00、月初 04:00、年初 04:00 及 DST 语义未变。Application 与 Repository 使用同一设备时区对象，不在 UI 重算 occurrence。 |
| UI 单源 | ✅ | Android 与 iOS 继续调用同一个 `FamilyQuestScreen`、编辑器、Landing、Theme 和 MainViewModel。`PremiumUiState` 仅替换旧的页面内 Boolean；任务进度、愿望卡和提醒统一用 500ms FastOutSlowIn 动画。Landing 的 “Level Up Your Life” 仍为 30sp、单行且禁止 soft wrap。 |
| 订阅可靠性 | ✅ | 产品 ID 固定为 `quest_reward_monthly`；价格优先使用 Product 本地化结果，失败回退 `$1.99/month`。CHECKING 阶段锁定功能；取消、pending、失败与 unverified 不解锁；成功交易 finish 后重验；恢复使用 `AppStore.sync()`；前台和 `Transaction.updates` 均重验 entitlement。免费 Coffee 按钮以 verified Premium 和余额双重条件启用。 |
| 默认 Coffee 迁移 | ✅ | 新安装在种子事务中创建 Deposit=0 的 `seed-reward-coffee` 愿望；重置在同一事务恢复标准 Coffee 与愿望。v0.55 使用独立、稳定的 processed_commands 幂等键，在同一 Room 事务记录迁移完成；标记随现有完整备份导出/恢复。保留 v0.51 目录迁移键，已有愿望不覆盖，迁移后取消不会被下次启动补回，不重新恢复已删除的目录任务。Room v8 和备份 v3 结构未变。 |
| 备份与事件兼容 | ✅ | 备份 v3、事件 schema v1、幂等命令、软删除和 outbox 结构未修改。StoreKit entitlement 不写 Room、备份或领域事件。Android 保留系统文档选择器；iOS 继续使用 Documents。NAS 自动同步仍未实现。 |
| 禁用模式与静态卫生 | ✅ | 生产代码没有 allowMainThreadQueries、fallbackToDestructiveMigration 或 UI/ViewModel 直连 DAO；allowMainThreadQueries 仅保留在隔离的 instrumentation 测试。 |

## 测试门禁状态

### 2026-09-08 验收前检查

- 34187076820 已通过 KMP 编译（原生委托修复有效），但模拟器启动超过 6 分钟未结束。✅ 下一轮固定到官方 runner 清单已有的 iPhone 16 / iOS 26.2，启动步骤上限 5 分钟并保留启动日志；不再选取不确定的首个 iOS 26 runtime，不改变应用或测试通过条件。

- ✅ 已验证交易若已撤销/过期/升级，购买回调与更新监听在等待 SDK finish/重新核验之前立即清除缓存权益并锁定；避免后续网络超时错误保留已知失效的 Premium。复用 grantsPremium 判定，既有原生撤销/过期/监听测试继续保留，无领域或数据库变化。

- ✅ GitHub API 已确认永久链接的政策文件存在（2,694 字节）；CI 在正式构建阶段增加公开网页 HTTP 成功及标题核验，curl 请求 30 秒上限、最多 2 次重试，仅只读公开文档，不修改业务或订阅。浏览器页面测试仍需该步骤实际通过。

- ✅ 政策入口固定到已推送的政策版本提交永久链接，不依赖尚未合并的 main 文件或临时分支保留；不提前合并未通过验收的应用代码。HTTP 页面可用性仍需单独核验，本机网络读取出现 EOF，不声称已完成网页验收。
- 最新 Android test/Compose 20 项再次通过；Lint 实际为 0 error、1 条既有 OldTargetApi warning（targetSdk 33），不是无告警。本轮不升级 Android targetSdk。

- ✅ 将现有法律入口提取为同文件私有 Compose 组件，付费墙和 Stats 共用，使 Premium 用户也能访问政策；复用已存在的 URI 打开及错误处理，不新增服务/仓库/领域规则。共享 UI 变化后将补跑 Compose instrumentation 与 Lint，再打包。

- 34186615415 在 Kotlin 编译阶段发现 Objective-C/Kotlin 混合继承限制，未执行 StoreKit/UI。✅ 已将 UIDocumentPicker 委托改为 BackupActions 内部持有的 NSObject 对象，不复制文件处理或业务规则；平台边界、回调及事务恢复保持不变，下一轮继续编译验证。

- ✅ 补充已有 XCUITest 长流程：实际完成任务累积到 620 金币后由 StoreKitTest 撤销订阅，验证 Coffee 仍 Locked/禁用且余额不变，再恢复真实模拟交易；验证 10 金币心愿的 1 金币 Deposit；检查月任务八个频次按钮并完成两次 occurrence。均复用现有控件/StoreKitTest，不加入生产测试后门或跳过断言。

- 34185637618：Xcode 26.2 已编译 Kotlin/Compose 和 Swift；StoreKit 8/8 通过（4.9 秒，包含试用资格断言）。UI 在 Start Free Trial 的文字虚拟节点 not hittable 失败，后续未通过。
- ✅ 复用 interact 处理 iOS 26 虚拟节点：严格确认真实 frame 完整可见且不在键盘后方后，若系统不提供命中点则点击其中心；保留购买、编辑、恢复等业务结果断言，不通过跳过交互来通过测试。下一轮同时验证已提交的平台超时与文件选择器。

- ✅ iOS 导入复用现有 BackupActions，改由 UIDocumentPicker 选择 JSON，取消不执行导入；选中内容仍调用现有 Application 校验和 Room 原子恢复，不复制恢复规则。平台回调释放 security-scoped 访问，导出文件错误转换为现有失败回调；UI 测试增加真实文件选择步骤。无备份格式或领域接口变化。
- Android 本轮后续实际结果：App instrumentation 20/20 通过，lintDebug 成功；原签名冲突后测试工具已清理该模拟器安装，未执行手动卸载或数据删除。后续变更仅 iOS 平台/测试，Android 不重复扩大测试。

- ✅ 后续可靠性修复：复用 SubscriptionManager 平台边界，以一次性 continuation 竞速替代会等待子任务结束的 task group；商品、资格、entitlement 和恢复请求均有 15 秒调用方上限，SDK 迟到结果不写权限。核验失败只保留尚未过期的最近已验证权益，否则维持锁定并显示可恢复错误；系统购买确认仍由用户控制，未伪造购买成功。新增迟到结果/后续请求测试，无 Domain/Application/Room 变化。
- JVM test 已成功（未变更任务复用缓存）；Data instrumentation 30/30 通过；App instrumentation 因旧安装签名冲突未执行成功，不能计为测试通过。下次运行前已检查仅平台超时及错误态恢复按钮发生变化，静态门禁仍为 ✅。

- ✅ 上架缺口修复静态门禁：试用资格只由 Swift StoreKit 的真实 introductoryOffer/eligibility 产生，经现有桥传入 PremiumUiState；iOS 默认不承诺试用，购买后立即去除试用资格。Domain/Application/Room v8/事件/备份不变；Android 保留演示订阅并明确展示无扣费说明。
- ✅ 复用现有状态桥、付费墙和 LocalUriHandler 增加自动续订说明、法律入口及打开失败反馈；付费墙可滚动，适配增加的文本。隐私政策按实际本地数据和重置保留历史语义编写，正式 main URL 必须在发布合并后检查可用，不能预填上线完成。
- ✅ PrivacyInfo.xcprivacy 声明应用自有 UserDefaults 用途 CA92.1、无跟踪及无开发者收集字段，加入应用资源；最终依赖包的隐私报告仍需 Archive 验证。图标通过现有 .NET 图像库铺底输出 RGB，不引入运行时依赖。
- ✅ CI 显式选择已从官方 runner 清单核实的 Xcode 26.2，选择 iOS 26 模拟器并增加无签名 Release Archive/manifest 检查；最低部署仍为 iOS 15。现有测试断言保留，扩充未获试用资格文案和购买后资格断言。以上为静态检查，不代表新工具链编译和运行已通过。

- 已下载 34183423412 轻量证据，摘要 8 passed / 1 failed / 0 skipped；实际截图显示金额 30500、标题正确、Add 在数字键盘上方完整可见。
- ✅ 根据截图，只在目标按钮不可点击时执行键盘隐藏/滚动；可见 Add 直接真实点击，不对数字键盘增加非业务必需的隐藏断言。输入清除和此调整仍待推送验证；网络连续连接重置/443 不可达，旧提交误触发的 34184540550 已取消，不计为新测试。

- 34183423412：KMP 与 StoreKit 8/8 通过（99.0 秒）；系统 Continue 已关闭，金额完整显示并获得 Keyboard Focused。UI 输入后金额为 30500：虚拟 TextView.value 为空字符串而 label 为 30，未发送删除键。
- ✅ 下一轮只修正 fill 使用已观察到的 label 读取旧文本，保留删除键输入和结果断言；不改生产界面、业务、数据库、同步或分层。系统提示与可见性处理已有实际成功证据，完整流程仍未通过。

- 34182374786：KMP 通过、StoreKit 8/8 通过（68.9 秒），UI 仍在金额字段可见性断言失败。完整日志显示系统 UIContinuousPathIntroductionView 含 Continue 按钮覆盖键盘，SystemInputAssistantView 位于 y=516..561，原滚动起点 y=531 落在系统栏内。
- ✅ 下一轮仅修正测试驱动：首次键盘出现时真实点击系统教学提示 Continue 并确认消失；滚动上界同时排除预测词栏。继续完整字段可见性、输入值和后续业务断言。无生产代码/架构/持久化变化，静态门禁通过；实际效果待 CI。

- ✅ 诊断包下载未完成后，复用现有轻量 evidence artifact：只要生成 test summary，无论测试成败都上传摘要和截图；保留完整 xcresult 诊断包和全部测试门禁。仅 CI 证据可用性调整，应用架构及测试断言不变。

- 34181433617 实际结果：KMP 通过，StoreKit 8/8 通过（96.1 秒），UI Next 点击未报 AX 错误但仍未切换焦点；标题变为 Acceptance Quest500，金额保持 30。金额 TextView 实际只露出 13dp 高度。
- ✅ 下一轮只修改 XCUITest 输入定位：在键盘上方的抽屉区域滚动，要求目标输入框至少 48pt 高且完整位于键盘上方，再按真实 frame 点击并核对输入值；不依赖 Next 自动切换，不删减金额断言。生产代码、Domain/Application、Room、事件和备份均不变，复用现有 fill 方法，无新公共抽象。该修复尚未验证。

- 上一轮 `34141752940`：KMP 通过，StoreKit 8/8 全部通过（72.1 秒）；UI 完成订阅并输入任务名称，点击系统 Next 时因 XCTest AXScrollToVisible 失败而中止，尚未完成任务保存。
- ✅ 本次仅将 XCUITest 中已存在的键盘 Next 按钮点击改为其实际 frame 中心点点击，继续真实键盘交互和字段值断言。生产代码、架构依赖、Room v8、事件、备份、权限规则均未改变，静态门禁通过后启动新一轮完整验收。

### 公共仓库恢复验收

- 已通过 GitHub CLI 确认仓库为 PUBLIC。第九轮 `34126315243` 成功分配 macOS runner，KMP 测试通过，StoreKit 原生测试 7/7 全部通过，包括购买、恢复、取消、过期、撤销、pending、失败、价格与交易监听。
- UI 已实际通过引导、默认 Coffee 提醒、免费锁定、付费墙购买和 Premium 状态检查，并打开 New Quest 编辑器；失败点是测试使用 TextField 类型查询，但实际界面树中 task-title / task-reward 为 TextView。尚未通过完整 UI 验收，不发布产物。
- ✅ 下一轮仅调整 XCUITest：按稳定 identifier 查找输入控件，不假设 UIKit 元素类型；读取现值兼容 Compose 的 label。业务代码、分层、Room 和协议均未变化，静态架构门禁继续通过。该修正须实际运行后才能计为验证通过。
- 第十轮 `34129462279` KMP 通过，原生 6/7 通过：重新购买已返回，但立即读取 entitlement 得到 free，过期/撤销测试失败；UI 等待 Start Free Trial 5 秒失败，尚未到达编辑器，不能宣称输入定位修正已验证。下一轮仅将重新购买后的断言复用有界 10 秒 eventually 检查，每次读取真实 entitlement，不写入模拟权限；不改变生产业务或架构。
- ✅ UI 核验按钮等待改为 60 秒并在断言失败时将实际可访问性树写入日志，覆盖先前实际观察到的 StoreKit 核验延迟；仍要求出现可开始试用状态。生产 entitlement、金额、事务、数据库与事件保持不变，静态门禁已在下次测试前更新。
- 第十一轮 `34133563989` KMP 通过，原生 5/7 通过；退款后即时读取仍为 premium，pending 提示为 nil。UI 已再次订阅并进入编辑器，task-title 为有有效屏幕 frame 的虚拟 TextView，但 XCTest 命中点为 {-1,-1}，直接 tap 失败。
- ✅ 下一轮继续使用 10 秒有界真实 entitlement 核验覆盖过期/退款传播；输入点击仅在实际控件 frame 完整位于屏幕内时回退到中心点，并断言系统键盘出现后输入。Swift 仅增加 DEBUG 下不含交易信息的购买结果分类日志，权限判定与状态行为不变，Domain/Application/Room/事件未变化。pending 提示问题尚未定位，不预填通过。
- 第十二轮 `34138833163` KMP 通过；原生 6/7 通过（pending/失败本轮通过），组合过期/退款用例超过 180 秒。UI 已真实弹出键盘并输入，但金额被输入到仍持有焦点的标题，实际 label 为 Acceptance Quest500，Add 被键盘遮挡，未保存任务。
- ✅ 下一轮将过期重购与退款拆成两个独立 StoreKit 用例，保留全部状态断言及每用例 180 秒上限；UI 使用已观察到的系统 next 按钮切换表单焦点，输入后核对目标字段真实值，并经 Next/Done 收起键盘后再滚动保存。仅测试及报告变化，生产业务、平台权限逻辑、Room 和事件均未更改。该方案仍待实际验收，不计为通过。

### 第四轮后续静态检查

- ✅ 本轮只增加 CI/XCTest 执行时限：单测试默认 180 秒、上限 600 秒，完整 UI 场景允许 600 秒；测试步骤上限 25 分钟，保留后续诊断上传时间。禁用测试并行执行，避免 StoreKit 测试会话互相影响。
- ✅ Domain/Application、平台订阅逻辑、Room v8、备份、事件与 Android 源码均未改变；没有新增公共 API 或重复业务实现。
- 第四轮 `34107978986` 已推送并启动，Kotlin Multiplatform 测试通过；截至本次检查，StoreKit/UI 步骤运行超过 35 分钟仍无最终结果，尚不能判定卡住原因。实时日志浏览器访问被自动审批拦截，不声明任何 StoreKit/UI 断言通过。
- 本段在下一轮测试之前更新；执行时限的实际效果仍须由后续 CI 验证。
- 第四轮取消后的日志确认：应用成功启动，`testCancelledPurchaseDoesNotUnlockOrReportSuccess` 和 `testEntitlementRulesRejectUnverifiedExpiredRevokedAndWrongProducts` 两项通过；`testExpiredAndRevokedTransactionsDoNotUnlock` 从 09:58:34 UTC 到取消时 10:35:46 UTC 未结束，UI 测试尚未开始。不是编译耗时。
- ✅ 后续仅修正两个测试 target 的 StoreKit 初始化顺序：先 reset/clear，再设置 disableDialogs，防止自动购买依赖重置前的配置；过期/撤销测试新增不含交易信息的阶段日志。取消运行也上传诊断，业务及架构边界不变。初始化顺序是否为挂起根因仍待新运行验证，不提前宣称修复成功。
- 第五轮 `34112344454`：4 项原生测试通过，3 项购买相关测试在 180 秒超时；UI 在付费墙购买后等待 Premium 失败。第六轮 `34112889783`：原生 6/7 通过，包括购买/恢复、过期/退款、pending/失败、价格和交易监听；取消测试超时。UI 同样失败，下载的 xcresult 汇总为 6 passed / 2 failed / 0 skipped，失败截图显示 Processing，无系统弹窗。
- ✅ 下一轮保留全部断言和 180/600 秒 XCTest 上限，仅让取消测试调用 entitlement 核验而不加载无关商品；UI 购买成功等待由 20 秒改为 90 秒，以覆盖第六轮已观察到的数十秒 StoreKit 延迟。尚不能声明全功能验收通过，也不发布产物。
- ✅ 补充 UI 定位静态检查：`RewardsContent` 使用 LazyColumn，底部 Add New Reward 不保证已进入可访问性树。购买和重启的 Premium 状态断言改为首屏仅 Premium 可见的 Edit Specialty Coffee；后续仍实际滚动点击 Add New Reward 并创建奖励，不删减功能验收。
- 第七轮 `34116791196` 和第八轮 `34118805464` 均在分配 runner 前失败，steps 为空。第八轮 GitHub annotation 明确提示：`The job was not started because recent account payments have failed or your spending limit needs to be increased.` 因此 `5a0a29c` / `4c0cd59` 的测试修改尚未获得运行验证。需仓库所有者处理 Settings > Billing & plans 后重跑；未合并 main、未发布 v0.55。

以下为此前轮次的历史结果；2026-09-09 最终状态以本报告“最终实际结果”及 app-store-readiness.md 为准：

| 验证项 | 当前状态 |
| :--- | :--- |
| Gradle 配置与 Android common 编译 | ✅ 本轮 test、Compose instrumentation、lintDebug、assembleDebug 命令成功结束 |
| Common/JVM 单元测试 | ✅ Gradle test 成功；未变更的 Domain/Application/Sync 单测任务复用 UP-TO-DATE 结果，不声称全部重新执行 |
| Data/Room Android instrumentation | ✅ 本轮 API 33 `habitica_test_api33` 30/30 通过；包含事务迁移标记、取消后备份恢复、默认愿望与已有数据保留。 |
| App/Compose Android instrumentation | ✅ 最终代码 20/20 通过；包含免费高余额 Coffee 锁定、订阅 UI 状态和 500ms 动画回归。 |
| Android lintDebug / assembleDebug | ✅ 本轮命令成功；Lint 为 0 errors、1 条既有 OldTargetApi 警告（target 33），不是零警告。最终 APK 在 API 33 重新安装及冷启动成功，crash buffer 为空且进程存活。 |
| iOS Kotlin/Native、StoreKit 与 XCUITest | ⏳ 34185637618 的 KMP、StoreKit 8/8 通过，UI 付费墙命中失败。最新 34187777245 的 KMP、iOS 26.2 启动通过；Swift Transaction 类型歧义使 StoreKit/UI 未执行。本地 f8b9806 已限定类型，另增加 iOS SQLite 回滚测试；Git HTTPS 连接失败，尚未推送验证。完整 UI 流程从未全部通过。 |
| iOS Simulator 产物检查 | ⏳ 未执行；目标为 0.55 (19)、arm64/x86_64 通用未签名 `.app` ZIP |
| 发布产物签名与 SHA-256 | Android 已验证 0.55 (19)、com.familyquest.app、minSdk 26、targetSdk 33、APK v2 签名有效，28,399,812 bytes。SHA-256：`B6E71312A90AAFCBD5806E19861E0C1F95A5D0B365AF75BC4A551EDB2D4347D6`。iOS ZIP 尚未生成，不发布 Release。 |
| 真机/App Store archive | ⏳ 最终目标已扩展为 App Store；签名 Archive、TestFlight 和真机沙盒仍未完成，需要 Apple Developer 账户、证书和 provisioning profile。Simulator `.app` 不是 IPA。 |

测试前结论：v0.55 的架构边界、可靠 entitlement 设计、迁移兼容和跨平台职责划分全部为 ✅，且没有 Room schema、备份格式、同步协议或 Domain/Application 公共 API 变更。当前仅授权进入测试，不代表 Android/iOS 构建或发布已经通过；这些结论必须在实际门禁完成后回填。
