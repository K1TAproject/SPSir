package com.spsir.ledger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class StatisticsScopePreferencesTest {
    @Test fun persistsAcrossInstancesAndOnlySuccessfulRestoreResetsPreference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "scope-${UUID.randomUUID()}"
        val db = LedgerDatabase.open(context, name)
        val file = File(context.cacheDir, "$name.json")
        val prefs = context.getSharedPreferences(name, 0)
        try {
            db.dao().seed(presetCategories())
            db.dao().insertEvent(LedgerEvent("trip", "旅行"))
            LedgerRepository(db.dao()).save(EntryDraft(id = "one", eventId = "trip", amount = "10"))
            val store = BackupStore(db, file)
            val scopes = StatisticsScopePreferences(prefs)
            assertEquals(StatisticsScope(), scopes.read())
            val selected = StatisticsScope("exclude", "trip")
            scopes.write(selected)
            assertEquals(selected, StatisticsScopePreferences(context.getSharedPreferences(name, 0)).read())
            val original = store.snapshot()
            assertFalse(BackupJson.encode(original).contains("statisticsScope"))
            // Previewing or cancelling restoration performs no preference write.
            BackupJson.decode(BackupJson.encode(original))
            assertEquals(selected, scopes.read())
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_entry BEFORE INSERT ON entries BEGIN SELECT RAISE(ABORT, 'test'); END")
            try { scopes.restore(store, original); fail("Expected insertion failure") } catch (_: android.database.sqlite.SQLiteException) {}
            assertEquals(selected, scopes.read())
            assertEquals(original.entries, store.snapshot().entries)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_entry")
            scopes.restore(store, original)
            assertEquals(StatisticsScope(), scopes.read())
            assertEquals(original.entries, store.snapshot().entries)
            assertEquals(original.entries, store.readSafety().entries)
        } finally { db.close(); context.deleteDatabase(name); context.deleteSharedPreferences(name); file.delete() }
    }
}
