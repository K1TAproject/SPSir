package com.spsir.ledger

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class DailyUseUiTest {
    @get:Rule val compose = createComposeRule()

    private fun sample() = LedgerState(loading = false, categories = presetCategories(),
        events = listOf(LedgerEvent("trip", "北欧旅行")),
        rows = listOf(LedgerRow(LedgerEntry("film", "expense", 12000, "NOK", 8123,
            "photo.film", "trip", "2026-09-30", "旅行胶卷"), "胶卷购买", "摄影", "北欧旅行")))

    @Test fun searchCrossesMonthsAndClearsBackToMonthlyList() {
        compose.setContent { LedgerTheme { LedgerScreen(sample()) } }
        compose.onNodeWithText("搜索与筛选").performScrollTo().performClick()
        compose.onNodeWithText("备注关键词").performTextInput("胶卷")
        capture("01-search-form")
        compose.onNodeWithText("查找", substring = false).performClick()
        compose.onNodeWithText("旅行胶卷", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("筛选结果 · 1 笔").performScrollTo().assertIsDisplayed()
        capture("02-search-result")
        compose.onNodeWithText("清除筛选").performScrollTo().performClick()
        compose.onNodeWithText("筛选结果 · 1 笔").assertDoesNotExist()
    }

    @Test fun hiddenCategoryRemainsEditableAndPickerSupportsPhotographyAndCalendar() {
        var saved: EntryDraft? = null
        val today = LocalDate.now().toString()
        val original = sample().rows.single().let { it.copy(entry = it.entry.copy(occurredOn = today)) }
        compose.setContent { LedgerTheme { LedgerScreen(sample().copy(rows = listOf(original), hiddenCategories = setOf("photo")),
            onSave = { draft, done -> saved = draft; done() }) } }
        compose.onNodeWithText("胶卷购买", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("分类：摄影 / 胶卷购买").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("date-记账日期").performScrollTo().performClick()
        capture("03-calendar")
        compose.onNodeWithText("今天", substring = false).performClick()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.runOnIdle { assertEquals("photo.film", saved!!.categoryId); assertEquals(today, saved!!.date) }
        compose.onNodeWithText("＋ 记一笔").performClick()
        compose.onNodeWithText("分类：饮食 / 早餐").performScrollTo().performClick()
        compose.onNodeWithText("摄影", substring = false).assertDoesNotExist()
        compose.onNodeWithText("午餐", substring = false).performClick()
        compose.onNodeWithText("分类：饮食 / 午餐").assertIsDisplayed()
    }

    @Test fun managementHidesAndRestoresChoicesAndRecentCategoryIsDirectlySelectable() {
        compose.setContent {
            var state by remember { mutableStateOf(sample()) }
            LedgerTheme { LedgerScreen(state, setCategoryHidden = { id, hidden ->
                state = state.copy(hiddenCategories = if (hidden) state.hiddenCategories + id else state.hiddenCategories - id)
            }) }
        }
        compose.onNodeWithText("工具", substring = false).performClick()
        compose.onNodeWithTag("visible-food").performScrollTo().performClick().assertIsOff()
        capture("04-category-manager")
        compose.onNodeWithTag("visible-food").performClick().assertIsOn()
        compose.onNodeWithText("返回", substring = false).performClick()
        compose.onNodeWithText("＋ 记一笔").performClick()
        compose.onNodeWithText("金额（人民币）").performTextInput("65.50")
        compose.onNodeWithText("最近使用", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("胶卷购买", substring = false).performClick()
        compose.onNodeWithText("分类：摄影 / 胶卷购买").assertIsDisplayed()
        capture("05-recent-category")
        compose.onNodeWithText("分类：摄影 / 胶卷购买").performClick()
        compose.onNodeWithTag("choose-photo.care").performScrollTo().performClick()
        compose.onNodeWithText("分类：摄影 / 保养维修").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "v03")
        directory.mkdirs()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
