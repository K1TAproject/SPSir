# 随手账本 · Android 0.3

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

- 金额优先录入，日历选择日期；支出分类面板提供大类／细类及最近 6 个分类（按记账日期排序）。
- 「工具」中可隐藏／恢复预设分类；历史流水、编辑和统计不受影响。
- 「搜索与筛选」支持跨月备注搜索及日期、收支、类别、事件组合条件；流水按日期分组并显示结果内每日支出。
- 「工具」中可导出完整 JSON 备份、校验并恢复文件；覆盖前保留应用内旧账本副本，失败回滚。

当前为本地单人调试版本，建议定期将备份导出到应用之外。事件改名／结束／复盘备注、预算、云备份和完整自定义分类仍未实现。不含多人分摊、账户余额和自动录入。

## 0.3 使用入口

- **记账**：点击「记一笔」，填写金额，通过分类面板／最近分类选择用途，点击「记账日期」使用日历。
- **查找历史记录**：点击流水页「搜索与筛选」，条件留空表示不限。查找跨所有月份；「清除筛选」返回当前所选月份。
- **隐藏分类**：右上角「工具」，向下滚动到「分类显示」。隐藏大类会同时让其下属细类暂时退出新录入候选。
- **备份**：「工具 → 导出完整备份」，选择保存位置。备份以 UTF-8 JSON 保存，金额为整数最小单位；包含全部分类、隐藏设置、事件和流水。
- **恢复**：「工具 → 选择备份恢复」，检查摘要后确认覆盖。当前支持格式版本 1、最大 20 MB；不进行合并。恢复前自动保存一份旧账本，下一次恢复会替换该副本，可在同页查看恢复或导出。应用内副本会随卸载丢失。

数据库结构从版本 1 升级至 2，仅新增分类隐藏状态表，通过迁移保留原账本。备份格式版本与数据库版本相互独立。

本版开发需求见 [0.3 需求](docs/REQUIREMENTS-0.3.md)，验证与截图见 [0.3 验证记录](docs/BUILD-0.3.md)。

## 电脑上预览与调试

手机试用包：双击根目录 `build-apk.cmd`，生成 `app/build/outputs/apk/debug/SPSir.apk`。手机安装、备份迁移及更新方式见 [生成与安装 APK](docs/INSTALL-ANDROID.md)。

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

历史验证记录：[0.2 版本](docs/BUILD-0.2.md)、[第一版](docs/BUILD.md)。

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
- `Backup.kt`：完整 JSON 备份、严格字段校验、覆盖前副本及事务恢复。
- `LedgerFilter.kt`：组合筛选与可见／最近分类。
- `LedgerToolsUi.kt`：备份入口、日期选择、分类选择和管理、搜索筛选表单。
- `app/schemas`：Room 导出的数据库版本结构；未来升级应编写迁移，不清库重建。

目前是小规模账本原型，页面订阅全部流水后在内存中筛选／汇总。数据量明显增加时再改成按月份／事件的 SQL 查询与分页。
