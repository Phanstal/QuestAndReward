# QuestAndReward macOS / iOS 编译指南

本文用于在 macOS 上编译、测试、签名和归档 QuestAndReward iOS 应用。项目使用 Kotlin Multiplatform、Compose Multiplatform 和 Room KMP；业务规则、数据访问、ViewModel 与界面由 Android/iOS 共用，`iosApp` 只负责 SwiftUI 宿主和 Apple 工程配置。

## 1. 环境要求

- Apple Silicon 或 Intel Mac
- macOS 14 或更高版本
- Xcode 16.x，并在 Xcode Settings > Platforms 中安装目标 iOS Simulator runtime
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
4. 确认 Deployment Target 为 iOS 15.0 或更高。

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

## 6. iOS 功能验收

模拟器首次启动后按顺序检查：

1. Landing 的 `Level Up Your Life` 保持单行，完成首次引导后进入主界面。
2. Quests、Store、Rewards、Stats 四页均可切换，编辑抽屉和确认弹窗可操作。
3. 新增、编辑、完成、软删除任务；每日/每周/每月周期和月度次数行为与 Android 一致。
4. 购买、使用、出售奖励，心愿 Deposit、兑换和统计更新正确。
5. 杀掉并重启应用后数据仍存在，说明 Room/SQLite 与 UserDefaults 正常工作。
6. 在 Finder 或 Files 中确认应用 Documents 可见。`Export Backup` 写入 `quest-backup-*.json`；`Import Data` 导入 Documents 中按文件名排序最新的同名备份。
7. 在 Xcode Debug navigator 和设备日志中确认没有未捕获异常或数据库迁移失败。

iOS 与 Android 使用相同的 Compose 页面和业务服务，因此无需维护第二套 UI 行为。SwiftUI 的 `ContentView` 仅承载共享 `ComposeUIViewController`。

## 7. 真机与 Release 构建

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

## 8. Android 同步验证

在同一提交上执行 Android 门禁，确保跨平台改动没有破坏 APK：

```bash
./gradlew test lintDebug assembleDebug
./gradlew :data:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

第二条命令需要已启动的 Android API 33 或更高版本模拟器。APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 9. 常见问题

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

## 10. 发布前记录

每次 iOS 发布至少记录：Git commit、`MARKETING_VERSION`、`CURRENT_PROJECT_VERSION`、Xcode 版本、目标 iOS 版本、测试设备、archive 是否成功、导出方式和 IPA SHA-256。macOS 实测结果应回填到 `docs/architecture-compliance-report.md`，不能用 Windows 上的 Android 结果代替。
