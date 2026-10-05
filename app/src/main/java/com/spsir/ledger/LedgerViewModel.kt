package com.spsir.ledger

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
    val hiddenCategories: Set<String> = emptySet(),
    val backupPreview: LedgerBackup? = null,
    val message: String? = null,
    val publicEvents: List<PublicBundle> = emptyList(),
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
        require(dao.isDerived(draft.id) == 0) { "请在公共开支中编辑" }
        require(draft.eventId == null || dao.isPublic(draft.eventId) == 0) { "请使用公共开支表单" }
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
    private val publicRepository = PublicRepository(db)
    private val repository = LedgerRepository(db.dao())
    private val backups = BackupStore(db, File(application.filesDir, "before-restore.json"))
    private val mutableState = MutableStateFlow(LedgerState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                db.dao().seed(presetCategories())
                combine(db.dao().categories(), db.dao().events(), db.dao().rows(), db.dao().hiddenCategories(), db.publicDao().watch()) { categories, events, rows, hidden, publicEvents ->
                    mutableState.update { it.copy(loading = false, categories = categories, events = events, rows = rows, hiddenCategories = hidden.map { h -> h.categoryId }.toSet(), publicEvents = publicEvents) }
                }.collect {}
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = "读取失败，请重启应用。") }
            }
        }
    }

    fun createPublic(name: String, names: List<String>, done: () -> Unit) = mutate(done) { publicRepository.create(name, names) }
    fun savePublic(draft: PublicDraft, done: () -> Unit) = mutate(done) { publicRepository.save(draft) }
    fun deletePublic(event: String, id: String, done: () -> Unit) = mutate(done) { publicRepository.delete(event, id) }
    fun saveMember(event: String, id: String?, name: String, done: () -> Unit) = mutate(done) { publicRepository.member(event, id, name) }
    fun deleteMember(event: String, id: String) = mutate({}) { publicRepository.deleteMember(event, id) }

    fun clearError() = mutableState.update { it.copy(error = null) }
    fun save(draft: EntryDraft, done: () -> Unit) = mutate(done) { repository.save(draft) }
    fun createEvent(name: String, done: () -> Unit) = mutate(done) { repository.createEvent(name) }
    fun delete(id: String, done: () -> Unit) = mutate(done) { val source = db.publicDao().fromEntry(id)
        if (source != null) publicRepository.delete(source.eventId, source.id) else db.dao().deleteEntry(id)
    }
    fun setCategoryHidden(id: String, hidden: Boolean) = mutate({}) {
        require(db.dao().category(id)?.kind == "expense") { "请选择支出分类" }
        if (hidden) db.dao().hide(HiddenCategory(id)) else db.dao().showCategory(id)
    }

    fun dismissBackup() { if (!state.value.saving) mutableState.update { it.copy(backupPreview = null, error = null) } }
    fun clearMessage() = mutableState.update { it.copy(message = null) }

    fun exportBackup(uri: Uri, safety: Boolean = false) = mutate({}) {
        withContext(Dispatchers.IO) {
            val data = if (safety) backups.readSafety() else backups.snapshot()
            val resolver = getApplication<Application>().contentResolver
            requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法打开保存位置" }.bufferedWriter(Charsets.UTF_8).use { it.write(BackupJson.encode(data)) }
        }
        mutableState.update { it.copy(message = "备份已导出") }
    }

    fun inspectBackup(uri: Uri) = mutate({}) {
        val data = withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            requireNotNull(resolver.openInputStream(uri)) { "无法读取文件" }.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count = input.read(buffer)
                while (count != -1) {
                    require(output.size() + count <= 20 * 1024 * 1024) { "备份超过 20 MB，请使用较小文件" }
                    output.write(buffer, 0, count)
                    count = input.read(buffer)
                }
                val bytes = output.toByteArray()
                require(bytes.size <= 20 * 1024 * 1024) { "备份超过 20 MB，请使用较小文件" }
                BackupJson.decode(bytes.toString(Charsets.UTF_8))
            }
        }
        mutableState.update { it.copy(backupPreview = data) }
    }

    fun inspectSafety() = mutate({}) {
        val data = withContext(Dispatchers.IO) { backups.readSafety() }
        mutableState.update { it.copy(backupPreview = data) }
    }

    fun restoreBackup() {
        val data = state.value.backupPreview ?: return
        mutate({ mutableState.update { it.copy(backupPreview = null, message = "账本已恢复") } }) { backups.restore(data) }
    }

    private fun mutate(done: () -> Unit, action: suspend () -> Unit) {
        if (state.value.saving) return
        mutableState.update { it.copy(saving = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                action()
                done()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                mutableState.update { it.copy(error = e.message) }
            } catch (_: Exception) {
                mutableState.update { it.copy(error = "操作失败，请检查文件或保存位置。") }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    override fun onCleared() {
        db.close()
    }
}
