package com.spsir.ledger

import androidx.room.*
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "event_transfers", foreignKeys = [
    ForeignKey(entity = PublicEvent::class, parentColumns = ["eventId"], childColumns = ["eventId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = EventMember::class, parentColumns = ["id"], childColumns = ["fromId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = EventMember::class, parentColumns = ["id"], childColumns = ["toId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("eventId"), Index("fromId"), Index("toId")])
data class EventTransfer(@PrimaryKey val id: String, val eventId: String, val fromId: String,
    val toId: String, val amountMinor: Long, val occurredOn: String, val note: String)

data class TransferDraft(val id: String = UUID.randomUUID().toString(), val eventId: String,
    val fromId: String, val toId: String, val amount: String = "",
    val date: String = LocalDate.now().toString(), val note: String = "")

fun EventTransfer.toDraft() = TransferDraft(id, eventId, fromId, toId, Money.format(amountMinor), occurredOn, note)

suspend fun LedgerDao.requireWritableEvent(id: String?) {
    if (id != null) require(requireNotNull(event(id)) { "事件不存在" }.archived.not()) { "事件已归档，请先取消归档" }
}

fun validateTransfer(t: EventTransfer, bundle: PublicBundle) {
    require(t.id.isNotBlank() && t.eventId == bundle.event.eventId) { "转账事件无效" }
    require(t.fromId != t.toId && bundle.members.any { it.id == t.fromId } && bundle.members.any { it.id == t.toId }) { "请选择不同的本事件成员" }
    Money.validateMinor(t.amountMinor)
    Money.date(t.occurredOn)
    require(t.note.length <= 500) { "备注最多 500 字" }
}

// Exclude the edited transfer before checking how much can still be settled.
fun PublicBundle.transferLimit(id: String, from: String, to: String): Long {
    val balances = copy(transfers = transfers.filterNot { it.id == id }).balances()
    val debt = balances.find { it.member.id == from }?.balance ?: 0L
    val credit = balances.find { it.member.id == to }?.balance ?: 0L
    return minOf(if (debt < 0) Math.negateExact(debt) else 0, credit.coerceAtLeast(0))
}

fun PublicBundle.settlementStatus(): String = when {
    expenses.isEmpty() && transfers.isEmpty() -> "暂无待结算费用"
    balances().all { it.balance == 0L } -> "已结清"
    else -> "待结算"
}

class EventRepository(private val db: LedgerDatabase) {
    suspend fun rename(id: String, name: String) = db.withTransaction {
        requireNotNull(db.dao().event(id)) { "事件不存在" }
        val value = name.trim()
        require(value.length in 1..40) { "事件名称请填写 1～40 字" }
        db.dao().renameEvent(id, value)
    }

    suspend fun archive(id: String, archived: Boolean, confirmed: Boolean = false) = db.withTransaction {
        requireNotNull(db.dao().event(id)) { "事件不存在" }
        val unsettled = db.publicDao().bundle(id)?.balances()?.any { it.balance != 0L } == true
        require(!archived || !unsettled || confirmed) { "事件尚未结清，请确认归档" }
        db.dao().archiveEvent(id, archived)
    }

    suspend fun save(draft: TransferDraft, confirmed: Boolean = false) = db.withTransaction {
        db.dao().requireWritableEvent(draft.eventId)
        val bundle = requireNotNull(db.publicDao().bundle(draft.eventId)) { "请选择公共事件" }
        val old = db.publicDao().transferById(draft.id)
        require(old == null || old.eventId == draft.eventId) { "不能迁移转账到其他事件" }
        val transfer = EventTransfer(draft.id, draft.eventId, draft.fromId, draft.toId,
            Money.parse(draft.amount), Money.date(draft.date), draft.note.trim())
        require(draft.note.length <= 500) { "备注最多 500 字" }
        validateTransfer(transfer, bundle)
        require(confirmed || transfer.amountMinor <= bundle.transferLimit(draft.id, draft.fromId, draft.toId)) { "转账超过当前可结算金额，请确认" }
        val updated = bundle.copy(transfers = bundle.transfers.filterNot { it.id == draft.id } + transfer)
        settlements(updated.balances())
        db.publicDao().transfer(transfer)
    }

    suspend fun delete(eventId: String, id: String) = db.withTransaction {
        db.dao().requireWritableEvent(eventId)
        require(db.publicDao().transferById(id)?.eventId == eventId) { "转账不存在" }
        db.publicDao().deleteTransfer(id)
    }
}

val transferSchema = listOf(
    "CREATE TABLE IF NOT EXISTS event_transfers (id TEXT NOT NULL PRIMARY KEY, eventId TEXT NOT NULL, fromId TEXT NOT NULL, toId TEXT NOT NULL, amountMinor INTEGER NOT NULL, occurredOn TEXT NOT NULL, note TEXT NOT NULL, FOREIGN KEY(eventId) REFERENCES public_events(eventId) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(fromId) REFERENCES event_members(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(toId) REFERENCES event_members(id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
    "CREATE INDEX IF NOT EXISTS index_event_transfers_eventId ON event_transfers(eventId)",
    "CREATE INDEX IF NOT EXISTS index_event_transfers_fromId ON event_transfers(fromId)",
    "CREATE INDEX IF NOT EXISTS index_event_transfers_toId ON event_transfers(toId)",
)
