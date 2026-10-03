package com.spsir.ledger

import android.graphics.Bitmap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class StatisticsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(rows: List<LedgerRow> = samples()) {
        compose.setContent {
            LedgerTheme {
                LazyColumn(contentPadding = PaddingValues(20.dp)) {
                    item { StatisticsPanel(rows, presetCategories()) }
                }
            }
        }
        compose.onNodeWithText("选择日期").performClick()
        compose.onNodeWithTag("stat-date-input").performTextReplacement("2026-01-01")
        compose.onNodeWithText("确定").performClick()
    }

    @Test fun dailyWeeklyMonthlyYearlyViewsShowCorrectTotalsAndCharts() {
        show()
        compose.onNodeWithTag("stat-range").assertTextEquals("2026-01-01 ～ 2026-01-31")
        compose.onNodeWithTag("stat-expense").assertTextContains("¥ 900.00")
        compose.onNodeWithTag("stat-income").assertTextContains("¥ 1000.00")
        capture("01-month-overview")

        compose.onNodeWithTag("period-DAY").performClick()
        compose.onNodeWithTag("stat-range").assertTextEquals("2026-01-01")
        compose.onNodeWithTag("stat-expense").assertTextContains("¥ 200.00")
        compose.onNodeWithTag("period-WEEK").performClick()
        compose.onNodeWithTag("stat-range").assertTextEquals("2025-12-29 ～ 2026-01-04")
        compose.onNodeWithTag("stat-expense").assertTextContains("¥ 550.00")
        capture("02-week-overview")
        compose.onNodeWithTag("period-YEAR").performClick()
        compose.onNodeWithTag("stat-range").assertTextEquals("2026-01-01 ～ 2026-12-31")
        compose.onNodeWithTag("stat-expense").assertTextContains("¥ 1800.00")
        compose.onNodeWithTag("stat-net").assertTextContains("¥ -800.00")
        capture("03-year-overview")

        compose.onNodeWithTag("income-expense-chart").performScrollTo().performTouchInput { click(centerRight) }
        compose.onNodeWithTag("trend-details").performScrollTo().performClick()
        compose.onNodeWithText("2026-12-01").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("收起明细").performScrollTo().performClick()
    }

    @Test fun sharesDrillDownAndSeparateIncome() {
        show()
        compose.onNodeWithTag("share-photo").performScrollTo().assertTextContains("33.3%")
        capture("04-category-shares")
        compose.onNodeWithTag("share-photo").performClick()
        compose.onNodeWithTag("share-photo.film").performScrollTo().assertTextContains("100.0%")
        capture("05-photo-detail")
        compose.onNodeWithText("收入占比").performScrollTo().performClick()
        compose.onNodeWithTag("share-income").performScrollTo().assertTextContains("100.0%")
        compose.onNodeWithTag("share-income").assertTextContains("¥ 1000.00")
    }

    @Test fun emptyPeriodHasNoMisleadingChartOrPercentages() {
        show(emptyList())
        compose.onNodeWithTag("stat-expense").assertTextContains("¥ 0.00")
        compose.onNodeWithTag("income-expense-chart").assertDoesNotExist()
        compose.onNodeWithText("暂无支出").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("收入占比").performScrollTo().performClick()
        compose.onNodeWithText("暂无收入").performScrollTo().assertIsDisplayed()
    }

    private fun samples(): List<LedgerRow> = listOf(
        sample("old", "2025-12-29", 5000, "food.breakfast"),
        sample("meal", "2026-01-01", 20000, "food.lunch"),
        sample("film", "2026-01-04", 30000, "photo.film"),
        sample("phone", "2026-01-31", 40000, "routine.phone"),
        sample("later", "2026-02-01", 90000, "food.dinner"),
        sample("pay", "2026-01-01", 100000, "income", "income"),
    )

    private fun sample(id: String, date: String, amount: Long, categoryId: String, kind: String = "expense"): LedgerRow {
        val categories = presetCategories()
        val category = categories.first { it.id == categoryId }
        return LedgerRow(LedgerEntry(id, kind, amount, "CNY", amount, categoryId, if (kind == "expense") "trip" else null, date, ""),
            category.name, categories.find { it.id == category.parentId }?.name, "示例事件")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "statistics")
        directory.mkdirs()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
