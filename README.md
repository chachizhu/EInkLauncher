# E-Ink Launcher

一个面向 Android 电子书阅读器的极简桌面。它把桌面当作一张静止的目录页：只显示用户选择的应用，并尽量避免动画、定时轮询和后台工作带来的刷新与耗电。

## 设计原则

- 主屏使用纯白背景与黑色文字，不使用壁纸、阴影或渐变；应用图标统一转为灰度显示，避免彩色在墨水屏上发灰发脏。
- 仅在左上方显示用户选择的应用（最多 12 个），其余应用不会出现在主屏。
- 隐藏系统顶部状态栏：右上角单行显示日期（可选）、固定 24 小时制时间、Wi-Fi 状态图标与电量；时间不显示秒。
- 不使用页面转场、列表动画、涟漪或平滑滚动。
- 只有用户在管理页手动检查更新或确认下载时才联网；不在启动时、定时或后台检查更新。
- 不运行后台服务、前台服务或周期任务。
- 时间、日期、电量和 Wi-Fi 状态只在桌面可见期间根据系统事件更新，不通过高频计时器轮询。

> 系统导航栏、其他应用自身的动画，以及部分厂商系统级转场不受普通 Launcher 完全控制。电子墨水的全刷/局刷接口也没有统一 Android 标准，当前版本不调用特定厂商 SDK。

## 使用方法

### 设置默认桌面

1. 安装并打开 E-Ink Launcher。
2. 首次进入会直接显示管理页；选择希望显示在主屏上的应用，然后点击 **完成**。
3. 在系统弹窗中选择 **E-Ink Launcher**，并设为默认主屏应用。
4. 如果没有出现弹窗，可以在管理页点击 **选择默认桌面**，或进入 **设置 → 应用 → 默认应用 → 主屏应用** 手动选择。不同厂商的菜单名称可能略有不同。

### 修改应用列表

- 长按主屏右下角的设置（齿轮）按钮，进入应用管理页。
- 带实体 Menu 键的设备也可以按 Menu 键进入管理页。
- 在管理页添加、移除或调整应用顺序，完成后返回主屏。
- 轻触“已选择”列表中的应用名称，可以设置仅用于主屏显示的简短别名。
- 在管理页选择“紧凑”“舒适”或“大字”显示预设，字号、行高、字重和间隔会一起调整并自动保存；“大字”的字重与“舒适”一致，间隔按字号同比例放大。
- 在管理页的“主屏时钟”中选择仅显示 24 小时制时间，或同时显示日期和时间；电量始终显示。
- 当当前字号和屏幕高度无法容纳全部应用时，主屏会自动使用无动画静态分页；可使用屏幕按钮或 Page Up/Page Down 翻页。
- 当列表为空时，主屏会保留添加应用的入口。

## 从 GitHub 安装

### 应用内手动更新

1. 长按主屏右下角的设置（齿轮）按钮进入设置，然后点击 **检查更新**。
2. 检查只会在这次操作后连接本仓库的 GitHub Release；应用不会在启动时或后台自动检查、轮询或下载。
3. 如有新版本，确认下载后，应用会校验 APK 的 SHA-256、包名、版本与签名，再唤起 Android 系统安装界面。
4. Android 可能要求为 **E-Ink Launcher** 开启“允许安装未知应用”。授权后返回管理页，再次点击 **安装更新**，最后在系统界面确认安装。

应用不会静默安装。下载与安装均由用户当次操作触发，最终安装确认和未知来源授权由 Android 系统管理。

### 通过浏览器安装

