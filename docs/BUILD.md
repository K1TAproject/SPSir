# 第一版验证记录

日期：2026-10-02。项目：`D:\SPSir`，版本：0.1.0。

## 实际结果

- Kotlin / Compose / Room 编译成功。
- 4 项 JVM 单元测试通过：十进制金额精度、非法金额、日期边界、事件与全局汇总口径。
- 4 项 Android 35 模拟器测试通过：实际 Room 数据库持久化与编辑删除、非法记录拒绝、手动外币表单关联事件、页面渲染与导航。
- Android Lint：0 errors，4 warnings。提示为目标 API、编译 SDK、Gradle 可升级，以及未设计正式应用图标；未屏蔽这些提示。
- 已在 Windows 上启动 `SPSir_API35` 模拟器并运行 App。未连接实体手机。
- 实际首页状态栏已检查，文字对比度正确。

只生成了模拟器所需的 debug 应用和测试包，没有生成正式签名发布包。

## 操作入口

双击项目根目录 `run-debug.cmd`。更改代码后重新运行，会重新构建并更新模拟器应用，不清除账本。

如果只需要重新打开已编译版本，在项目目录运行：

```powershell
.\scripts\Run-Android.ps1 -NoBuild
```

Android Studio 的 Preview 函数与使用说明见根目录 README；本次实际运行验证采用 Android 模拟器。

## 真实渲染截图

- [实际空白账本首页](screenshots/00-empty.png)
- [示例流水](screenshots/previews/01-ledger.png)
- [事件累计支出与分类](screenshots/previews/02-event.png)
- [月度消费结构](screenshots/previews/03-statistics.png)
- [手动外币输入](screenshots/previews/04-manual-entry.png)

后四张由 Compose 设备测试宿主渲染，使用内存中的示例数据，不写入正式账本；测试宿主的系统栏外观与实际 Activity 存在差异。截图中的 120 NOK、81.23 元只是测试值，不代表真实汇率。

## 第一版边界

分类为预设，暂未开放分类管理。事件可创建与关联流水，暂未实现改名、归档和复盘备注。没有账户余额、自动识别、自动汇率、云同步或备份恢复。

当前 App 数据只保存在模拟器或将来安装它的设备内；卸载或清除应用数据会丢失账本。备份功能完成前，不将它作为唯一的正式账本。

## 可复核的输出

- 单元测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`
- 首轮设备测试报告：`app/build/reports/androidTests/connected/debug/index.html`
- Lint 报告：`app/build/reports/lint-results-debug.html`
- 最终构建日志：`.local/final-build.log`
- 最终设备测试日志：`.local/final-device-tests.log`，结果为 `OK (4 tests)`。
- 数据库初始结构：`app/schemas/com.spsir.ledger.LedgerDatabase/1.json`

开发环境固定为 Gradle 8.13、Android Gradle Plugin 8.11.1、Kotlin 2.1.20、Compose BOM 2025.05.01、Room 2.7.2、编译／目标 API 35；本机 Java 为现有的 JBR 21。将来升级数据库必须编写迁移，不允许以清空用户数据代替升级。
