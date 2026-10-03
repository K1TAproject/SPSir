package com.spsir.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class LedgerState(
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val events: List<LedgerEvent> = emptyList(),
    val rows: List<LedgerRow> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
)

data class EntryDraft(
    val id: String = UUID.randomUUID().toString(),
    val kind: String = "expense",
    val amount: String = "",
    val currency: String = "CNY",
    val rmb: String = "",
    val categoryId: String = "food.breakfast",
    val eventId: String? = null,
    val date: String = LocalDate.now().toString(),
    val note: String = "",
)

fun LedgerEntry.toDraft() = EntryDraft(
    id, kind, Money.format(originalMinor, MoneyCurrency.valueOf(currency).digits), currency,
    Money.format(rmbMinor), categoryId, eventId, occurredOn, note,
)

class LedgerRepository(private val dao: LedgerDao) {
    suspend fun save(draft: EntryDraft) {
        require(draft.kind in listOf("expense", "income")) { "收支类型无效" }
        val currency = MoneyCurrency.entries.find { it.name == draft.currency }
        require(currency != null) { "请选择支持的币种" }
        val original = Money.parse(draft.amount, currency.digits)
        val rmb = if (currency == MoneyCurrency.CNY) original else Money.parse(draft.rmb)
        val category = dao.category(draft.categoryId)
        require(category != null && category.kind == draft.kind) { "请选择对应的收支分类" }
        require(draft.kind == "income" || category.parentId != null) { "请选择具体的支出子分类" }
        require(draft.note.length <= 500) { "备注最多 500 字" }
        val eventId = if (draft.kind == "expense") draft.eventId else null
        require(eventId == null || dao.event(eventId) != null) { "关联事件不存在" }
        dao.save(LedgerEntry(draft.id, draft.kind, original, currency.name, rmb,
            category.id, eventId, Money.date(draft.date), draft.note.trim()))
    }

    suspend fun createEvent(name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 40) { "事件名称请填写 1～40 字" }
        dao.insertEvent(LedgerEvent(UUID.randomUUID().toString(), trimmed))
    }
}

fun expenseTotal(rows: List<LedgerRow>): Long = rows.filter { it.entry.kind == "expense" }.sumOf { it.entry.rmbMinor }

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val db = LedgerDatabase.open(application)
    private val repository = LedgerRepository(db.dao())
    private val mutableState = MutableStateFlow(LedgerState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                db.dao().seed(presetCategories())
                combine(db.dao().categories(), db.dao().events(), db.dao().rows()) { categories, events, rows ->
                    Triple(categories, events, rows)
                }.collect { (categories, events, rows) ->
                    mutableState.update { it.copy(loading = false, categories = categories, events = events, rows = rows) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = "账本读取失败，请关闭后重试；原数据不会被清空。") }
            }
        }
    }

    fun clearError() = mutableState.update { it.copy(error = null) }
    fun save(draft: EntryDraft, done: () -> Unit) = mutate(done) { repository.save(draft) }
    fun createEvent(name: String, done: () -> Unit) = mutate(done) { repository.createEvent(name) }
    fun delete(id: String, done: () -> Unit) = mutate(done) { db.dao().deleteEntry(id) }

    private fun mutate(done: () -> Unit, action: suspend () -> Unit) {
        if (state.value.saving) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                action()
                done()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                mutableState.update { it.copy(error = e.message) }
            } catch (_: Exception) {
                mutableState.update { it.copy(error = "保存失败，请重试。输入内容已保留。") }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    override fun onCleared() {
        db.close()
    }
}
