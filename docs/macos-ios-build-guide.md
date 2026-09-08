# QuestAndReward macOS / iOS 编译指南

本文用于在 macOS 上编译、测试、签名和归档 QuestAndReward iOS 应用。项目使用 Kotlin Multiplatform、Compose Multiplatform 和 Room KMP；业务规则、数据访问、ViewModel 与界面由 Android/iOS 共用，`iosApp` 只负责 SwiftUI 宿主和 Apple 工程配置。

## 1. 环境要求

- Apple Silicon 或 Intel Mac
- macOS 14 或更高版本
- Xcode 26.2（当前 CI 版本），并在 Xcode Settings > Components 中安装目标 iOS 26 Simulator runtime；上传要求至少 Xcode 26
- Xcode Command Line Tools：`xcode-select --install`
- JDK 17。可使用 Android Studio 自带 JBR，或安装 Temurin 17
- Git
- 如同时验证 Android：Android Studio、Android SDK 35，以及 Build Tools 34.0.0

确认工具：

```bash
xcodebuild -version
java -version
git --version
```

如系统默认 Java 不是 17，可在当前终端设置：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

项目不使用 CocoaPods，不需要执行 `pod install`。

## 2. 获取项目

```bash
git clone https://github.com/Phanstal/QuestAndReward.git
cd QuestAndReward
chmod +x gradlew
```

首次构建会下载 Gradle、Kotlin、Compose、Room 和 SQLite 依赖，需要能够访问项目配置的 Maven 仓库。

## 3. 先验证共享代码

列出 Gradle 工程并运行共享单元测试：

```bash
./gradlew projects
./gradlew iosSimulatorArm64Test
```

Apple Silicon 使用 `iosSimulatorArm64Test`。Intel Mac 可改用：

```bash
./gradlew iosX64Test
```

测试覆盖 Domain、Application 和 Sync 的周期、奖励、备份与事件兼容规则。Room 迁移的 instrumentation 测试当前位于 Android 源集，仍需在 Android 模拟器上执行。

## 4. 配置签名

用 Xcode 打开：

```bash
open iosApp/iosApp.xcodeproj
```

在 target `QuestAndReward` 的 Signing & Capabilities 中：

1. 勾选 Automatically manage signing。
2. 选择自己的 Apple Developer Team。
3. 将 Bundle Identifier `com.phanstal.questandreward` 改为团队名下唯一值；若该标识可用则无需修改。
4. 确认 Deployment Target 为 iOS 15.0 或更高。保留 `Info.plist` 中 `CADisableMinimumFrameDurationOnPhone = YES`；Compose 在 iPhone 启动时检查该项，缺失会导致运行期崩溃。

Apple Team、证书、私钥和 provisioning profile 不应提交到仓库。个人免费 Team 可以安装到已连接设备，但不能发布到 App Store Connect。

## 5. 模拟器编译与运行

先查看本机可用设备：

```bash
xcrun simctl list devices available
```

将下列设备名替换为实际存在的 iPhone 模拟器：

