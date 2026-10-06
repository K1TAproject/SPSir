package com.spsir.ledger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class TransferDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun check(block: suspend (LedgerDatabase, PublicRepository, EventRepository, BackupStore, PublicBundle) -> Unit) = runBlocking {
        val name = "transfer-${UUID.randomUUID()}.db"; val file = File(context.cacheDir, "$name.json")
        val db = LedgerDatabase.open(context, name)
        try {
            db.dao().seed(presetCategories())
            val public = PublicRepository(db); public.create("旅行", listOf("甲"))
            val b = db.publicDao().all().single()
            public.save(PublicDraft(EntryDraft(id = "meal", amount = "600", eventId = b.event.eventId), b.members.single { it.isSelf }.id, b.members.map { it.id }.toSet()))
            block(db, public, EventRepository(db), BackupStore(db, file), db.publicDao().all().single())
        } finally { db.close(); context.deleteDatabase(name); file.delete() }
    }
    private fun draft(b: PublicBundle) = TransferDraft(id = "transfer", eventId = b.event.eventId,
        fromId = b.members.single { !it.isSelf }.id, toId = b.members.single { it.isSelf }.id, amount = "100")
    private suspend fun rejected(action: suspend () -> Unit) {
        try { action(); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
    }

    @Test fun transferCrudArchiveAndMemberProtectionLeavePersonalLedgerUntouched() = check { db, public, events, _, b ->
        val original = db.dao().allEntries()
        val d = draft(b); events.save(d)
        assertEquals(20000L, db.publicDao().all().single().balances().single { it.member.isSelf }.balance)
        public.member(b.event.eventId, d.fromId, "同行者")
        assertEquals(d.fromId, db.publicDao().all().single().transfers.single().fromId)
        rejected { public.deleteMember(b.event.eventId, d.fromId) }
        rejected { events.save(d.copy(amount = "450")) }
        events.save(d.copy(amount = "450"), true)
        assertEquals(-15000L, db.publicDao().all().single().balances().single { it.member.isSelf }.balance)
        rejected { events.archive(b.event.eventId, true) }
        events.archive(b.event.eventId, true, true)
        rejected { events.save(d) }; rejected { events.delete(b.event.eventId, d.id) }
        rejected { public.member(b.event.eventId, null, "乙") }
        rejected { public.delete(b.event.eventId, "meal") }
        rejected { public.save(PublicDraft(EntryDraft(id = "meal", amount = "700", eventId = b.event.eventId), d.toId, b.members.map { it.id }.toSet())) }
        rejected { LedgerRepository(db.dao()).save(original.single().toDraft().copy(eventId = null)) }
        events.rename(b.event.eventId, "旅行改名")
        assertEquals("旅行改名", db.dao().event(b.event.eventId)!!.name)
        assertEquals(original, db.dao().allEntries())
        events.archive(b.event.eventId, false)
        events.delete(b.event.eventId, d.id)
        assertTrue(db.publicDao().all().single().transfers.isEmpty())
        events.save(d.copy(amount = "300"))
        assertEquals("已结清", db.publicDao().all().single().settlementStatus())
        assertEquals(original, db.dao().allEntries())
        // Transfer-only member links also prevent deletion, without any expense participation.
        public.member(b.event.eventId, null, "乙")
        val other = db.publicDao().all().single().members.single { it.name == "乙" }
        events.save(d.copy(id = "indirect", toId = other.id), true)
        rejected { public.deleteMember(b.event.eventId, other.id) }
    }

    @Test fun personalArchivedEntriesCannotMoveChangeOrDelete() = check { db, _, events, _, _ ->
        db.dao().insertEvent(LedgerEvent("personal", "摄影"))
        val repo = LedgerRepository(db.dao())
        val d = EntryDraft(id = "film", amount = "80", eventId = "personal")
        repo.save(d); events.archive("personal", true)
        rejected { repo.save(d.copy(eventId = null)) }; rejected { repo.delete("film") }
        rejected { repo.save(d.copy(id = "new")) }
        assertEquals(8000L, db.dao().entry("film")!!.rmbMinor)
        events.archive("personal", false); repo.save(d.copy(amount = "90")); repo.delete("film")
        assertNull(db.dao().entry("film"))
    }

    @Test fun versionThreeBackupRoundTripLegacyAndInvalidTransferRollback() = check { db, _, events, store, b ->
        events.save(draft(b)); events.archive(b.event.eventId, true, true)
        val original = store.snapshot(); val text = BackupJson.encode(original)
        assertEquals(original, BackupJson.decode(text))
        store.restore(BackupJson.decode(text)); assertEquals(original.entries, store.snapshot().entries)
        assertEquals(original.events, store.snapshot().events); assertEquals(original.publicEvents, store.snapshot().publicEvents)
        val invalid = listOf<(JSONObject) -> Unit>(
            { it.getJSONArray("events").getJSONObject(0).put("archived", "true") },
            { it.getJSONArray("publicEvents").getJSONObject(0).getJSONArray("transfers").getJSONObject(0).put("fromId", "missing") },
            { it.getJSONArray("publicEvents").getJSONObject(0).getJSONArray("transfers").getJSONObject(0).put("eventId", "wrong") },
            { it.getJSONArray("publicEvents").getJSONObject(0).getJSONArray("transfers").getJSONObject(0).put("amountMinor", 0) },
            { it.getJSONArray("publicEvents").getJSONObject(0).getJSONArray("transfers").getJSONObject(0).put("occurredOn", "2026-02-30") },
            { val a = it.getJSONArray("publicEvents").getJSONObject(0).getJSONArray("transfers"); a.put(a.getJSONObject(0)) },
        )
        invalid.forEach { change -> val json = JSONObject(text); change(json); rejected { store.restore(BackupJson.decode(json.toString())) } }
        assertEquals(original.publicEvents, store.snapshot().publicEvents)
        // An actual insert error must roll back all tables, retaining the safety snapshot.
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_transfer BEFORE INSERT ON event_transfers BEGIN SELECT RAISE(ABORT, 'test'); END")
        try { store.restore(original); fail() } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(original.publicEvents, store.snapshot().publicEvents)
        assertEquals(original.events, store.readSafety().events)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_transfer")
        val legacy = JSONObject(text).put("version", 2)
        legacy.getJSONArray("events").getJSONObject(0).remove("archived")
        legacy.getJSONArray("publicEvents").getJSONObject(0).remove("transfers")
        store.restore(BackupJson.decode(legacy.toString()))
        assertFalse(db.dao().allEvents().single().archived); assertTrue(db.publicDao().all().single().transfers.isEmpty())
        assertEquals(original.entries, db.dao().allEntries())
    }

    @Test fun versionThreeDatabaseMigratesWithPublicLinksAndAmountsIntact() = runBlocking {
        val name = "v3-${UUID.randomUUID()}.db"
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open("com.spsir.ledger.LedgerDatabase/3.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, 0, null).use { sql ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i); sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                val indices = e.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            val queries = schema.getJSONArray("setupQueries"); for (i in 0 until queries.length()) sql.execSQL(queries.getString(i))
            sql.execSQL("INSERT INTO categories VALUES ('food','饮食',NULL,'expense',0)")
            sql.execSQL("INSERT INTO categories VALUES ('food.lunch','午餐','food','expense',1)")
            sql.execSQL("INSERT INTO events VALUES ('trip','旧旅行')")
            sql.execSQL("INSERT INTO entries VALUES ('e','expense',100,'CNY',100,'food.lunch','trip','2026-10-01','旧记录')")
            sql.execSQL("INSERT INTO public_events VALUES ('trip')")
            sql.execSQL("INSERT INTO event_members VALUES ('me','trip','我',1,0)")
            sql.execSQL("INSERT INTO public_expenses VALUES ('p','trip','me',100,'CNY',100,'food.lunch','2026-10-01','旧记录','e')")
            sql.execSQL("INSERT INTO expense_members VALUES ('p','me')"); sql.version = 3
        }
        val db = LedgerDatabase.open(context, name)
        try {
            val b = db.publicDao().all().single()
            assertEquals(4, db.openHelper.readableDatabase.version)
            assertFalse(db.dao().allEvents().single().archived)
            assertEquals("e", b.expenses.single().expense.entryId)
            assertEquals(100L, db.dao().allEntries().single().rmbMinor)
            assertTrue(b.transfers.isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun textExportIsUtf8AndProviderReadableWithoutChangingLedger() = check { db, _, _, store, b ->
        val before = store.snapshot()
        val text = EventBill(db.dao().allEvents().single(), b, presetCategories()).text()
        val source = cacheEventBill(context, text)
        val destination = File(context.cacheDir, "bill-${UUID.randomUUID()}.txt")
        try {
            saveEventBill(context.contentResolver, android.net.Uri.fromFile(destination), source)
            assertArrayEquals(text.toByteArray(Charsets.UTF_8), destination.readBytes())
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", source)
            assertEquals(text, context.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
            try { saveEventBill(context.contentResolver, android.net.Uri.parse("content://invalid.spsir.test/absent"), source); fail() }
            catch (_: java.io.FileNotFoundException) {}
            assertEquals(before.entries, store.snapshot().entries)
            assertEquals(before.publicEvents, store.snapshot().publicEvents)
        } finally { source.delete(); destination.delete() }
    }
}
