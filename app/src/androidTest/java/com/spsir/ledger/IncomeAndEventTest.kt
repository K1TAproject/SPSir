package com.spsir.ledger

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class IncomeAndEventTest {
    @get:Rule val compose = createComposeRule()

    @Test fun incomeCategorySurvivesFormRestorationAndSave() {
        var saved: EntryDraft? = null
        val restore = StateRestorationTester(compose)
        restore.setContent { LedgerTheme { EntryEditor(EntryDraft(kind = "income", categoryId = "income", amount = "100"),
            false, LedgerState(loading = false, categories = presetCategories()), {}, { saved = it }, {}, {}) } }
        compose.onAllNodesWithText("收入", substring = false).onLast().performScrollTo().performClick()
        compose.onNodeWithText("家教费", substring = false).performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("家教费").assertExists()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.runOnIdle { assertEquals("income.tutoring", saved!!.categoryId) }
    }

    @Test fun incomeBackupAndOldPresetUpgradeKeepHistoricalRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "income-test-${UUID.randomUUID()}"
        val db = LedgerDatabase.open(context, name)
        try {
            val old = presetCategories().filterNot { it.id.startsWith("income.") }
            db.dao().seed(old)
            val repo = LedgerRepository(db.dao())
            repo.save(EntryDraft(id = "old", kind = "income", categoryId = "income", amount = "50"))
            db.dao().seed(presetCategories())
            repo.save(EntryDraft(id = "new", kind = "income", categoryId = "income.cashback", amount = "12.50"))
            assertEquals("income", db.dao().entry("old")!!.categoryId)
            assertEquals("income.cashback", db.dao().entry("new")!!.categoryId)
            val data = LedgerBackup("2026-10-11T00:00:00Z", presetCategories(), emptyList(),
                listOf(db.dao().entry("old")!!, db.dao().entry("new")!!), emptyList())
            assertEquals(data, BackupJson.decode(BackupJson.encode(data)))
            val legacy = data.copy(categories = old, entries = data.entries.take(1))
            for (version in 1..3) {
                val json = JSONObject(BackupJson.encode(legacy)).put("version", version)
                assertEquals(legacy, BackupJson.decode(json.toString()))
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }

}
