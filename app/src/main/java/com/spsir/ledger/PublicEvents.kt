package com.spsir.ledger

import androidx.room.*
import java.util.UUID

@Entity(tableName = "public_events", foreignKeys = [ForeignKey(entity = LedgerEvent::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.RESTRICT)])
data class PublicEvent(@PrimaryKey val eventId: String)

@Entity(tableName = "event_members", foreignKeys = [ForeignKey(entity = PublicEvent::class, parentColumns = ["eventId"], childColumns = ["eventId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("eventId"), Index(value = ["eventId", "name"], unique = true), Index(value = ["eventId", "position"], unique = true)])
data class EventMember(@PrimaryKey val id: String, val eventId: String, val name: String, val isSelf: Boolean, val position: Int)

@Entity(tableName = "public_expenses", foreignKeys = [
    ForeignKey(entity = PublicEvent::class, parentColumns = ["eventId"], childColumns = ["eventId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = EventMember::class, parentColumns = ["id"], childColumns = ["payerId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = LedgerEntry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("eventId"), Index("payerId"), Index("categoryId"), Index(value = ["entryId"], unique = true)])
data class PublicExpense(@PrimaryKey val id: String, val eventId: String, val payerId: String,
    val originalMinor: Long, val currency: String, val rmbMinor: Long, val categoryId: String,
    val occurredOn: String, val note: String, val entryId: String? = null)

@Entity(tableName = "expense_members", primaryKeys = ["expenseId", "memberId"], foreignKeys = [
    ForeignKey(entity = PublicExpense::class, parentColumns = ["id"], childColumns = ["expenseId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = EventMember::class, parentColumns = ["id"], childColumns = ["memberId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("memberId")])
data class ExpenseMember(val expenseId: String, val memberId: String)

data class SharedExpense(@Embedded val expense: PublicExpense,
    @Relation(parentColumn = "id", entityColumn = "expenseId") val participants: List<ExpenseMember>)
data class PublicBundle(@Embedded val event: PublicEvent,
    @Relation(parentColumn = "eventId", entityColumn = "eventId") val members: List<EventMember>,
    @Relation(entity = PublicExpense::class, parentColumn = "eventId", entityColumn = "eventId") val expenses: List<SharedExpense>,
    @Relation(parentColumn = "eventId", entityColumn = "eventId") val transfers: List<EventTransfer> = emptyList())

@Dao
interface PublicDao {
    @Upsert suspend fun transfer(transfer: EventTransfer)
    @Query("SELECT * FROM event_transfers WHERE id = :id") suspend fun transferById(id: String): EventTransfer?
    @Query("DELETE FROM event_transfers WHERE id = :id") suspend fun deleteTransfer(id: String)
    @Query("DELETE FROM event_transfers") suspend fun clearTransfers()

    @Transaction @Query("SELECT * FROM public_events ORDER BY eventId") suspend fun all(): List<PublicBundle>
    @Transaction @Query("SELECT * FROM public_events WHERE eventId = :id") suspend fun bundle(id: String): PublicBundle?
    @Query("SELECT * FROM public_expenses WHERE entryId = :id") suspend fun fromEntry(id: String): PublicExpense?
    @Query("SELECT * FROM public_expenses WHERE id = :id") suspend fun expenseById(id: String): PublicExpense?
    @Insert suspend fun insertEvent(event: PublicEvent)
    @Upsert suspend fun member(member: EventMember)
    @Upsert suspend fun expense(expense: PublicExpense)
    @Insert suspend fun participants(values: List<ExpenseMember>)
    @Query("DELETE FROM expense_members WHERE expenseId = :id") suspend fun clearParticipants(id: String)
    @Query("DELETE FROM public_expenses WHERE id = :id") suspend fun deleteExpense(id: String)
    @Query("DELETE FROM event_members WHERE id = :id") suspend fun deleteMember(id: String)
    @Query("DELETE FROM expense_members") suspend fun clearParticipants()
    @Query("DELETE FROM public_expenses") suspend fun clearExpenses()
    @Query("DELETE FROM event_members") suspend fun clearMembers()
    @Query("DELETE FROM public_events") suspend fun clearEvents()
}

fun splitAmount(amount: Long, members: List<EventMember>): Map<String, Long> {
    require(amount >= 0 && members.isNotEmpty()) { "请选择参与者" }
    val ordered = members.sortedWith(compareBy<EventMember> { it.position }.thenBy { it.id })
    require(ordered.map { it.id }.distinct().size == ordered.size) { "参与者重复" }
    val base = amount / ordered.size
    val remainder = amount % ordered.size
    return ordered.mapIndexed { index, member -> member.id to base + if (index < remainder) 1L else 0L }.toMap()
}

fun SharedExpense.shares(bundle: PublicBundle, original: Boolean = false): Map<String, Long> =
    splitAmount(if (original) expense.originalMinor else expense.rmbMinor,
        bundle.members.filter { m -> participants.any { it.memberId == m.id } })

fun PublicBundle.personalEntry(row: SharedExpense, id: String): LedgerEntry? {
    val self = members.single { it.isSelf }.id
    val original = row.shares(this, true)[self] ?: 0L
    val rmb = row.shares(this)[self] ?: 0L
    if (original == 0L && rmb == 0L) return null
    val e = row.expense
    return LedgerEntry(id, "expense", original, e.currency, rmb, e.categoryId, e.eventId, e.occurredOn, e.note)
}

data class MemberBalance(val member: EventMember, val paid: Long, val share: Long, val sent: Long = 0, val received: Long = 0) {
    val balance: Long get() = Math.subtractExact(Math.addExact(Math.subtractExact(paid, share), sent), received)
}
data class Settlement(val from: String, val to: String, val amount: Long)
fun PublicBundle.balances(): List<MemberBalance> {
    val paid = members.associate { it.id to 0L }.toMutableMap()
    val owed = paid.toMutableMap()
    expenses.forEach { row ->
        val e = row.expense
        paid[e.payerId] = Math.addExact(paid.getValue(e.payerId), e.rmbMinor)
        row.shares(this).forEach { (id, value) -> owed[id] = Math.addExact(owed.getValue(id), value) }
    }
    val sent = members.associate { it.id to 0L }.toMutableMap()
    val received = sent.toMutableMap()
    transfers.forEach { t ->
        sent[t.fromId] = Math.addExact(sent.getValue(t.fromId), t.amountMinor)
        received[t.toId] = Math.addExact(received.getValue(t.toId), t.amountMinor)
    }
    return members.sortedBy { it.position }.map {
        MemberBalance(it, paid.getValue(it.id), owed.getValue(it.id), sent.getValue(it.id), received.getValue(it.id)).also { b -> b.balance }
    }
}
fun settlements(balances: List<MemberBalance>): List<Settlement> {
    require(balances.fold(0L) { sum, b -> Math.addExact(sum, b.balance) } == 0L)
    val remaining = balances.associate { it.member.id to it.balance }.toMutableMap()
    val order = balances.map { it.member }.sortedBy { it.position }
    return buildList {
        while (true) {
            val payer = order.filter { remaining.getValue(it.id) < 0 }.minByOrNull { remaining.getValue(it.id) } ?: break
            val receiver = order.filter { remaining.getValue(it.id) > 0 }.maxBy { remaining.getValue(it.id) }
            val amount = minOf(Math.negateExact(remaining.getValue(payer.id)), remaining.getValue(receiver.id))
            add(Settlement(payer.id, receiver.id, amount))
            remaining[payer.id] = Math.addExact(remaining.getValue(payer.id), amount)
            remaining[receiver.id] = remaining.getValue(receiver.id) - amount
        }
    }
}

data class PublicDraft(val entry: EntryDraft, val payerId: String, val participants: Set<String>)
class PublicRepository(private val db: LedgerDatabase) {
    suspend fun create(name: String, names: List<String>) = db.withTransaction {
        val title = name.trim()
        require(title.isNotEmpty() && title.length <= 40) { "事件名称请填写 1～40 字" }
        val normalized = listOf("我") + names.map { it.trim() }
        require(normalized.all { it.isNotEmpty() && it.length <= 20 } && normalized.distinct().size == normalized.size) { "成员昵称须为 1～20 字且不能重复" }
        val id = UUID.randomUUID().toString()
        db.dao().insertEvent(LedgerEvent(id, title)); db.publicDao().insertEvent(PublicEvent(id))
        normalized.forEachIndexed { index, n -> db.publicDao().member(EventMember(UUID.randomUUID().toString(), id, n, index == 0, index)) }
    }
    suspend fun member(eventId: String, id: String?, name: String) = db.withTransaction {
        db.dao().requireWritableEvent(eventId)
        val bundle = requireNotNull(db.publicDao().bundle(eventId)) { "事件不存在" }
        val old = id?.let { key -> requireNotNull(bundle.members.find { it.id == key }) }
        require(old?.isSelf != true) { "不能修改本人身份" }
        val value = name.trim()
        require(value.isNotEmpty() && value.length <= 20 && bundle.members.none { it.id != id && it.name == value }) { "成员昵称须为 1～20 字且不能重复" }
        db.publicDao().member(old?.copy(name = value) ?: EventMember(UUID.randomUUID().toString(), eventId, value, false, Math.addExact(bundle.members.maxOf { it.position }, 1)))
    }
    suspend fun deleteMember(eventId: String, id: String) = db.withTransaction {
        db.dao().requireWritableEvent(eventId)
        val bundle = requireNotNull(db.publicDao().bundle(eventId))
        require(bundle.members.any { it.id == id && !it.isSelf }) { "不能删除本人" }
        require(bundle.expenses.none { it.expense.payerId == id || it.participants.any { p -> p.memberId == id } }) { "该成员有关联开支" }
        require(bundle.transfers.none { it.fromId == id || it.toId == id }) { "该成员有关联转账" }
        db.publicDao().deleteMember(id)
    }
    suspend fun save(draft: PublicDraft) = db.withTransaction {
        val d = draft.entry
        db.dao().requireWritableEvent(d.eventId)
        val bundle = requireNotNull(d.eventId?.let { db.publicDao().bundle(it) }) { "请选择公共事件" }
        require(d.kind == "expense") { "公共事件仅支持支出" }
        require(bundle.members.any { it.id == draft.payerId }) { "请选择付款人" }
        require(draft.participants.isNotEmpty() && draft.participants.all { id -> bundle.members.any { it.id == id } }) { "请选择本事件参与者" }
        val currency = MoneyCurrency.valueOf(d.currency)
        val original = Money.parse(d.amount, currency.digits)
        val rmb = if (currency == MoneyCurrency.CNY) original else Money.parse(d.rmb)
        val category = db.dao().category(d.categoryId)
        require(category?.kind == "expense" && category.parentId != null) { "请选择支出细类" }
        require(d.note.length <= 500) { "备注最多 500 字" }
        val old = db.publicDao().expenseById(d.id)
        require(old == null || old.eventId == bundle.event.eventId) { "不能迁移公共开支到其他事件" }
        val expense = PublicExpense(d.id, bundle.event.eventId, draft.payerId, original, currency.name, rmb, d.categoryId, Money.date(d.date), d.note.trim())
        val links = draft.participants.map { ExpenseMember(d.id, it) }
        bundle.copy(expenses = bundle.expenses.filterNot { it.expense.id == d.id } + SharedExpense(expense, links)).balances()
        val personal = bundle.personalEntry(SharedExpense(expense, links), old?.entryId ?: UUID.randomUUID().toString())
        if (personal != null) db.dao().save(personal)
        db.publicDao().expense(expense.copy(entryId = personal?.id))
        db.publicDao().clearParticipants(d.id); db.publicDao().participants(links)
        if (personal == null) old?.entryId?.let { db.dao().deleteEntry(it) }
    }
    suspend fun delete(eventId: String, id: String) = db.withTransaction {
        db.dao().requireWritableEvent(eventId)
        val old = requireNotNull(db.publicDao().bundle(eventId)?.expenses?.find { it.expense.id == id }) { "开支不存在" }
        db.publicDao().deleteExpense(id)
        old.expense.entryId?.let { db.dao().deleteEntry(it) }
    }
}

// SQL mirrors Room's version 3 schema; migration tests validate column and index definitions.
val publicSchema = listOf(
    "CREATE TABLE IF NOT EXISTS public_events (eventId TEXT NOT NULL PRIMARY KEY, FOREIGN KEY(eventId) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
    "CREATE TABLE IF NOT EXISTS event_members (id TEXT NOT NULL PRIMARY KEY, eventId TEXT NOT NULL, name TEXT NOT NULL, isSelf INTEGER NOT NULL, position INTEGER NOT NULL, FOREIGN KEY(eventId) REFERENCES public_events(eventId) ON UPDATE NO ACTION ON DELETE RESTRICT)",
    "CREATE INDEX IF NOT EXISTS index_event_members_eventId ON event_members(eventId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_event_members_eventId_name ON event_members(eventId,name)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_event_members_eventId_position ON event_members(eventId,position)",
    "CREATE TABLE IF NOT EXISTS public_expenses (id TEXT NOT NULL PRIMARY KEY, eventId TEXT NOT NULL, payerId TEXT NOT NULL, originalMinor INTEGER NOT NULL, currency TEXT NOT NULL, rmbMinor INTEGER NOT NULL, categoryId TEXT NOT NULL, occurredOn TEXT NOT NULL, note TEXT NOT NULL, entryId TEXT, FOREIGN KEY(eventId) REFERENCES public_events(eventId) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(payerId) REFERENCES event_members(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(entryId) REFERENCES entries(id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
    "CREATE INDEX IF NOT EXISTS index_public_expenses_eventId ON public_expenses(eventId)",
    "CREATE INDEX IF NOT EXISTS index_public_expenses_payerId ON public_expenses(payerId)",
    "CREATE INDEX IF NOT EXISTS index_public_expenses_categoryId ON public_expenses(categoryId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_public_expenses_entryId ON public_expenses(entryId)",
    "CREATE TABLE IF NOT EXISTS expense_members (expenseId TEXT NOT NULL, memberId TEXT NOT NULL, PRIMARY KEY(expenseId,memberId), FOREIGN KEY(expenseId) REFERENCES public_expenses(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(memberId) REFERENCES event_members(id) ON UPDATE NO ACTION ON DELETE RESTRICT)",
    "CREATE INDEX IF NOT EXISTS index_expense_members_memberId ON expense_members(memberId)",
)
