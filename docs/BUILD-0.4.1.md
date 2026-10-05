# 0.4.1 名称更新验证

验证日期：2026-10-05。

- 安装后的应用名称及首页标题统一为 `SPSir`，安装包仍为 `SPSir.apk`。
- 版本：0.4.1（versionCode 6）。应用 ID 保持 `com.spsir.ledger`，未修改数据库和业务逻辑。
- 构建：`scripts/Build-Apk.ps1` 成功。
- APK 元数据确认应用名称、版本号和应用 ID；签名校验通过，签名证书与更新前安装包一致。
- 在 API 35 模拟器执行 `adb install -r` 覆盖安装成功，启动后 UI 节点确认首页标题为 `SPSir`。未卸载应用或清除数据。
- `git diff --check` 通过；本次仅调整名称与版本，未重复运行完整业务测试。0.4 功能验证见 [上一版记录](BUILD-0.4.md)。手机实际覆盖安装仍待用户验证。

安装包：`app/build/outputs/apk/debug/SPSir.apk`，11,787,628 字节。

SHA-256：`09282B13ECCCD79DF5CB1484AC1E97928C1CCA4CFB3D9D738BCF1B0F1E725391`。

更新前建议在“工具”导出备份，再直接打开新 APK 覆盖安装，不要先卸载。签名冲突时请保留原应用和数据，勿直接卸载。
