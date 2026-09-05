# QuestAndReward 架构规范

版本：1.0  
状态：生效  
适用范围：`QuestAndReward` Android/iOS 客户端、后续 NAS 同步网关及共享协议  
优先级：本文件是项目不可协商的最高工程约束。任何实现、评审和交付均不得绕过；确有冲突时，必须先修订本规范并记录架构决策（ADR），不得在代码中静默例外。

## 0. 平台适用原则

本项目当前交付物是基于 Kotlin Multiplatform 的本地优先 Android/iOS 应用，并未运行 Spring、HTTP 服务或远程数据库。规范中的服务端术语按以下方式执行：

| 能力 | Android/iOS 客户端 | NAS 同步网关/服务端 |
| --- | --- | --- |
| 健康探针 | 不暴露网络端口；以数据库可打开、同步状态和可诊断错误作为应用内健康状态 | 必须提供 `/actuator/health`、readiness 和 liveness |
| 链路追踪 | 每次应用命令生成 `traceId`，贯穿业务错误与持久化事件；不得记录敏感数据 | 必须通过 MDC 注入和传播 `traceId` |
| 数据库迁移 | Room KMP `Migration`，禁止破坏性迁移和 `fallbackToDestructiveMigration` | Flyway 或 Liquibase |
| 熔断/降级 | 仅对未来网络 `SyncTransport` 实现启用；离线时写入 outbox 并继续提供本地功能 | 所有外部依赖必须启用熔断和显式降级 |
| API 兼容 | 领域事件 schema 只能增量演进，破坏性变更提升 `schemaVersion` | HTTP API 按 `/api/vN` 演进并双跑至少 2 个周期 |

“不适用”只能用于对应能力确实不存在的情况，不得用来掩盖未实现的外部调用保护。新增网络、服务端或远程数据库能力时，相关条款自动转为强制适用。

## 1. 可靠性工程（Reliability）

- **超时与熔断**：所有 RPC/HTTP/DB 操作必须设置超时（Timeout）。外部依赖必须配置熔断器（CircuitBreaker）及降级策略（Fallback），返回 Mock 或缓存数据，严禁直接抛出未捕获异常导致系统雪崩。
- **重试幂等**：若启用重试，必须使用指数退避（Exponential Backoff），且最大重试次数小于或等于 3。所有写操作接口必须支持幂等性（Idempotent Key）。
- **健康探针**：核心业务模块必须暴露 `/actuator/health` 及就绪/存活探针。
- 客户端本地命令必须受协程超时保护；超时或失败必须映射为可展示的业务错误，禁止异常越过 ViewModel 边界导致崩溃。
- 本地写入必须在单一 Room 事务内完成投影、账本和 outbox 更新；事件 ID 或业务幂等键必须由调用方传入或稳定生成。
- 离线降级数据必须是真实缓存或本地投影；Mock 仅允许测试环境使用，生产环境严禁以虚假成功掩盖故障。

## 2. 可扩展性（Scalability）

- **依赖倒置**：核心业务逻辑（Domain/UseCase）只能依赖抽象接口，禁止直接依赖具体第三方实现（如具体 Redis/MySQL 客户端）。
- **策略模式**：对于未来可能变更的算法（如不同国家的税率计算、不同支付渠道），必须定义为 Interface，通过 Spring Map/工厂模式动态路由，严禁写大量的 if-else 修改核心类。
- 客户端不使用 Spring 时，以构造器注入和显式 Map/工厂完成策略路由；Domain 与 Application 模块不得依赖 Android、iOS、Room、JSON 或具体传输实现。
- 只有存在两个实现、明确的变化轴或隔离第三方依赖时才增加抽象，禁止为“可能有一天”创建空洞接口。

## 3. 可读性与可维护性（Readability）

- **日志链路**：必须使用 MDC 注入 `traceId`，确保全链路日志可追踪。禁止使用 `System.out.println` 或 `e.printStackTrace()`。
- **统一异常**：业务异常必须继承自定义 `BizException`，并使用枚举 `ErrorCodeEnum`（格式：`{模块前缀}-{4位数字}`），异常信息必须对用户友好。
- 客户端不引入服务端日志框架；命令上下文中的 `traceId` 是等价追踪载体。未来网关必须使用 MDC，且跨 HTTP/事件边界传播同一个 `traceId`。
- 禁止向 UI 暴露数据库异常、堆栈或内部实现信息。未知异常必须在 Application 边界转换为统一错误码，并保留 `traceId` 供诊断。
- 核心配置注释必须解释设计原因（Why），不得复述代码行为。

