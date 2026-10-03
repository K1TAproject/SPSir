# 0.3.1：图标与文案精简

日期：2026-10-03。应用版本 0.3.1（versionCode 4）。

- 使用 `design/app-icon/spsir-icon-1024.png`，提供 Android 自适应图标和各密度 PNG；已在 API 35 模拟器桌面确认圆形裁切完整。
- 精简流水、事件、统计、录入、分类及备份界面的解释文字；保留金额币种、操作入口及删除／覆盖确认。
- 构建入口 `build-apk.cmd` 现在额外输出 `app/build/outputs/apk/debug/SPSir.apk`。手机桌面名称仍为“随手账本”。
- 未更改数据库结构或记账逻辑。

## 验证

- Debug APK、测试 APK、13 项单元测试和 Lint 构建成功。
- API 35 模拟器覆盖安装成功，15 项设备测试通过；没有清除应用数据。
- 检查了桌面图标以及流水、事件、统计、录入截图，位于 `screenshots/v0.3.1/`。页面截图中的流水为测试样例。
- APK 签名验证通过（v2），Android 8.0 及以上可安装。实机安装由用户进行。
- Lint 无错误，10 条警告：目标 API／依赖版本、SDK 条件检查、5 张旧式方形图标以及可选单色图标。API 26 及以上使用已验证的自适应图标。

## 安装包

路径：`app/build/outputs/apk/debug/SPSir.apk`

大小：11,672,952 字节。

SHA-256：`E454D59B4FA2C4E32FA930C8F5D6535F10D26D877108D4254B61E08612585946`

这是带调试签名的个人试用包。构建与安装说明见 [INSTALL-ANDROID.md](INSTALL-ANDROID.md)。
