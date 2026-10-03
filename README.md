# 随手账本 · Android 0.2

个人手动记账原型。Kotlin + Jetpack Compose + Room / SQLite，最低 Android 8.0（API 26）。

已完成第一版界面美化，查看 [界面截图与验证](docs/UI-V1.md)。

## 当前功能

- 手动新增、编辑和删除收入／支出，无自动识别、导入、联网或自动汇率。
- 预设 13 个支出大类、110 个可选细分类别，先选大类再选细类。完整内容见 [分类清单](docs/CATEGORIES.md)。
- 收入只有一个通用分类，不维护账户余额。
- 外币保存原币金额、币种和**手动填写**的人民币金额。人民币交易只输入一次。
- 支持 CNY、EUR、NOK、SEK、DKK、ISK、USD、GBP、JPY。ISK、JPY 原币以整数保存，其余保留至多两位小数。
- 一笔支出可选一个事件；默认金额已经 AA，仅记录个人承担部分。
- 事件内记账自动关联事件；编辑已有流水可补关联／解除关联。
- 同一笔流水只存一份。事件与全部流水共用记录，所有合计使用人民币金额。
- 日度、周度、月度、年度收支一览、收支趋势柱状图、分类占比及子分类下钻；事件保留跨月份总额与分类占比。
- 周度按周一至周日；支持前后切换、指定日期、回到当前周期。点击柱形可查看金额，也可展开各时间段明细。
- Compose 预览及界面测试使用示例数据，实际账本初始为空。

这是可迭代的开发原型，**还没有备份恢复，不适合作为唯一的正式账本**。分类管理、事件改名／结束／复盘备注、预算、完整搜索筛选、云备份均未实现。不含多人分摊、账户余额和自动录入。

## 电脑上预览与调试

### Android Studio 的 Compose Preview（不生成安装包）

1. 用 Android Studio 打开本目录，选择 JDK 17 或更高版本作为 Gradle JDK。
2. SDK 路径使用项目内 `.tools/sdk`；本机 `local.properties` 已指向这里。
3. 等待 Gradle Sync 完成，打开 `app/src/main/java/com/spsir/ledger/MainActivity.kt`。
4. 切换 **Split / Design**，查看 `EmptyLedgerPreview` 和 `PopulatedLedgerPreview`；可启用 Interactive Mode 点击预览。

预览复用真实 Compose 页面，不创建 Room 数据库。预览回调不写入记录，验证真实保存应运行模拟器。

### Android 模拟器（真实交互与数据库）

双击根目录 `run-debug.cmd`，或在 PowerShell 运行：

```powershell
.\scripts\Run-Android.ps1
```

它会启动项目专用 `SPSir_API35` 模拟器、构建并安装本地 **debug 调试包**。这不是正式发布包；模拟器运行 Android 应用也需要内部调试包。不会连接或修改你的实体手机。

修改代码后再次运行同一命令，更新时保留模拟器账本。退出模拟器不会删除数据。调试包和日志在被忽略的 `app/build`、`.local` 中。

如果机器没有模拟器加速支持，先按 Android 官方文档配置加速：
https://developer.android.com/studio/run/emulator-acceleration

诊断用的软件模拟模式（性能可能很差）：

```powershell
.\scripts\Run-Android.ps1 -SoftwareEmulation
```

`-Headless` 可用于无窗口自动测试；普通运行会显示模拟器窗口。

### 本机开发环境

- SDK、模拟器、Gradle 等放在 `.tools`，不修改全局 Java 或 PATH。
- 此电脑已安装 Google AEHD 2.2 模拟器加速驱动；这是独立的 Windows 驱动服务，不随项目目录移动。
- 开发脚本优先读取 `.local/java-home.txt`，没有该文件时使用 Android Studio 内置 JBR 或 `JAVA_HOME`。
- `local.properties`、`.tools`、`.local` 和构建产物不提交到版本管理。
- 模拟器端口固定为 5580，只向名为 `SPSir_API35` 的虚拟设备安装。

本次构建与验证结果见 [0.2 验证记录](docs/BUILD-0.2.md)，其中包含模拟器截图；[第一版记录](docs/BUILD.md) 保留作为历史参考。

换电脑后，需要 JDK 17+、Android SDK 35、Build Tools 35.0.0、platform-tools、emulator、Google APIs Android 35 x86_64 镜像。Android Studio 中可以通过 SDK Manager 安装；也可将 SDK 安装到 `.tools/sdk` 复用脚本。项目提供标准 Gradle Wrapper。

## 检查

```powershell
.\scripts\Check.ps1
```

检查金额精度、非法输入、日期边界、周期统计与事件汇总口径，并执行 Android Lint。模拟器启动后可进一步运行：

```powershell
. .\scripts\Environment.ps1
& $Gradle :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
& $Adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
& $Adb -s $Device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $Adb -s $Device shell am instrument -w com.spsir.ledger.test/androidx.test.runner.AndroidJUnitRunner
```

设备测试使用独立测试数据库，验证持久化、分类兼容升级、手动表单、周期切换、占比下钻和空数据。不会写入实际 `ledger.db`。以上命令保留已安装应用数据；不要对有真实账本的设备使用会在测试后卸载应用的测试流程。

## 代码位置

- `MainActivity.kt`：Compose 页面、输入表单、无数据库的电脑预览。
- `LedgerViewModel.kt`：页面状态、手动录入校验、保存操作。
- `LedgerDatabase.kt`：Room 表、外键、查询及分类种子数据。
- `Money.kt`：以最小货币单位精确保存金额、日期验证。
- `Statistics.kt`：自然周期范围、人民币收支汇总、趋势分组与分类占比。
- `StatisticsUi.kt`：周期选择、收支图表与类别下钻。
- `app/schemas`：Room 导出的数据库版本结构；未来升级应编写迁移，不清库重建。

目前是小规模账本原型，页面订阅全部流水后在内存中筛选／汇总。数据量明显增加时再改成按月份／事件的 SQL 查询与分页。