## 4. 模块分层与边界（Decoupling & Layering）

- **严格遵守四层单向依赖**：`Web/Controller` -> `Application/Service` -> `Domain/Entity` <- `Infrastructure/Mapper`。严禁跨层调用（如 Controller 直接调用 Mapper）。
- **跨模块通信**：不同业务域（如 Order 与 User）之间，禁止直接注入对方的 Service 实现。必须通过 `Application Event`（领域事件）或 `OpenFeign API Facade` 进行解耦。
- Android/iOS 映射为 `Compose/ViewModel -> Application Service -> Domain <- Data/Room`。共享 Compose 和 ViewModel 不得直接调用 DAO、Room Repository 实现或 `SyncTransport` 实现。
- `app` 仅在 Composition Root 允许装配 Infrastructure 实现；业务调用必须经过 Application Service。
- `commonMain` 保存跨平台业务与 UI；`androidMain`、`iosMain` 仅保存数据库路径、偏好、文件选择/文件访问和平台启动入口。平台源集不得复制领域规则。
- 家庭角色、任务、奖励、账本和同步协议保持显式边界；跨边界副作用使用领域事件/outbox，禁止隐式双写。

## 5. 杜绝重复造轮子（DRY - Don't Repeat Yourself）

- **强制检索机制**：在创建任何新的 Util、Helper、Converter、Client 之前，必须先扫描 `common`、`shared-kernel` 或 `base` 模块。
- **复用原则**：若已有类功能覆盖度大于或等于 70%，必须进行扩展（继承/策略增强），绝对禁止推翻重写。若多个模块出现相同逻辑片段，必须立即抽取为共享组件。
- 本项目当前没有 `common`、`shared-kernel` 或 `base` 模块；新增通用能力前仍必须检索全仓库并在自检报告中记录结果。
- 优先使用 Kotlin、AndroidX、Room、kotlinx.serialization 等成熟 API；禁止创建仅包装单个标准库调用的 `Util`/`Helper`。
- 共享组件只能包含稳定且无业务归属的能力；不得把业务规则堆入“万能 common”。

## 6. 长期演进策略（Long-term Evolution）

- **数据库版本管理**：所有 DDL（表结构变更）必须通过 Flyway/Liquibase 脚本管理，严禁手动连库修改。
- **API 版本兼容**：对外接口只允许增量（增加 Optional 字段），禁止减量或改类型。破坏性变更必须发布新版本（如 `/api/v2/...`），并保证旧版本至少 2 个周期的双跑维护。
- Room KMP schema 变更必须提升数据库版本、提供显式 `Migration` 并导出 schema；禁止破坏性迁移。Android 升级必须继续验证既有 v1→当前版本迁移；未来网关数据库严格使用 Flyway/Liquibase。
- 领域事件必须包含稳定事件类型与 schema 版本。消费者必须忽略未知 Optional 字段；破坏性变更使用新事件版本，并保留旧版本读取能力至少 2 个发布周期。
- SVN/NAS 只保存不可变事件或快照，不得同步 SQLite 文件。合并必须基于事件 ID、设备 ID 和幂等键，不依赖文件时间戳。

## 7. 交付门禁

任何测试或 APK 打包之前，必须按顺序完成：

1. 架构边界和依赖方向静态审查。
2. 可靠性、幂等性、事务一致性和异常边界审查。
3. `common`、`shared-kernel`、`base` 以及全仓重复实现检索。
4. 生成或更新 `docs/architecture-compliance-report.md`。
5. 报告中不得存在未处理的红色项；不适用项必须给出可验证原因和未来触发条件。
6. 通过上述自检后，方可执行自动化测试、Lint 和 APK 构建。

最低自动化门禁（可在 Windows/Linux 执行）：

```powershell
.\gradlew.bat test lintDebug assembleDebug
```

Apple 平台门禁必须在 macOS/Xcode 执行：

```bash
./gradlew iosSimulatorArm64Test
xcodebuild test -project iosApp/iosApp.xcodeproj -scheme QuestAndReward \
  -destination 'platform=iOS Simulator,name=iPhone 16'
xcodebuild archive -project iosApp/iosApp.xcodeproj -scheme QuestAndReward \
  -destination 'generic/platform=iOS'
```

生产发布还必须增加数据库迁移测试、事件兼容性测试、Android 与 iOS 真机/模拟器 UI 测试和各平台 Release 签名验证。
