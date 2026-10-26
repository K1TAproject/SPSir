package com.spsir.ledger

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.io.File
import android.graphics.Bitmap

class SharedStatisticsUiTest {
    @get:Rule val compose = createComposeRule()
    private val month = LocalDate.now().toString().take(7)
    private fun row(id: String, event: String?, amount: Long, day: String, kind: String = "expense") = LedgerRow(
        LedgerEntry(id, kind, amount, "CNY", amount, if (kind == "income") "income" else "food.lunch", event, "$month-$day", id),
        if (kind == "income") "收入" else "午餐", if (kind == "income") null else "饮食", event)
    private fun fixture() = LedgerState(loading = false, categories = presetCategories(),
        events = listOf(LedgerEvent("trip", "北欧旅行"), LedgerEvent("photo", "摄影活动", archived = true), LedgerEvent("empty", "尚无开支")),
        rows = listOf(row("home", null, 200000, "01"), row("travel", "trip", 800000, "02"),
            row("photo", "photo", 50000, "03"), row("pay", null, 600000, "01", "income")))
    private fun seek(tag: String) { compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag)) }
    private fun open() { seek("statistics-scope"); compose.onNodeWithTag("statistics-scope").performClick() }
    private fun capture(name: String) {
        compose.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrument.targetContext.getExternalFilesDir(null), "v047").apply { mkdirs() }
        val bitmap = requireNotNull(instrument.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun select(mode: String, event: String) {
        open()
        compose.onNodeWithTag("scope-mode-$mode").performScrollTo().performClick()
        compose.onNodeWithTag("scope-event-$event").performScrollTo().performClick()
        compose.onNodeWithText("应用").performClick()
    }

    @Test fun pagesShareScopeWhileRecordsDailyTotalsAndEventDetailsKeepTheirRoles() {
        var state by mutableStateOf(fixture())
        val restore = StateRestorationTester(compose)
        restore.setContent { LedgerTheme { LedgerScreen(state, onScopeChange = { state = state.copy(statisticsScope = it) }) } }
        seek("total-本月支出")
        compose.onNodeWithTag("total-本月支出").assertTextContains("¥ 10500.00")
        select("exclude", "trip")
        seek("total-本月支出")
        compose.onNodeWithTag("total-本月支出").assertTextContains("¥ 2500.00").assertTextContains("收入 ¥ 6000.00").assertTextContains("收支差额 ¥ 3500.00")
        capture("01-month-scope")
        seek("daily-$month-02")
        compose.onNodeWithTag("daily-$month-02").assertTextContains("当日统计支出 ¥ 0.00", substring = true)
        seek("entry-travel")
        compose.onNodeWithTag("entry-travel").assertTextContains("不计入统计").performClick()
        compose.onNodeWithText("编辑记录").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("统计", substring = false).performClick()
        seek("stat-expense")
        compose.onNodeWithTag("stat-expense").assertTextContains("¥ 2500.00")
        compose.onNodeWithTag("stat-income").assertTextContains("¥ 6000.00")
        capture("02-statistics-scope")
        restore.emulateSavedInstanceStateRestore()
        seek("stat-expense")
        compose.onNodeWithTag("stat-expense").assertTextContains("¥ 2500.00")
        select("only", "photo")
        compose.onNodeWithText("流水", substring = false).performClick()
        seek("total-本月支出")
        compose.onNodeWithTag("total-本月支出").assertTextContains("¥ 500.00").assertTextContains("收入 ¥ 0.00")
        compose.runOnIdle { state = state.copy(events = state.events.map { if (it.id == "photo") it.copy(name = "摄影新名", archived = false) else it }) }
        seek("scope-label")
        compose.onNodeWithTag("scope-label").assertTextEquals("仅含：摄影新名")
        compose.onNodeWithText("事件", substring = false).performClick()
        compose.onNodeWithText("北欧旅行").performClick()
        seek("total-事件累计支出")
        compose.onNodeWithTag("total-事件累计支出").assertTextContains("¥ 8000.00")
        compose.onNodeWithText("流水", substring = false).performClick()
        open()
        compose.onNodeWithText("恢复全部").performScrollTo().performClick()
        compose.onNodeWithText("应用").performClick()
        seek("total-本月支出")
        compose.onNodeWithTag("total-本月支出").assertTextContains("¥ 10500.00")
    }

    @Test fun searchConflictShowsRecordsAndZeroWhileMonthlyCardRemainsMonthly() {
        var state by mutableStateOf(fixture())
        compose.setContent { LedgerTheme { LedgerScreen(state, onScopeChange = { state = state.copy(statisticsScope = it) }) } }
        select("exclude", "trip")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("搜索与筛选"))
        compose.onNodeWithText("搜索与筛选").performClick()
        compose.onNodeWithText("备注关键词").performTextReplacement("travel")
        compose.onNodeWithText("查找", substring = false).performClick()
        seek("total-筛选结果支出")
        compose.onNodeWithTag("total-筛选结果支出").assertTextContains("¥ 0.00").assertTextContains("计入统计 0 笔")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("当前结果未计入统计，可调整统计范围。"))
        compose.onNodeWithText("当前结果未计入统计，可调整统计范围。").assertExists()
        seek("entry-travel")
        compose.onNodeWithTag("entry-travel").assertTextContains("不计入统计")
        capture("03-search-conflict")
        seek("total-本月支出")
        compose.onNodeWithTag("total-本月支出").assertTextContains("¥ 2500.00")
    }

    @Test fun draftRequiresExplicitEventAndCancelOrBackNeverApplies() {
        var state by mutableStateOf(fixture())
        val restore = StateRestorationTester(compose)
        restore.setContent { LedgerTheme { LedgerScreen(state, onScopeChange = { state = state.copy(statisticsScope = it) }) } }
        open()
        compose.onNodeWithTag("scope-mode-exclude").performClick()
        compose.onNodeWithText("应用").assertIsNotEnabled()
        compose.onNodeWithTag("scope-event-empty").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("scope-event-empty").assertIsSelected()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(StatisticsScope(), state.statisticsScope) }
        open()
        compose.onNodeWithTag("scope-mode-exclude").performClick()
        compose.onNodeWithText("应用").assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.runOnIdle { assertEquals(StatisticsScope(), state.statisticsScope) }
        open()
        compose.onNodeWithTag("scope-mode-only").performClick()
        // Tap the horizontal outside margin of the dialog, using actual screen coordinates.
        val instrument = InstrumentationRegistry.getInstrumentation()
        val time = android.os.SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val touch = android.view.MotionEvent.obtain(time, time, action, 1f, 200f, 0)
            instrument.uiAutomation.injectInputEvent(touch, true)
            touch.recycle()
        }
        compose.onNodeWithText("应用").assertDoesNotExist()
        compose.runOnIdle { assertEquals(StatisticsScope(), state.statisticsScope) }
        select("only", "empty")
        seek("total-本月支出")
        compose.onNodeWithTag("total-本月支出").assertTextContains("¥ 0.00")
    }

    @Test fun noEventsOnlyOffersAllAndLongListIsScrollableWithLargeFont() {
        var events by mutableStateOf(emptyList<LedgerEvent>())
        var applied: StatisticsScope? = null
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
            LedgerTheme { StatisticsScopeDialog(StatisticsScope(), events, setOf("e19"), {}, { applied = it }) }
        } }
        compose.onNodeWithTag("scope-mode-exclude").assertDoesNotExist()
        compose.onNodeWithText("应用").assertIsEnabled()
        compose.runOnIdle { events = (0..19).map { LedgerEvent("e$it", "第 $it 次旅行——很长的事件名称用于大字体验证", archived = true) } }
        compose.onNodeWithTag("scope-mode-exclude").performClick()
        compose.onNodeWithTag("scope-event-e19").performScrollTo().performClick()
        compose.onNodeWithText("公共事件 · 已归档").assertExists()
        capture("04-large-font-events")
        compose.onNodeWithText("应用").performClick()
        compose.runOnIdle { assertEquals(StatisticsScope("exclude", "e19"), applied) }
    }
}
