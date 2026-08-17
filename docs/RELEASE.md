# 直装 APK 发布与恢复手册

本文适用于 E-Ink Launcher 的 GitHub APK 直装渠道。项目不使用 Google Play，Android APK 平台签名是安装和升级身份的根。

## 不可变发布原则

- 正式分发前，仓库管理员必须在 **Settings → General → Releases** 中启用 **Immutable releases**。工作流会拒绝修改同名 Release，但这只是纵深防御，不能替代 GitHub 对启用后新发布 Release 的 tag 和资产提供的服务端不可变保护。
- 正式版本只使用规范的 `vX.Y.Z` tag，tag 必须指向经过审核和测试的确定提交。
- 已推送并发布的 tag、GitHub Release、APK 和 `.sha256` 文件不可移动、删除后重建或同名覆盖。
- 发布工作流中的固定证书 SHA-256、README 公布的指纹和 APK 实际 signer 摘要必须三者一致。
- 修复错误版本时发布更高的补丁版本；不得用旧 APK 覆盖新版本，也不得复用已经创建 Release 的 tag。
- `versionCode` 由 semver 确定性生成：`major × 1,000,000 + minor × 1,000 + patch`。正式 tag 要求 `major <= 2146`、`minor/patch <= 999`，且不能是 `v0.0.0`；工作流会确认候选版本严格高于仓库中每个既有稳定 Release。

这一映射不依赖 `GITHUB_RUN_NUMBER`，工作流重命名或迁移不会改变同一 tag 的 `versionCode`。策略切换前发布的 `v0.1.3` 使用 `versionCode 11`；`v0.1.4` 映射为 `1004`，仍可从该版本覆盖升级。

如果工作流在创建 GitHub Release 之前失败，可以在修复非代码环境问题后重跑同一 tag。只要该 Release 已经存在，重跑就应失败；后续任何改变都必须使用新 tag。

## 签名密钥保管

发布 keystore、alias、keystore 密码和私钥密码共同决定升级连续性。遗失密钥会使项目无法为现有安装继续发布更新；直接替换密钥会导致 Android 拒绝覆盖安装。

最低保管要求：

1. 在可信的离线环境生成 keystore，不在聊天、Issue、构建日志或代码评审中传递明文密钥材料。
2. 保存至少两份相互独立的加密离线备份，置于不同故障域；密码与 keystore 分开保管。
3. 定期在隔离环境验证备份可解密、alias 可读取，并用 `keytool -list -v` 核对证书 SHA-256；验证不应修改正式 keystore。
4. GitHub 仅保存工作流所需 Secret，限制仓库写权限、`v*` tag 推送权限和发布审批权限。人员离开或设备遗失后及时轮换账号凭据，但不要轮换 APK 签名密钥。
5. 保存证书指纹、keystore 创建背景、备份位置和恢复责任人的离线记录。不要在记录中附密码。

当前应用的更新校验要求新 APK 与已安装 APK 的当前签名证书一致，因此尚不支持无缝密钥轮换。未来如需轮换，必须先设计 Android 签名 lineage 兼容方案，在现有密钥仍可用时发布迁移版本，并覆盖所有支持的 Android 版本做升级测试。不能只修改 GitHub Secret 或 CI 固定指纹。

## 发布前检查

1. 确认目标提交经过评审，工作区干净，版本说明不包含 Secret 或个人数据，并确认 **Settings → General → Releases → Immutable releases** 已启用。
2. 本地运行 README 所列的 lint、单元测试和构建命令。
3. 在最低支持版本、当前主要 Android 版本和至少一台真实电子墨水设备上验证：冷启动、Home 键、应用启动、管理页和默认桌面恢复。
4. 从上一正式版做覆盖升级测试，确认选择和字号保留；同时验证错误签名、相同或更低 `versionCode` 会被拒绝。
5. 确认新 tag 符合上述 semver 范围，`versionName` 正确，且语义版本严格高于所有既有稳定 Release；按公式复核计划生成的 `versionCode`。
6. 核对 README 公布的证书指纹没有被常规文档编辑改变，签名 Secret 完整，离线备份近期验证过。
7. 定期将工作流中固定的第三方 Action 提交 SHA 与各自官方仓库的受支持版本核对；升级时单独评审并运行完整发布链测试，不改回可移动的主版本 tag。
8. 检查版本是否新增权限、联网目标、收集数据或缓存行为；如有变化，先更新隐私和安全文档。
9. 推送 tag 后等待工作流完成，不在发布过程中移动 tag 或手动上传替代 APK。

