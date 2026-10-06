package com.spsir.ledger

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class TransferUiTest {
    @get:Rule val compose = createComposeRule()
    private val members = listOf(EventMember("me", "trip", "我", true, 0), EventMember("a", "trip", "甲", false, 1))
    private fun bundle() = PublicBundle(PublicEvent("trip"), members, listOf(SharedExpense(
        PublicExpense("meal", "trip", "me", 60000, "CNY", 60000, "food.lunch", LocalDate.now().toString(), "私人备注", "entry"),
        members.map { ExpenseMember("meal", it.id) })))
    private fun state(): LedgerState {
        val b = bundle(); val entry = b.personalEntry(b.expenses.single(), "entry")!!
        return LedgerState(loading = false, categories = presetCategories(), events = listOf(LedgerEvent("trip", "旅行")),
            rows = listOf(LedgerRow(entry, "午餐", "饮食", "旅行")), publicEvents = listOf(b))
    }
    private fun scroll(text: String, substring: Boolean = false) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = substring))
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val i = InstrumentationRegistry.getInstrumentation()
        val dir = File(i.targetContext.getExternalFilesDir(null), "v043").apply { mkdirs() }
        val bitmap = requireNotNull(i.uiAutomation.takeScreenshot())
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun suggestionPrefillsThenRestoresAndConfirmsExcessBeforeSaving() {
        val restore = StateRestorationTester(compose)
        var saved: TransferDraft? = null; var confirmed = false
        var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
        restore.setContent { keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current; LedgerTheme { LedgerScreen(state(), saveTransfer = { d, c, done -> saved = d; confirmed = c; done() }) } }
        compose.onNodeWithText("事件", substring = false).performClick()
        compose.onNodeWithText("旅行", substring = false).performClick()
        compose.onNodeWithText("结算", substring = false).performClick()
        scroll("甲 → 我", true)
        compose.onNodeWithText("甲 → 我", substring = true).performClick()
        compose.onNodeWithText("转账金额（人民币）").performTextReplacement("450")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("450").assertExists()
        capture("01-transfer-form")
        compose.runOnIdle { keyboard?.hide() }
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.onNodeWithText("确认超额转账？").assertIsDisplayed()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText("确认保存").performClick()
        compose.runOnIdle { assertEquals("450", saved!!.amount); assertEquals("a", saved!!.fromId); assertEquals("me", saved!!.toId); assertTrue(confirmed) }
    }

    @Test fun archiveWarningFiltersEventsAndLocksPersonalEntryRoute() {
        var data by mutableStateOf(state())
        compose.setContent { LedgerTheme { LedgerScreen(data, archiveEvent = { id, archived, confirmed, done ->
            assertTrue(confirmed || !archived)
            data = data.copy(events = data.events.map { if (it.id == id) it.copy(archived = archived) else it }); done()
        }) } }
        compose.onNodeWithText("事件", substring = false).performClick()
        compose.onNodeWithText("旅行", substring = false).performClick()
        compose.onNodeWithText("更多", substring = false).performClick()
        compose.onNodeWithText("归档", substring = false).performClick()
        compose.onNodeWithText("尚未结清，仍要归档？").assertIsDisplayed()
        compose.onNodeWithText("确认归档").performClick()
        compose.onNodeWithText("＋ 记开支").assertDoesNotExist()
        capture("02-archived-event")
        compose.onNodeWithText("返回", substring = false).performClick()
        compose.onNodeWithText("旅行", substring = false).assertDoesNotExist()
        compose.onNodeWithText("已归档", substring = false).performClick()
        compose.onNodeWithText("旅行", substring = false).assertExists()
        compose.onNodeWithText("流水", substring = false).performClick()
        scroll("私人备注")
        compose.onNodeWithText("私人备注", substring = false).performClick()
        compose.onNodeWithText("事件已归档，请先取消归档").assertExists()
        compose.onNodeWithText("保存", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("删除公共开支", substring = false).assertDoesNotExist()
    }

    @Test fun billPreviewKeepsSnapshotAndRequiresOptInForNotes() {
        var b by mutableStateOf(bundle())
        compose.setContent { LedgerTheme { EventBillDialog(LedgerEvent("trip", "旅行"), b, presetCategories()) {} } }
        compose.onNodeWithText("私人备注", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("bill-details").performClick()
        compose.onNodeWithText("私人备注", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("bill-notes").performClick()
        compose.onNodeWithText("私人备注", substring = true).assertExists()
        compose.runOnIdle { b = b.copy(expenses = b.expenses.map { it.copy(expense = it.expense.copy(rmbMinor = 99900, originalMinor = 99900)) }) }
        compose.onNodeWithText("事件总支出：¥ 600.00", substring = true).assertExists()
        compose.onNodeWithText("事件总支出：¥ 999.00", substring = true).assertDoesNotExist()
        capture("03-bill-preview")
    }

    @Test fun largeFontTransferSupportsKeyboardAndDeleteConfirmation() {
        var deleted = false
        var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
        val t = EventTransfer("t", "trip", "a", "me", 10000, LocalDate.now().toString(), "")
        compose.setContent { keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current; CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
            Box(Modifier.width(320.dp)) { LedgerTheme { TransferEditor(t.toDraft(), true, bundle().copy(transfers = listOf(t)), state(), {}, { _, _ -> }, { deleted = true }) } }
        } }
        compose.onNodeWithText("转账金额（人民币）").performScrollTo().performClick().performTextReplacement("125")
        compose.runOnIdle { keyboard?.show() }
        android.os.SystemClock.sleep(750) // Let the platform IME animation finish before the screenshot.
        capture("04-transfer-large-font-keyboard")
        compose.onNodeWithText("删除转账", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("删除这笔转账？").assertIsDisplayed()
        compose.runOnIdle { assertFalse(deleted) }
        compose.onNodeWithText("确认删除").performClick()
        compose.runOnIdle { assertTrue(deleted) }
    }
    @Test fun shareAndDocumentResultsAreUserInitiatedAndRecoverFromCancelOrFailure() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val target = File(instrument.targetContext.cacheDir, "test-export.txt")
        var mode = "cancel"
        var shared: android.content.Intent? = null
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: android.content.Intent): android.app.Instrumentation.ActivityResult? {
                if (intent.action == android.content.Intent.ACTION_CHOOSER) {
                    shared = intent
                    return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
                }
                if (intent.action == android.content.Intent.ACTION_CREATE_DOCUMENT) {
                    if (mode == "cancel") return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
                    val uri = if (mode == "success") android.net.Uri.fromFile(target) else android.net.Uri.parse("content://invalid.spsir.test/absent")
                    return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, android.content.Intent().setData(uri))
                }
                return null
            }
        }
        instrument.addMonitor(monitor)
        try {
            compose.setContent { LedgerTheme { EventBillDialog(LedgerEvent("trip", "旅行"), bundle(), presetCategories()) {} } }
            compose.onNodeWithText("保存文本").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasText("保存文本") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            assertFalse(target.exists())
            mode = "failure"
            compose.onNodeWithText("保存文本").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("保存失败，请重新选择位置").fetchSemanticsNodes().isNotEmpty() }
            mode = "success"
            compose.onNodeWithText("保存文本").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("账单已保存").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(target.readText(Charsets.UTF_8).contains("事件总支出：¥ 600.00"))
            assertFalse(target.readText(Charsets.UTF_8).contains("私人备注"))
            compose.onNodeWithText("分享", substring = false).performScrollTo().performClick()
            compose.waitUntil(5000) { shared != null }
            @Suppress("DEPRECATION")
            val send = shared!!.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
            assertEquals(android.content.Intent.ACTION_SEND, send.action)
            assertEquals("text/plain", send.type)
            assertTrue(send.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            @Suppress("DEPRECATION")
            val uri = send.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)!!
            val sharedText = instrument.targetContext.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
            assertTrue(sharedText.contains("剩余应收 ¥ 300.00")); assertFalse(sharedText.contains("私人备注"))
            compose.waitUntil(5000) { compose.onAllNodes(hasText("关闭") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        } finally { instrument.removeMonitor(monitor); target.delete() }
    }
}
