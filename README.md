# SPSir · Android 0.4.7

个人手动记账应用：Kotlin + Jetpack Compose + Room / SQLite，支持 Android 8.0（API 26）及以上。单机单人使用，账本只保存在本机。

当前版本 **0.4.7**（versionCode 12）· 数据库版本 4 · 备份格式 4。更新内容见 [0.4.7 说明](docs/RELEASE-NOTES-0.4.7.md)。

## 快速开始

| 目的 | 做法 |
|---|---|
| 装到手机试用 | 双击 `build-apk.cmd`，得到 `app/build/outputs/apk/debug/SPSir-0.4.7.apk`，复制到手机安装。详见 [安装 APK](docs/INSTALL-ANDROID.md) |
| 在电脑上运行 | 双击 `run-debug.cmd`，启动 `SPSir_API35` 模拟器并安装调试包 |
| 生成正式版 | 双击 `build-release.cmd`，产出签名 APK 与 `update.json`。流程见 [正式发布](docs/RELEASE.md) |
| 跑测试与 Lint | `.\scripts\Check.ps1` |

## 功能

| 模块 | 说明 |
|---|---|
| 记账 | 金额优先、日历选日期；13 个大类、110 个细类；「最近使用」按记账次数取前 6 项；可编辑、删除。 |
| 多币种 | CNY、EUR、NOK、SEK、DKK、ISK、USD、GBP、JPY；原币与人民币金额均按最小单位精确保存，人民币交易只输入一次。 |
| 事件 | 个人事件默认已 AA；公共事件支持同行者、单人付款、参与者均分，公共费用自动写入本人流水；事件可改名、归档（归档仍计入统计）。 |
| 结算 | 查看应承担、实付、已转出／收到与转账建议；成员间实际转账可分批多次，编辑或删除后重算；可预览或分享 UTF-8 文字账单。 |
| 统计 | 日／周／月／年收支、趋势柱状图、分类占比与子分类下钻；流水页与统计页共用事件「仅含／排除」范围，确认后记住选择；排除记录仍可查看。 |
| 查找 | 跨月备注搜索，可组合日期、收支、类别、事件条件。 |
| 数据 | 隐藏／恢复预设分类；完整 JSON 备份与恢复，覆盖前保留副本。 |
| 更新 | 启动时检查 GitHub 正式版，可在工具页关闭；下载由浏览器完成。 |

不支持多设备同步、账户余额、预算、退款、自动支付识别与自动汇率。

## 文档

| 文档 | 内容 |
|---|---|
| [分类清单](docs/CATEGORIES.md) | 13 个大类与 110 个细类 |
| [界面截图](docs/UI-V1.md) | 第一版界面与验证 |
| [安装手机试用包](docs/INSTALL-ANDROID.md) | 生成、安装、签名迁移 |
| [正式发布与更新](docs/RELEASE.md) | 版本流程、GitHub Release、更新校验 |
| [0.4 需求](docs/REQUIREMENTS-0.4.md) | 本版需求与验收 |
| [0.4.7 验证记录](docs/BUILD-0.4.7.md) | 测试、Lint、签名与 SHA-256 |
| 历史记录 | `docs/` 下的 `BUILD-*.md` |

## 开发

环境：JDK 17+、Android SDK 35、Build Tools 35.0.0、platform-tools；模拟器需 Google APIs Android 35 x86_64 镜像。SDK、Gradle、模拟器放在 `.tools`，不修改全局 PATH；脚本优先读取 `.local/java-home.txt`，其次 Android Studio 内置 JBR 或 `JAVA_HOME`。

```powershell
.\scripts\Check.ps1                          # 单元测试 + Lint
.\scripts\Run-Android.ps1                    # 启动 SPSir_API35 模拟器并安装调试包
.\scripts\Run-Android.ps1 -Headless          # 无窗口
.\scripts\Run-Android.ps1 -SoftwareEmulation # 无硬件加速时诊断
```

Compose Preview：Android Studio 打开项目（Gradle JDK 17+，SDK 选 `.tools/sdk`），查看 `MainActivity.kt` 的 `EmptyLedgerPreview` / `PopulatedLedgerPreview`。预览不建数据库，验证真实保存需运行模拟器。

设备测试（模拟器已启动）：

```powershell
. .\scripts\Environment.ps1
& $Gradle :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
& $Adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
& $Adb -s $Device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $Adb -s $Device shell am instrument -w com.spsir.ledger.test/androidx.test.runner.AndroidJUnitRunner
```

测试使用独立数据库，不写入实际 `ledger.db`。模拟器端口固定 5580。

## 版本与发布

- 每次发版：修改 `app/build.gradle.kts` 版本号 → 更新本文件 → 编写 `docs/RELEASE-NOTES-<版本>.md` 与 `docs/BUILD-<版本>.md`。打包脚本会先校验，缺失即停止。
- 正式包使用独立签名，已安装 0.4.5 可直接覆盖升级并保留账本；从旧调试版首次转正式版需先导出备份、重装并恢复。签名文件在 `.local/`，不要入库，需离线备份。
- 数据库迁移 1 → 2 → 3 → 4；备份格式 4 可读取格式 1～4，上限 20 MiB。

## 代码结构

| 文件 | 职责 |
|---|---|
| `MainActivity.kt` | Compose 页面、输入表单、无数据库预览 |
| `LedgerViewModel.kt` | 页面状态、录入校验、保存 |
| `LedgerDatabase.kt` | Room 表、外键、查询、分类种子 |
| `Money.kt` | 最小货币单位金额与日期校验 |
| `Statistics.kt` / `StatisticsUi.kt` | 周期汇总、趋势、占比与界面 |
| `Backup.kt` | JSON 备份、严格校验、恢复 |
| `LedgerFilter.kt` | 组合筛选与「最近使用」分类 |
| `LedgerToolsUi.kt` | 工具页、分类选择与管理、筛选表单 |
| `PublicEvents.kt` / `EventManagementUi.kt` | 公共事件、成员、结算与账单 |
| `AppUpdates.kt` / `UpdateUi.kt` | 版本检查与更新提示 |
| `app/schemas` | Room 结构，升级须写迁移，不清库 |

`.gitignore` 已覆盖 SDK、Gradle 缓存、签名、APK、数据库与临时文件；`.tools`、`.local`、`build` 不入库，`git add .` 只添加源码与文档。

账本页面订阅全部流水后在内存中筛选／汇总，适合小规模数据；数据量明显增大时应改为按月份／事件的 SQL 查询与分页。
