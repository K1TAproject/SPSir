package com.spsir.ledger

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LedgerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expenseAddedInsideAnEventCarriesItsIdentityAndManualAmounts() {
        var saved: EntryDraft? = null
        compose.setContent {
            LedgerTheme {
                LedgerScreen(
                    LedgerState(loading = false, categories = presetCategories(), events = listOf(LedgerEvent("trip", "北欧旅行"))),
                    onSave = { draft, done -> saved = draft; done() },
                )
            }
        }
        compose.onNodeWithText("事件", useUnmergedTree = true).performClick()
        compose.onNodeWithText("北欧旅行").performClick()
        compose.onNodeWithText("＋ 记一笔").performClick()
        compose.onNodeWithText("CNY · 人民币").performClick()
        compose.onNodeWithText("NOK · 挪威克朗").performClick()
        compose.onNodeWithText("原币金额（NOK）").performTextInput("120")
        compose.onNodeWithText("人民币金额（手动填写）").performTextInput("81.23")
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertEquals("trip", saved!!.eventId)
            assertEquals("NOK", saved!!.currency)
            assertEquals("120", saved!!.amount)
            assertEquals("81.23", saved!!.rmb)
        }
    }

    @Test fun renderLedgerEventStatisticsAndManualEditor() {
        val meal = LedgerEntry("sample-meal", "expense", 12000, "NOK", 8123,
            "food.lunch", "trip", LocalDate.now().toString(), "旅行午餐 · 已 AA")
        val film = LedgerEntry("sample-film", "expense", 6500, "CNY", 6500,
            "photo.film", "trip", LocalDate.now().toString(), "出发前购买胶卷")
        compose.setContent {
            LedgerTheme {
                LedgerScreen(LedgerState(loading = false, categories = presetCategories(),
                    events = listOf(LedgerEvent("trip", "北欧旅行")),
                    rows = listOf(LedgerRow(meal, "午餐", "餐饮", "北欧旅行"), LedgerRow(film, "胶卷购买", "摄影", "北欧旅行"))))
            }
        }
        capture("01-ledger")
        compose.onNodeWithText("事件", useUnmergedTree = true).performClick()
        compose.onNodeWithText("北欧旅行").performClick()
        compose.onNodeWithText("事件累计支出").assertIsDisplayed()
        capture("02-event")
        compose.onNodeWithText("统计", useUnmergedTree = true).performClick()
        compose.onNodeWithText("各类别占比").performScrollTo().assertIsDisplayed()
        capture("03-statistics")
        compose.onNodeWithText("流水", useUnmergedTree = true).performClick()
        compose.onNodeWithText("午餐", substring = false).performClick()
        compose.onNodeWithText("原币金额（NOK）").assertIsDisplayed()
        capture("04-manual-entry")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "previews")
        directory.mkdirs()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
