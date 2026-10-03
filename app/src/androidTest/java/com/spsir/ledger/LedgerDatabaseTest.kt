package com.spsir.ledger

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LedgerDatabaseTest {
    @Test fun presetUpgradeIsIdempotentAndPreservesLegacyLedgerAndEvents() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${UUID.randomUUID()}.db"
        val db = LedgerDatabase.open(context, name)
        try {
            db.dao().seed(listOf(Category("food", "餐饮"), Category("food.snacks", "零食水果", "food"),
                Category("transport", "交通"), Category("transport.transit", "公交地铁", "transport"),
                Category("daily", "日用"), Category("daily.supplies", "生活用品", "daily")))
            db.dao().insertEvent(LedgerEvent("existing", "原有事件"))
            val repository = LedgerRepository(db.dao())
            listOf("food.snacks", "transport.transit", "daily.supplies").forEachIndexed { index, category ->
                repository.save(EntryDraft(id = "legacy-$index", amount = "12.34", categoryId = category, eventId = "existing"))
            }
            val before = db.dao().rows().first().map { it.entry }
            db.dao().seed(presetCategories())
            db.dao().seed(presetCategories())
            assertEquals(before, db.dao().rows().first().map { it.entry })
            assertEquals("购物", db.dao().category("daily")!!.name)
            assertEquals("公交地铁（原分类）", db.dao().category("transport.transit")!!.name)
            assertEquals(presetCategories().size, db.dao().categories().first().size)
            assertEquals(3702L, expenseTotal(db.dao().rows().first()))
            assertEquals("原有事件", db.dao().events().first().single().name)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun manualForeignExpensePersistsUpdatesAndAppearsOnceInBothViews() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${UUID.randomUUID()}.db"
        var db = LedgerDatabase.open(context, name)
        try {
            db.dao().seed(presetCategories())
            db.dao().insertEvent(LedgerEvent("trip", "北欧旅行"))
            val repository = LedgerRepository(db.dao())
            val draft = EntryDraft(id = "meal", amount = "120", currency = "NOK", rmb = "81.23",
                categoryId = "food.lunch", eventId = "trip", date = "2026-09-30")
            repository.save(draft)
            repository.save(draft) // Same save retried must not duplicate a record.
            assertEquals(1, db.dao().rows().first().size)
            db.close()
            db = LedgerDatabase.open(context, name)
            var rows = db.dao().rows().first()
            assertEquals(8123L, rows.single().entry.rmbMinor)
            assertEquals(12000L, rows.single().entry.originalMinor)
            assertEquals("北欧旅行", rows.single().eventName)
            assertEquals(expenseTotal(rows), expenseTotal(rows.filter { it.entry.eventId == "trip" }))

            LedgerRepository(db.dao()).save(draft.copy(rmb = "85.10", date = "2026-10-01"))
            rows = db.dao().rows().first()
            assertEquals(8510L, expenseTotal(rows))
            assertEquals(0, rows.count { it.entry.occurredOn.startsWith("2026-09") })

            LedgerRepository(db.dao()).save(draft.copy(eventId = null))
            assertNull(db.dao().rows().first().single().eventName)
            assertEquals(1, db.dao().rows().first().size)
            db.dao().deleteEntry("meal")
            assertTrue(db.dao().rows().first().isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun invalidManualInputNeverWritesAPartialEntry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test-${UUID.randomUUID()}.db"
        val db = LedgerDatabase.open(context, name)
        try {
            db.dao().seed(presetCategories())
            val repository = LedgerRepository(db.dao())
            val invalid = listOf(
                EntryDraft(amount = "1", currency = "EUR", rmb = ""),
                EntryDraft(amount = "1", eventId = "missing"),
                EntryDraft(amount = "1", categoryId = "income"),
                EntryDraft(amount = "1", date = "2026-02-30"),
            )
            invalid.forEach { draft ->
                try { repository.save(draft); fail("Invalid record was accepted") }
                catch (_: IllegalArgumentException) { /* Expected validation error. */ }
            }
            assertTrue(db.dao().rows().first().isEmpty())
            repository.save(EntryDraft(amount = "12.34", rmb = "999"))
            assertEquals(1234L, db.dao().rows().first().single().entry.rmbMinor)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
