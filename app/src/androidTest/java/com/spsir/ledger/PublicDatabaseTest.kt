package com.spsir.ledger

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PublicDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun checkDb(block: suspend (LedgerDatabase, PublicRepository, BackupStore) -> Unit) = runBlocking {
        val name = "public-${UUID.randomUUID()}.db"
        val db = LedgerDatabase.open(context, name)
        val file = File(context.cacheDir,"$name.json")
        try { db.dao().seed(presetCategories()); block(db, PublicRepository(db), BackupStore(db,file)) }
        finally { db.close(); context.deleteDatabase(name); file.delete() }
    }
    private suspend fun setup(db: LedgerDatabase, repo: PublicRepository): PublicBundle {
        repo.create("旅行",listOf("甲","乙")); return db.publicDao().all().single()
    }
    private fun draft(b: PublicBundle, id: String="meal") = PublicDraft(EntryDraft(id=id,amount="100",eventId=b.event.eventId), b.members.single { it.isSelf }.id,b.members.map { it.id }.toSet())
    @Test fun editsAndDeletionUpdateOnePersonalEntryAndProtectMembers() = checkDb { db,repo,_ ->
        val b=setup(db,repo); val d=draft(b)
        repo.save(d); repo.save(d)
        val first=db.dao().allEntries().single(); assertEquals(3334L,first.rmbMinor)
        repo.save(d.copy(entry=d.entry.copy(amount="120",date="2026-10-01",categoryId="photo.film"),payerId=b.members.single { it.name=="甲" }.id))
        val updated=db.dao().allEntries().single(); assertEquals(first.id,updated.id); assertEquals(4000L,updated.rmbMinor); assertEquals("photo.film",updated.categoryId)
        try { LedgerRepository(db.dao()).save(updated.toDraft()); fail() } catch (_: IllegalArgumentException) {}
        try { repo.deleteMember(b.event.eventId,b.members.single { it.name=="甲" }.id); fail() } catch (_: IllegalArgumentException) {}
        repo.save(d.copy(participants=d.participants - b.members.single { it.isSelf }.id))
        assertTrue(db.dao().allEntries().isEmpty())
        repo.save(d); assertEquals(1,db.dao().allEntries().size)
        repo.delete(b.event.eventId,d.entry.id)
        assertTrue(db.dao().allEntries().isEmpty()); assertTrue(db.publicDao().all().single().expenses.isEmpty())
    }
    @Test fun publicBackupRoundTripRejectsTamperingAndReadsLegacy() = checkDb { db,repo,store ->
        val b=setup(db,repo); repo.save(draft(b))
        val original=store.snapshot(); val text=BackupJson.encode(original)
        val decoded=BackupJson.decode(text); assertEquals(original,decoded)
        repo.member(b.event.eventId,null,"丙")
        store.restore(decoded)
        val after=store.snapshot(); assertEquals(original.publicEvents,after.publicEvents); assertEquals(original.entries,after.entries)
        assertEquals(4,store.readSafety().publicEvents.single().members.size)
        val changes=listOf<(JSONObject)->Unit>(
            { it.getJSONArray("entries").getJSONObject(0).put("rmbMinor",1) },
            { it.getJSONArray("publicEvents").getJSONObject(0).getJSONArray("members").getJSONObject(0).put("isSelf",false) },
            { it.getJSONArray("publicEvents").getJSONObject(0).getJSONArray("expenses").getJSONObject(0).put("payerId","missing") },
            { it.getJSONArray("publicEvents").getJSONObject(0).getJSONArray("expenses").getJSONObject(0).getJSONArray("participants").put("missing") },
        )
        changes.forEach { change -> try { val json=JSONObject(text); change(json); BackupJson.decode(json.toString()); fail() } catch (_: IllegalArgumentException) {} }
        val legacy=JSONObject(BackupJson.encode(LedgerBackup(original.exportedAt,presetCategories(),emptyList(),emptyList(),emptyList())))
        legacy.put("version",1); legacy.remove("publicEvents")
        store.restore(BackupJson.decode(legacy.toString())); assertTrue(db.publicDao().all().isEmpty())
    }
    @Test fun failedSaveAndRestoreRollBackPublicAndPersonalData() = checkDb { db,repo,store ->
        val b=setup(db,repo); val d=draft(b); repo.save(d)
        val before=store.snapshot()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_participant BEFORE INSERT ON expense_members BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repo.save(d.copy(entry=d.entry.copy(amount="200"))); fail() } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(before.entries,store.snapshot().entries); assertEquals(before.publicEvents,store.snapshot().publicEvents)
        try { store.restore(before); fail() } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(before.entries,store.snapshot().entries); assertEquals(before.publicEvents,store.snapshot().publicEvents)
    }
    @Test fun foreignZeroSharesRestoreAndInvalidMembersNeverWrite() = checkDb { db,repo,store ->
        val b=setup(db,repo); val d=draft(b)
        repo.save(d.copy(entry=d.entry.copy(amount="1",currency="JPY",rmb="0.01"),participants=b.members.filter { !it.isSelf }.map { it.id }.toSet()))
        assertTrue(db.dao().allEntries().isEmpty())
        // Change the self member's stable order so its original share rounds to zero but RMB remains positive.
        val self=b.members.single { it.isSelf }; db.publicDao().member(self.copy(position=3))
        repo.save(d.copy(entry=d.entry.copy(amount="1",currency="JPY",rmb="0.05")))
        val e=db.dao().allEntries().single(); assertEquals(0L,e.originalMinor); assertEquals(1L,e.rmbMinor)
        store.restore(BackupJson.decode(BackupJson.encode(store.snapshot())))
        assertEquals(e,db.dao().allEntries().single())
        try { repo.save(d.copy(participants=setOf("missing"))); fail() } catch (_: IllegalArgumentException) {}
        try { repo.member(b.event.eventId,null,"我"); fail() } catch (_: IllegalArgumentException) {}
        assertEquals(e,db.dao().allEntries().single())
    }
    @Test fun versionTwoMigrationKeepsOldPersonalData() = runBlocking {
        val name="v2-${UUID.randomUUID()}.db"
        val schema=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("com.spsir.ledger.LedgerDatabase/2.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name,0,null).use { sql ->
            val entities=schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e=entities.getJSONObject(i); sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
                val indices=e.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName")))
            }
            val setup=schema.getJSONArray("setupQueries"); for (i in 0 until setup.length()) sql.execSQL(setup.getString(i))
            sql.execSQL("INSERT INTO categories VALUES ('food','饮食',NULL,'expense',0)")
            sql.execSQL("INSERT INTO categories VALUES ('food.lunch','午餐','food','expense',1)")
            sql.execSQL("INSERT INTO events VALUES ('old','旧旅行')")
            sql.execSQL("INSERT INTO entries VALUES ('e','expense',123,'CNY',123,'food.lunch','old','2026-10-01','旧记录')")
            sql.execSQL("INSERT INTO hidden_categories VALUES ('food')"); sql.version=2
        }
        val db=LedgerDatabase.open(context,name)
        try {
            assertEquals(123L,db.dao().allEntries().single().rmbMinor); assertEquals("old",db.dao().allEntries().single().eventId)
            assertEquals("food",db.dao().allHidden().single().categoryId); assertTrue(db.publicDao().all().isEmpty())
            assertEquals(4,db.openHelper.readableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