```bash
xcodebuild build \
  -project iosApp/iosApp.xcodeproj \
  -scheme QuestAndReward \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

也可以在 Xcode 中选择 `QuestAndReward` scheme 和任意 iPhone 模拟器后按 Command-R。Xcode 的 Build Phase 会自动调用：

```bash
./gradlew :app:embedAndSignAppleFrameworkForXcode
```

该任务根据 Xcode 提供的 `SDK_NAME`、`ARCHS` 和 `CONFIGURATION` 构建静态 `QuestAndReward.framework`，无需手工复制 framework。

## 6. StoreKit 2 模拟订阅

仓库已提供 `iosApp/QuestAndReward.storekit`，其中只有一个自动续订产品：

```text
product id=quest_reward_monthly
price=$1.99/month
introductory offer=7 days free
```

共享 `QuestAndReward` scheme 的 Run Action 已绑定此文件。用 Xcode 打开工程后，可在 Product > Scheme > Edit Scheme > Run > Options 确认 StoreKit Configuration 选中 `QuestAndReward.storekit`。不要在模拟器测试中选择 `None`，否则 `Product.products` 无法加载本地商品。

运行应用并打开 Premium 付费墙，点击 `Start Free Trial`。交易成功后 Premium 应立即解锁；杀掉应用并重启，`Transaction.currentEntitlements` 应恢复权限。点击 `Restore Purchases` 会调用 `AppStore.sync()` 并重新核验，不会读取本地 Boolean。

Xcode 的 Debug > StoreKit > Manage Transactions 可检查、退款或删除测试交易。也可停止应用后使用 Manage Transactions 清空全部交易，再次启动应回到免费态。取消、Ask to Buy pending、失败、未验证、已过期和已撤销交易都不能解锁。

仓库内原生测试使用 `SKTestSession`，无需 App Store Connect 账户：

测试保持 iOS 15 部署目标，创建测试交易使用 Apple 的 `buyProduct(productIdentifier:)`（iOS 14 起可用），交易标识从 `allTransactions()` 读取；不要改成参数名为 `identifier` 的新异步接口，后者要求 iOS 17。

```bash
xcodebuild test \
  -project iosApp/iosApp.xcodeproj \
  -scheme QuestAndReward \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -resultBundlePath build/QuestAndRewardTests.xcresult
```

原生测试覆盖首次免费、购买、恢复、管理器重建后的 entitlement、过期/退款降级、pending/失败保持锁定及取消结果处理。XCUITest 还包含默认 Coffee、四页导航、免费购买锁定、订阅后重启保持、任务与奖励编辑、兑换、使用、70% 出售，以及导出、重置和导入恢复。实际通过情况以架构自检报告中的 CI 结果为准，不以测试源码存在代替验收通过。

商品查询配置 15 秒调用方超时与失败后 30 秒冷却；资格、权益和恢复请求同样采用 15 秒一次性完成竞速，忽略 SDK 迟到结果，不自动重试或伪造购买成功。系统购买确认由 StoreKit 和用户控制。启动、前台恢复和交易更新均核验 entitlement，并按已验证到期时间安排再次核验。核验超时只保留尚未到期的已验证结果，否则保持锁定并显示错误。订阅没有到期时间、被撤销、被升级或签名未验证时均不授予权限。

真实 App Store 购买还需要在 App Store Connect 创建同一个 `quest_reward_monthly` 自动续订产品，设置月费和 7 天免费试用，并完成 Paid Applications Agreement、税务、银行信息、签名和审核。仓库中的 `.storekit` 文件只服务本地测试，不能代替 App Store Connect 配置。

## 7. iOS 功能验收

模拟器首次启动后按顺序检查：

1. Landing 的 `Level Up Your Life` 保持单行，完成首次引导后进入主界面。
2. Quests、Store、Rewards、Stats 四页均可切换，编辑抽屉和确认弹窗可操作。
3. 新增、编辑、完成、软删除任务；每日/每周/每月周期和月度次数行为与 Android 一致。
4. 免费态余额即使高于 Coffee 价格也保持 `Locked`；本地 StoreKit 购买后才可购买、编辑和新增。
5. 购买、使用、出售奖励，心愿 Deposit、兑换和统计更新正确。
6. 杀掉并重启应用后 Room 数据仍存在，Premium 由 StoreKit entitlement 恢复。
7. 在 Finder 或 Files 中确认应用 Documents 可见。`Export Backup` 写入 `quest-backup-*.json`；`Import Data` 打开系统 JSON 文件选择器，选择后执行完整校验与原子恢复，取消选择不修改数据。
8. 在 Xcode Debug navigator 和设备日志中确认没有未捕获异常或数据库迁移失败。

iOS 与 Android 使用相同的 Compose 页面和业务服务，因此无需维护第二套 UI 行为。SwiftUI 的 `ContentView` 仅承载共享 `ComposeUIViewController`。

## 8. 真机与 Release 构建

连接已信任的 iPhone，在 Xcode 中选择设备并运行一次 Debug。命令行无签名编译可用于排除源码问题：

```bash
xcodebuild build \
  -project iosApp/iosApp.xcodeproj \
  -scheme QuestAndReward \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  CODE_SIGNING_ALLOWED=NO
