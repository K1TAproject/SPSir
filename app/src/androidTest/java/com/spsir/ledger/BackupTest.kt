package com.spsir.ledger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun sample() = LedgerBackup("2026-10-03T00:00:00Z", presetCategories(), listOf(LedgerEvent("trip", "旅行")),
        listOf(LedgerEntry("film", "expense", 99999999900L, "NOK", 8123, "photo.film", "trip", "2026-09-30", "胶卷"),
            LedgerEntry("salary", "income", 123456, "CNY", 123456, "income", null, "2026-10-01", "收入")),
        listOf(HiddenCategory("photo"), HiddenCategory("food.lunch")))

    @Test fun jsonRoundTripIsExactAndRejectsCorruptData() {
        val text = BackupJson.encode(sample())
        assertEquals(sample(), BackupJson.decode(text))
        val invalid = listOf<(JSONObject) -> Unit>(
            { it.put("version", 99) },
            { it.getJSONArray("entries").put(it.getJSONArray("entries").getJSONObject(0)) },
            { it.getJSONArray("entries").getJSONObject(0).put("originalMinor", 1.5) },
            { it.getJSONArray("entries").getJSONObject(0).put("eventId", "missing") },
            { it.getJSONArray("entries").getJSONObject(0).put("categoryId", "missing") },
            { it.getJSONArray("entries").getJSONObject(0).put("currency", "INVALID") },
            { it.getJSONArray("entries").getJSONObject(0).put("occurredOn", "2026-02-30") },
            { it.getJSONArray("entries").getJSONObject(0).remove("note") },
            { it.getJSONArray("hiddenCategories").put("missing") },
        )
        invalid.forEach { change ->
            val root = JSONObject(text); change(root)
            try { BackupJson.decode(root.toString()); fail("Accepted invalid backup") } catch (_: IllegalArgumentException) {}
        }
        try { BackupJson.decode("not json"); fail("Accepted corrupt file") } catch (_: IllegalArgumentException) {}
    }

    @Test fun restorePreservesDataAcrossReopenAndKeepsRecoverablePreviousCopy() = runBlocking {
        val name = "backup-${UUID.randomUUID()}.db"
        val file = File(context.cacheDir, "$name.json")
        var db = LedgerDatabase.open(context, name)
        try {
            db.dao().seed(presetCategories())
            LedgerRepository(db.dao()).save(EntryDraft(id = "old", amount = "12.34"))
            var store = BackupStore(db, file)
            val before = store.snapshot()
            store.restore(sample())
            assertEquals(before.entries, store.readSafety().entries)
            db.close(); db = LedgerDatabase.open(context, name); store = BackupStore(db, file)
            val restored = store.snapshot()
            assertEquals(sample().entries, restored.entries)
            assertEquals(sample().events, restored.events)
            assertEquals(sample().hidden.sortedBy { it.categoryId }, restored.hidden)
            store.restore(store.readSafety())
            assertEquals(before.entries, store.snapshot().entries)
            assertEquals(sample().entries, store.readSafety().entries)
        } finally { db.close(); context.deleteDatabase(name); file.delete() }
    }

    @Test fun failedInsertRollsBackAllTablesAndFailedSafetyWriteNeverDeletesData() = runBlocking {
        val name = "rollback-${UUID.randomUUID()}.db"
        val file = File(context.cacheDir, "$name.json")
        val db = LedgerDatabase.open(context, name)
        try {
            db.dao().seed(presetCategories())
            LedgerRepository(db.dao()).save(EntryDraft(id = "old", amount = "9.99"))
            db.dao().hide(HiddenCategory("food"))
            val store = BackupStore(db, file)
            val before = store.snapshot()
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_film BEFORE INSERT ON entries WHEN NEW.id = 'film' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            try { store.restore(sample()); fail("Expected rollback") } catch (_: android.database.sqlite.SQLiteException) {}
            val after = store.snapshot()
            assertEquals(before.entries, after.entries); assertEquals(before.events, after.events)
            assertEquals(before.categories, after.categories); assertEquals(before.hidden, after.hidden)
            val badStore = BackupStore(db, File(file, "child.json")) // Parent is the existing safety file, not a directory.
            try { badStore.restore(sample()); fail("Expected backup failure") } catch (_: java.io.IOException) {}
            assertEquals(before.entries, store.snapshot().entries)
        } finally { db.close(); context.deleteDatabase(name); file.delete() }
    }

    @Test fun versionOneMigrationKeepsExistingRecords() = runBlocking {
        val name = "migration-${UUID.randomUUID()}.db"
        val schema = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.spsir.ledger.LedgerDatabase/1.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val sqlite = context.openOrCreateDatabase(name, 0, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
            sqlite.execSQL("INSERT INTO categories VALUES ('food','饮食',NULL,'expense',0)")
            sqlite.execSQL("INSERT INTO categories VALUES ('food.lunch','午餐','food','expense',1)")
            sqlite.execSQL("INSERT INTO events VALUES ('trip','旅行')")
            sqlite.execSQL("INSERT INTO entries VALUES ('old','expense',12000,'NOK',8123,'food.lunch','trip','2026-09-30','原记录')")
            sqlite.version = 1
        } finally { sqlite.close() }
        val db = LedgerDatabase.open(context, name)
        try {
            val entry = db.dao().allEntries().single()
            assertEquals(8123L, entry.rmbMinor); assertEquals("trip", entry.eventId); assertEquals("原记录", entry.note)
            assertTrue(db.dao().allHidden().isEmpty())
            db.dao().hide(HiddenCategory("food"))
            db.dao().seed(presetCategories())
            assertEquals(entry, db.dao().allEntries().single())
            assertEquals("food", db.dao().allHidden().single().categoryId)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
