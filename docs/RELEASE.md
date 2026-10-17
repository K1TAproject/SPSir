# 正式发布与更新

当前版本：0.4.6 / versionCode 11。数据库版本 4；完整备份格式 4，继续读取旧格式 1～3。

## 首次切换正式版

0.4.3 及此前的个人试用包使用调试签名。本次正式版使用新建的独立签名，不能直接覆盖旧调试版。

1. 在旧版“工具”中导出完整备份到应用之外的安全位置，并确认文件已保存。文字账单不是完整备份。
2. 妥善保留备份后再卸载旧版，安装正式版，选择完整备份恢复。
3. 核对流水、公共事件及结算。以后一直使用同一正式签名的更新包覆盖安装，不再卸载。

签名文件 `.local/spsir-release.jks` 与配置 `.local/release-signing.properties` 含私钥和密码，均被 Git 忽略。请将两者一起备份到可信离线位置，不上传到仓库或 Release。丢失签名后不能简单新建密钥继续覆盖更新。配置格式如下（密码不应放入文档）：

```properties
storeFile=.local/spsir-release.jks
storePassword=实际密码
keyAlias=spsir
keyPassword=实际密码
```

## 版本更新流程（每次发版都执行）

1. 修改 `app/build.gradle.kts`：`versionName` 升位，`versionCode` 加 1。
2. 更新 `README.md`：首行版本号、功能说明，以及当前调试包 `SPSir-<版本>.apk`。
3. 新建 `docs/RELEASE-NOTES-<版本>.md`：面向用户的更新说明（改动、兼容性、安装方式），发布时作为 Release 正文。
4. 新建 `docs/BUILD-<版本>.md`：单元测试、设备测试、Lint、签名证书与 APK SHA-256 等验证记录。
5. 更新本文件与 `docs/INSTALL-ANDROID.md` 中的“当前版本”。
6. 运行 `.\scripts\Check.ps1`，确认单元测试和 Lint 通过。
7. 双击 `build-apk.cmd`（调试试用包）或 `build-release.cmd`（正式包）。两个脚本都会先检查 README 与 `docs/RELEASE-NOTES-<版本>.md`：版本号不一致或更新说明缺失时直接停止，不生成安装包；`docs/BUILD-<版本>.md`、`RELEASE.md`、`INSTALL-ANDROID.md` 未同步时给出警告。
8. 提交并推送源码，再按下文发布 Release。

## 构建与发布

- 正式包：双击 `build-release.cmd`，在 `app/build/outputs/apk/release/` 得到 `SPSir-0.4.6.apk` 和同次构建的 `update.json`。缺少签名时脚本停止，不会自动生成替代密钥。
- 调试包：双击 `build-apk.cmd`，在 `app/build/outputs/apk/debug/` 得到 `SPSir-0.4.6.apk`。调试包不作为正式更新附件。

脚本只生成本地文件，不提交、推送或发布。发布包禁用调试；未为减小体积而扩大混淆或依赖升级范围。

## 在 GitHub 发布 Release

1. 先提交并推送源码：`git add .` → `git commit -m "v0.4.6: ..."` → `git push`。`git push` 不会推送标签，标签由下一步的 Release 创建（也可先在本地 `git tag -a v0.4.6 -m "SPSir 0.4.6"` 后 `git push origin v0.4.6`，再在网页选择该标签）。
2. 打开仓库 K1TAproject/SPSir → **Releases → Draft a new release**。
3. **Tag** 填 `v0.4.6`，Target 选 `main` 上最新的源码提交。标签必须严格等于 `v` + `versionName`：应用只读取 `releases/latest`，并要求 `tag_name == v0.4.6`。
4. **Title** 填 `SPSir 0.4.6`；正文粘贴 `docs/RELEASE-NOTES-0.4.6.md` 的内容，应用用它显示更新说明。
5. 上传两个附件，缺一不可，且必须来自同一次 `build-release.cmd`：`SPSir-0.4.6.apk` 与 `update.json`。应用会校验应用 ID、versionCode、版本号、标签、APK 附件名、下载地址和 SHA-256，文件名或哈希不一致会静默失败，只在手动检查时提示失败。
6. 不要勾选 Pre-release；勾选“Set as the latest release”，然后发布。
7. 验证：打开 `https://api.github.com/repos/K1TAproject/SPSir/releases/latest`，确认 `tag_name` 为 `v0.4.6`、`assets` 同时含 APK 和 `update.json`；在装有 0.4.5 正式版的手机上于工具页手动检查更新，应提示 0.4.6 并能打开下载页。
8. 发布后不要删除或改名标签：旧版本用它拼接下载地址。后续版本必须递增 versionCode 并使用新标签。

已安装 GitHub CLI 时，可用一条命令代替第 2～6 步（命令会自行创建标签，不要再重复用网页面板发布同一版本）：

```powershell
gh release create v0.4.6 --title "SPSir 0.4.6" --notes-file docs/RELEASE-NOTES-0.4.6.md --latest `
  app/build/outputs/apk/release/SPSir-0.4.6.apk app/build/outputs/apk/release/update.json
```

## 更新行为

- 默认启动检查，每 24 小时最多自动请求一次；工具页可关闭或手动检查。失败的自动检查也计入间隔，避免网络故障时反复请求。
- 请求公开 GitHub Release 最新正式版及其 update.json，以整数 versionCode 比较版本，校验应用 ID、标签、版本、APK 附件与最低 Android 版本。私有仓库不支持，不在 APK 中放 Token。
- 更新说明来自 Release；可稍后或忽略当前版本，手动检查不受忽略状态及检查间隔限制。
- 用户点击“前往下载”才打开浏览器发布页。App 不下载 APK、不申请安装未知应用权限、不自动安装；浏览器或文件管理器通过系统安装流程处理。
- 更新检查不访问账本，不发送账户、流水或设备标识。GitHub 会接收普通网络请求（包括 IP）；没有网络仍可完整记账。无后台轮询或推送通知。
- 缺少附件、未发布、网络不可达或限流时，自动检查不打扰；手动检查提示失败。GitHub 在部分网络可能不可达。
- 第一台安装本版后，只有发布更大 versionCode 的完整正式 Release 才会出现更新提示。版本 0.4.3 没有更新检查，首次需要手动安装。

## 验证边界

更新解析、版本比较、节流、忽略和浏览器交互已使用测试数据验证；完整结果见 [0.4.4 验证记录](BUILD-0.4.4.md)。真正的“旧正式版 → 新正式版”在线提醒与安装，需要后续发布一个更高版本并在手机检查。本次不为了测试创建虚假公开版本，不发布任何文件。

实现参考：[GitHub 最新正式 Release API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)、[Android 应用签名](https://developer.android.com/studio/publish/app-signing)。