1. 打开本仓库的 [Releases](https://github.com/chachizhu/EInkLauncher/releases) 页面，选择最新版本。
2. 下载名称形如 `EInkLauncher-vX.Y.Z.apk` 的文件。
3. 如系统拦截安装，请仅为当前浏览器或文件管理器开启“允许安装未知应用”。
4. 安装完成后，按照上面的步骤将它设置为默认桌面。
5. 可使用同一 Release 中的 `.sha256` 文件核对下载内容。

GitHub Release 中的 APK 使用项目固定的 release 证书签名，因此后续版本可以保留数据并覆盖安装。请只从本仓库的 Releases 页面下载安装包。

发布证书 SHA-256 指纹：

```text
52:13:03:92:02:AE:7C:F9:7B:52:0E:A8:9E:02:18:2A:89:EC:8B:74:AF:E1:F9:14:FA:95:EE:17:F8:18:9E:A7
```

这是项目唯一认可的直装 APK 身份。CI 会同时核对签名后 APK 的实际证书、工作流内的固定信任值和本页指纹；任一不一致都会中止发布。`.sha256` 文件可用于发现下载损坏，但它与 APK 来自同一 Release，不能代替签名证书验证。

发布工作流拒绝覆盖或增补同名 Release。正式分发前，仓库管理员还必须在 **Settings → General → Releases** 中启用 **Immutable releases**，由 GitHub 对启用后新发布 Release 的 tag 和资产实施服务端保护；工作流检查不能替代这项仓库设置。发现问题时会从已知良好的提交构建、使用同一证书签名，并发布一个版本号和 `versionCode` 都更高的修复版本。完整流程见 [直装 APK 发布与恢复手册](docs/RELEASE.md)。

## 兼容范围

- 最低系统版本：Android 5.0（API 21）
- 目标系统版本：Android 13（API 33）
- 编译 SDK：Android API 35

项目用于 GitHub 直接分发，不以 Google Play 上架为目标。

## 隐私

- 应用会在设备本地读取可启动应用列表，用于管理页展示；所选组件、应用别名、显示预设、时钟显示模式和首次运行状态只保存在应用私有偏好中，并明确禁止系统备份或设备迁移。
- 没有账号、广告、分析 SDK、崩溃上报或云同步，也不会把应用列表上传到服务器。
- 只有用户主动检查或下载更新时才会访问 GitHub。GitHub 及其发布文件 CDN 会像普通 HTTPS 服务一样接收 IP 地址、请求时间和 User-Agent 等网络元数据。
- 下载的 APK 暂存在应用私有缓存中，供系统安装器读取；它可能保留到后续下载覆盖、系统清理缓存或用户清除应用缓存为止。
- `INTERNET` 和“安装未知应用”能力仅用于上述手动更新流程。最终安装与授权始终由 Android 系统确认；不使用应用内更新的用户无需授予未知来源安装权限。

其他应用在被启动后的数据处理不属于 E-Ink Launcher。更完整的安全边界、受支持版本与漏洞报告方式见 [安全政策](SECURITY.md)。

## 支持范围

- 仅支持本仓库 GitHub Releases 发布、且证书指纹与上文一致的最新稳定版；第三方重签名、修改版和其他来源 APK 不在支持范围内。
- Android 5.0（API 21）是最低兼容版本。不同电子书厂商对默认桌面、系统安装器、导航栏和电子墨水刷新策略有定制，未声明的厂商专用刷新模式不作保证。
- 遇到普通问题可提交 GitHub Issue，并附 Android 版本、设备型号、应用版本和可复现步骤。请不要在公开 Issue 中粘贴密码、私钥、完整设备日志或尚未公开的安全漏洞。

## 本地构建

需要 JDK 17 和 Android SDK：

```sh
./gradlew lintDebug testDebugUnitTest assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 发布新版本

普通提交和 Pull Request 会运行 Android Lint、JVM 单元测试、构建并保存 debug APK。推送一个以 `v` 开头的 tag（例如 `v0.1.0`）后，GitHub Actions 还会构建 release APK，执行 zipalign、使用固定证书签名，并把 `apksigner` 报告的证书 SHA-256 与上文固定指纹硬比较。验证通过后才会创建对应的 GitHub Release，并上传 APK 与 SHA-256 校验文件。

如果同名 Release 已存在，任务会明确失败，不会覆盖或增补既有资产。修复发布必须使用更高的新版本；不得移动已发布 tag、替换 APK 或把旧 APK 重新标成新版本。发布前检查、失败恢复和回滚方式见 [docs/RELEASE.md](docs/RELEASE.md)。

tag 中去掉 `v` 的部分会写入 APK 的 `versionName`。`versionCode` 由 semver 确定性生成：`major × 1,000,000 + minor × 1,000 + patch`。工作流要求 `major <= 2146`、`minor/patch <= 999`，拒绝 `v0.0.0`，并在构建前确认新版本严格高于仓库中所有既有稳定 Release；因此结果不依赖可能因工作流迁移而重新开始的 `GITHUB_RUN_NUMBER`。

### 一次性配置发布签名

发布前，在可信的本地环境生成并妥善备份 release keystore。不要把 keystore、密码或 `keystore.properties` 提交到仓库。可以使用 JDK 17 自带的 `keytool`：

```sh
keytool -genkeypair -v \
  -keystore eink-launcher-release.jks \
  -alias einklauncher \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

然后进入 GitHub 仓库的 **Settings → Secrets and variables → Actions**，添加以下 Repository secrets：

- `ANDROID_KEYSTORE_BASE64`：keystore 文件的 Base64 内容。在 macOS 可执行 `base64 -i eink-launcher-release.jks | pbcopy`。
- `ANDROID_KEYSTORE_PASSWORD`：keystore 密码。
- `ANDROID_KEY_ALIAS`：生成证书时使用的 alias，例如 `einklauncher`。
- `ANDROID_KEY_PASSWORD`：私钥密码。

四项 secret 缺少任何一项，tag 任务都会明确失败且不会创建 Release。首次发布后必须永久保留同一份 keystore、alias 和密码；遗失或替换证书会导致已安装用户无法覆盖升级。

keystore 不应只存在于 GitHub Secrets。至少保留两份相互独立、加密且经过恢复验证的离线备份，密码与 keystore 分开保管，并限制能够推送 `v*` tag、读取发布 Secret 或批准发布环境的人员。当前版本不支持签名密钥轮换；任何轮换计划都必须先设计并验证兼容迁移，不能直接替换 Secret。