```

正式归档需要已配置的 Team、Distribution 证书和 App ID：

```bash
xcodebuild archive \
  -project iosApp/iosApp.xcodeproj \
  -scheme QuestAndReward \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath build/QuestAndReward.xcarchive
```

归档成功后可在 Xcode Organizer 中执行 Distribute App。若需命令行导出 IPA，先由 Xcode 为目标发布方式生成 `ExportOptions.plist`，再执行：

```bash
xcodebuild -exportArchive \
  -archivePath build/QuestAndReward.xcarchive \
  -exportPath build/ios-release \
  -exportOptionsPlist ExportOptions.plist
```

`ExportOptions.plist` 依赖团队、发布渠道和签名方式，不应在没有真实账号配置时编造或提交通用版本。

## 9. Android 同步验证

在同一提交上执行 Android 门禁，确保跨平台改动没有破坏 APK：

```bash
./gradlew test lintDebug assembleDebug
./gradlew :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

第二条命令需要已启动的 Android API 33 或更高版本模拟器。APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 10. 常见问题

### Xcode 找不到 QuestAndReward framework

先确认从 `iosApp/iosApp.xcodeproj` 打开工程，而不是单独打开 Swift 文件。清理 Derived Data 后重新构建：

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/QuestAndReward-*
./gradlew --stop
```

然后在 Xcode 中再次 Build。若 Gradle 阶段失败，直接在项目根目录运行 `./gradlew :app:linkDebugFrameworkIosSimulatorArm64 --stacktrace` 查看 Kotlin/Native 错误。

### JDK 版本错误

出现 `Unsupported class file` 或 Gradle JVM 不匹配时，重新设置 JDK 17：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew --version
```

### 签名失败

模拟器不需要发布证书。真机或 archive 失败时，在 Xcode 的 Signing & Capabilities 中确认 Team、唯一 Bundle Identifier 和证书状态；不要通过修改共享 Gradle 代码绕过 Apple 签名。

### Kotlin/Native 缓存异常

```bash
./gradlew --stop
rm -rf .kotlin app/build data/build domain/build application/build sync/build
./gradlew iosSimulatorArm64Test --stacktrace
```

只删除项目内生成目录，不删除用户主目录中的全局 Gradle 或 Xcode 数据。

## 11. 发布前记录

每次 iOS 发布至少记录：Git commit、`MARKETING_VERSION`、`CURRENT_PROJECT_VERSION`、Xcode 版本、目标 iOS 版本、测试设备、archive 是否成功、导出方式和 IPA SHA-256。macOS 实测结果应回填到 `docs/architecture-compliance-report.md`，不能用 Windows 上的 Android 结果代替。
## 12. 2026-09 上架工具链补充

上传 App Store Connect 当前要求 Xcode 26+ 和 iOS 26 SDK。CI 固定选择 Xcode 26.2（macos-15 runner 已安装），不改变 iOS 15 最低部署版本。模拟器测试之后增加 `xcodebuild archive -configuration Release -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO` 编译门禁；该无签名 Archive 不能直接上传 TestFlight，签名验证仍需开发者账号。

付费墙从 StoreKit 读取试用资格，不符合资格时显示 Subscribe；价格使用本地化商品价格。PrivacyInfo.xcprivacy 随应用打包，正式 Archive 仍需核对依赖使用的 required-reason API。隐私政策位于 docs/privacy-policy.md，应用入口固定到已发布政策提交的永久链接；政策变更时应同步更新入口版本，并由所有者核对 App Store Connect 的隐私披露与实际数据处理一致。
