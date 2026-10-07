# 正式发布与更新

当前版本：0.4.4 / versionCode 9。数据库及完整备份格式不变。

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

## 构建与发布

1. 修改 `app/build.gradle.kts` 中 `versionName`，递增 `versionCode`。
2. 完成测试，将对应源码提交、推送（本次开发不自动执行）。
3. 双击 `build-release.cmd`。缺少签名时脚本停止，不会自动生成替代密钥。
4. 在 `app/build/outputs/apk/release/` 获取 `SPSir-0.4.4.apk` 和 `update.json`，两者由同一次构建生成。调试包仍用 `build-apk.cmd`，不要将调试包作为正式更新附件。
5. GitHub 仓库 K1TAproject/SPSir → Releases → Draft a new release。标签必须是 `v0.4.4`，选择对应源码提交，填写更新说明，上传上述两个文件后一起发布。
6. 正式更新不要勾选 Pre-release，将发布设为 Latest。先用草稿准备完整附件，避免用户读到缺少元数据的版本。不要手动改写 update.json 版本或复用另一版 APK。

脚本只生成本地文件，不提交、推送或发布。发布包禁用调试；未为减小体积而扩大混淆或依赖升级范围。

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
