# E-Ink Launcher

一个面向 Android 电子书阅读器的极简桌面。它把桌面当作一张静止的目录页：只显示用户选择的应用，并尽量避免动画、定时轮询和后台工作带来的刷新与耗电。

## 设计原则

- 主屏使用纯白背景与黑色文字，不使用壁纸、阴影、渐变或彩色图标。
- 仅在左侧居中显示用户选择的应用，其余应用不会出现在主屏。
- 隐藏系统顶部状态栏，在右上角显示时间与电量；时间不显示秒。
- 不使用页面转场、列表动画、涟漪或平滑滚动。
- 不申请网络权限，也不运行后台服务、前台服务或周期任务。
- 时间和电量只在桌面可见期间根据系统事件更新，不通过高频计时器轮询。

> 系统导航栏、其他应用自身的动画，以及部分厂商系统级转场不受普通 Launcher 完全控制。电子墨水的全刷/局刷接口也没有统一 Android 标准，当前版本不调用特定厂商 SDK。

## 使用方法

### 设置默认桌面

1. 安装并打开 E-Ink Launcher。
2. 首次进入会直接显示管理页；选择希望显示在主屏上的应用，然后点击 **完成**。
3. 在系统弹窗中选择 **E-Ink Launcher**，并设为默认主屏应用。
4. 如果没有出现弹窗，可以在管理页点击 **选择默认桌面**，或进入 **设置 → 应用 → 默认应用 → 主屏应用** 手动选择。不同厂商的菜单名称可能略有不同。

### 修改应用列表

- 长按主屏空白区域，进入应用管理页。
- 也可以长按右上角的时间/电量区域，作为固定的备用入口。
- 带实体 Menu 键的设备也可以按 Menu 键进入管理页。
- 在管理页添加、移除或调整应用顺序，完成后返回主屏。
- 当列表为空时，主屏会保留添加应用的入口。

## 从 GitHub 安装

1. 打开本仓库的 **Releases** 页面，选择最新版本。
2. 下载名称形如 `EInkLauncher-vX.Y.Z.apk` 的文件。
3. 如系统拦截安装，请仅为当前浏览器或文件管理器开启“允许安装未知应用”。
4. 安装完成后，按照上面的步骤将它设置为默认桌面。
5. 可使用同一 Release 中的 `.sha256` 文件核对下载内容。

GitHub Release 中的 APK 使用项目固定的 release 证书签名，因此后续版本可以保留数据并覆盖安装。请只从本仓库的 Releases 页面下载安装包。

## 兼容范围

- 最低系统版本：Android 8.0（API 26）
- 目标系统版本：Android 13（API 33）
- 编译 SDK：Android API 35

项目用于 GitHub 直接分发，不以 Google Play 上架为目标。

## 本地构建

需要 JDK 17 和 Android SDK：

```sh
./gradlew lintDebug testDebugUnitTest assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 发布新版本

普通提交和 Pull Request 会运行 Android Lint、JVM 单元测试、构建并保存 debug APK。推送一个以 `v` 开头的 tag（例如 `v0.1.0`）后，GitHub Actions 还会构建 release APK，执行 zipalign、使用固定证书签名并通过 `apksigner verify` 检查，再创建对应的 GitHub Release，并上传 APK 与 SHA-256 校验文件。

tag 中去掉 `v` 的部分会写入 APK 的 `versionName`，GitHub Actions 的递增运行编号会写入 `versionCode`，保证后续版本可以正常覆盖升级。

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
