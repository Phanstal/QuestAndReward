# AI 架构合规性自检报告

检查日期：2026-09-07
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

本节只记录实际执行结果，当前不预填通过：

| 验证项 | 当前状态 |
| :--- | :--- |
| Gradle 配置与 Android common 编译 | ✅ 本轮 test、Compose instrumentation、lintDebug、assembleDebug 命令成功结束 |
| Common/JVM 单元测试 | ✅ Gradle test 成功；未变更的 Domain/Application/Sync 单测任务复用 UP-TO-DATE 结果，不声称全部重新执行 |
| Data/Room Android instrumentation | ✅ 本轮 API 33 `habitica_test_api33` 30/30 通过；包含事务迁移标记、取消后备份恢复、默认愿望与已有数据保留。 |
| App/Compose Android instrumentation | ✅ 最终代码 20/20 通过；包含免费高余额 Coffee 锁定、订阅 UI 状态和 500ms 动画回归。 |
| Android lintDebug / assembleDebug | ✅ 包含可访问性测试标识的最终代码已通过，Lint XML 无 issue。API 33 覆盖安装和冷启动成功；实际点击通过引导、默认 Coffee 提醒、每日完成 1/1、每周/每月切换、免费 Locked、演示解锁、库存和 Stats，crash buffer 为空且进程存活。 |
| iOS Kotlin/Native、StoreKit 与 XCUITest | ⏳ 启动配置修复已在第四至六轮生效，KMP 测试通过。最近实际执行的第六轮 xcresult：原生 6/7 通过，取消测试超时，UI 购买状态断言失败，共 6 passed / 2 failed / 0 skipped。后续测试修改已提交；第七、八轮因 GitHub 账户计费/支出上限限制未启动，不能计作测试执行或通过。 |
| iOS Simulator 产物检查 | ⏳ 未执行；目标为 0.55 (19)、arm64/x86_64 通用未签名 `.app` ZIP |
| 发布产物签名与 SHA-256 | Android 已验证 0.55 (19)、com.familyquest.app、minSdk 26、targetSdk 33、APK v2 签名有效，28,394,992 bytes。SHA-256：`9B83AC7FCFFBCDCDB721DBA6FEA87F2B91D7D3DAFF8B2411EECAC867766C5241`。iOS ZIP 尚未生成，不发布 Release。 |
| 真机/App Store archive | 不适用 | 本轮目标是构建和测试；当前 iOS 产物明确为 Simulator `.app`，不是 IPA。真机或 App Store 发布仍需 Apple Developer 账户、证书和 provisioning profile。 |

测试前结论：v0.55 的架构边界、可靠 entitlement 设计、迁移兼容和跨平台职责划分全部为 ✅，且没有 Room schema、备份格式、同步协议或 Domain/Application 公共 API 变更。当前仅授权进入测试，不代表 Android/iOS 构建或发布已经通过；这些结论必须在实际门禁完成后回填。