## CI 发布门槛

tag 工作流依次执行：

1. 在使用签名材料前确认同名 Release 不存在，校验 semver 范围和确定性 `versionCode`，并通过分页查询确认它严格高于所有既有稳定 Release；随后把远端 tag 逐层解析到 commit，要求它与触发本次运行的 `github.sha` 完全一致；
2. 校验 tag 格式、构建 release APK，并使用发布 keystore 完成 zipalign 和签名；签名步骤无论成功或失败都会清除 Runner 上解码出的 keystore；
3. 运行 `apksigner verify --verbose --print-certs`，要求 `Number of signers` 严格等于 `1`，且只取得一个 64 位十六进制 signer SHA-256；
4. 将实际 signer、工作流固定信任值和 README 指纹规范化后硬比较；
5. 在 build job 记录已验证 APK 的 SHA-256；release job 下载 artifact 后必须与该值完全一致，才生成随包发布的 `.sha256` 文件；
6. 所有 tag 发布由同一并发组串行执行；创建 Release 前再次确认同名 Release 不存在、候选版本仍高于全部稳定 Release，并再次要求远端 tag 指向 `github.sha`；`gh release create --verify-tag` 禁止 CLI 自动创建缺失的 tag；
7. 仅在以上检查全部通过时创建新 Release，不覆盖或增补任何既有 Release 资产。

任一步失败都不得手动绕过。尤其不能为了完成发布而临时修改固定指纹、关闭签名校验或使用 `--clobber`。若发布命令异常后留下同 tag 的草稿 Release，工作流也会把它视为既有 Release 并停止；应先查明失败原因，不得让自动流程删除或覆盖该草稿。

## 发布后验证

1. 从公开 Release 页面重新下载 APK 和 `.sha256`，不要直接使用 CI 工作目录中的文件。
2. 确认该 Release 在 GitHub 上显示为 immutable，核对文件 SHA-256，并使用 Android SDK `apksigner verify --verbose --print-certs` 检查签名和证书摘要。
3. 在一台安装上一正式版的设备上执行最终覆盖升级冒烟测试。
4. 验证应用内手动检查更新能够找到新版本、完成下载校验并唤起系统安装器。
5. 记录 tag、提交 SHA、APK SHA-256、证书 SHA-256、`versionName`、`versionCode` 和验证设备，作为发布审计记录。

## 故障与回滚

Android 不允许使用更低 `versionCode` 的 APK 覆盖安装，应用内更新也只接受严格更新的版本。因此“回滚”不是重新上传旧 APK，而是前向修复：

1. 立即确认影响并暂停推广有问题的版本，但不要替换或删除既有 Release 资产。
2. 从最后一个已知良好的提交创建修复分支，只合入必要修复。
3. 使用更高的补丁版本和严格更高的 `versionCode` 构建新 APK，并继续使用同一发布证书。
4. 完成上一版到修复版、问题版到修复版两条覆盖升级测试。
5. 发布新的不可变 Release，并在说明中标明受影响版本、规避方法和修复范围。

如果问题导致 Launcher 无法正常使用，用户可先在 Android 系统设置中改回其他默认主屏，再通过本仓库 Releases 安装更高版本的修复 APK。为保留 E-Ink Launcher 的本地设置，不要先卸载应用；卸载会清除应用数据。若设备厂商隐藏默认桌面入口，应遵循该设备的官方恢复说明。

若签名密钥疑似泄露，应停止发布，保留证据并私下评估影响。不要在尚未有兼容迁移方案时更换 Secret 并发布新证书 APK，因为现有用户无法直接升级到它。
