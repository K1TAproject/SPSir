package com.spsir.ledger

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlin.system.measureTimeMillis

class ReliabilityDataTest {
    @Test fun backupBoundaryUsesUtf8BytesAndRejectsOversizeInput() {
        val base = LedgerBackup("2026-10-05T00:00:00Z", presetCategories(), emptyList(), emptyList(), emptyList())
        val bytes = BackupJson.exportBytes(base)
        val exact = bytes + ByteArray(BackupJson.MAX_BYTES - bytes.size) { 32 }
        assertEquals(base, BackupJson.read(ByteArrayInputStream(exact)))
        try { BackupJson.read(ByteArrayInputStream(exact + byteArrayOf(32))); fail("Oversize input accepted") }
        catch (_: IllegalArgumentException) {}
        val entries = (0 until 15000).map { LedgerEntry("$it", "expense", 100, "CNY", 100, "food.lunch", null, "2026-10-01", "中".repeat(500)) }
        try { BackupJson.exportBytes(base.copy(entries = entries)); fail("Oversize export accepted") }
        catch (_: IllegalArgumentException) {}
    }

    @Test fun largeLedgerReadStatisticsAndBackupRoundTrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "benchmark-${UUID.randomUUID()}.db"
        val db = LedgerDatabase.open(context, name)
        val file = File(context.cacheDir, "$name.json")
        try {
            db.dao().seed(presetCategories())
            for (count in listOf(10000, 50000)) {
                db.dao().clearEntries()
                val entries = (0 until count).map { LedgerEntry("$it", "expense", 1234, "CNY", 1234, "food.lunch", null, "2026-10-${(it % 28 + 1).toString().padStart(2, '0')}", "午餐") }
                db.dao().insertEntries(entries)
                lateinit var state: LedgerState
                val read = measureTimeMillis { state = db.readLedgerState() }
                val stats = measureTimeMillis {
                    assertEquals(count * 1234L, expenseTotal(state.rows))
                    assertEquals(count * 1234L, categoryShares(state.rows, state.categories, "expense").sumOf { it.amount })
                    assertEquals(count, filterRows(state.rows, state.categories, LedgerFilter(query = "午餐")).size)
                }
                val store = BackupStore(db, file)
                var bytes = 0
                val backup = measureTimeMillis {
                    val encoded = BackupJson.exportBytes(store.snapshot()); bytes = encoded.size
                    assertEquals(count, BackupJson.read(ByteArrayInputStream(encoded)).entries.size)
                }
                Log.i("SPSirBenchmark", "rows=$count readMs=$read statsMs=$stats backupMs=$backup bytes=$bytes")
            }
        } finally { db.close(); context.deleteDatabase(name); file.delete() }
    }

    @Test fun snapshotKeepsPublicAndPersonalAmountsInAgreement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "snapshot-${UUID.randomUUID()}.db"
        val db = LedgerDatabase.open(context, name)
        try {
            db.dao().seed(presetCategories())
            val repo = PublicRepository(db)
            repo.create("旅行", listOf("甲"))
            val b = db.publicDao().all().single()
            repeat(10) { index ->
                repo.save(PublicDraft(EntryDraft(id = "meal", amount = "${index + 1}", eventId = b.event.eventId), b.members.single { it.isSelf }.id, b.members.map { it.id }.toSet()))
                val state = db.readLedgerState()
                assertEquals(state.publicEvents.single().balances().single { it.member.isSelf }.share, expenseTotal(state.rows))
            }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
