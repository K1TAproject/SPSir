package com.spsir.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

val entryDraftSaver = listSaver<EntryDraft, String>(
    save = { listOf(it.id, it.kind, it.amount, it.currency, it.rmb, it.categoryId, it.eventId.orEmpty(), it.date, it.note) },
    restore = { EntryDraft(it[0], it[1], it[2], it[3], it[4], it[5], it[6].ifEmpty { null }, it[7], it[8]) },
)

// Shared by the existing forms so cancel, outside taps and system back agree.
@Composable
fun rememberDiscardChanges(changed: Boolean, saving: Boolean, close: () -> Unit): () -> Unit {
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (confirming) AlertDialog(
        onDismissRequest = { if (!saving) confirming = false },
        title = { Text("放弃修改？") },
        text = { Text("未保存的内容将丢失。") },
        confirmButton = { TextButton(enabled = !saving, onClick = { confirming = false; close() }) { Text("放弃修改") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { confirming = false }) { Text("继续编辑") } },
    )
    return { if (!saving) { if (changed) confirming = true else close() } }
}

@Composable
fun EntryEditor(initial: EntryDraft, editing: Boolean, state: LedgerState, dismiss: () -> Unit, save: (EntryDraft) -> Unit, delete: (String) -> Unit, openPublic: (EntryDraft) -> Unit) {
    val baseline by rememberSaveable(stateSaver = entryDraftSaver) { mutableStateOf(initial) }
    val id by rememberSaveable { mutableStateOf(initial.id) }
    var kind by rememberSaveable { mutableStateOf(initial.kind) }
    var amount by rememberSaveable { mutableStateOf(initial.amount) }
    var currency by rememberSaveable { mutableStateOf(initial.currency) }
    var rmb by rememberSaveable { mutableStateOf(initial.rmb) }
    var categoryId by rememberSaveable { mutableStateOf(initial.categoryId) }
    var eventId by rememberSaveable { mutableStateOf(initial.eventId) }
    var date by rememberSaveable { mutableStateOf(initial.date) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val category = state.categories.find { it.id == categoryId }
    var selectingCategory by rememberSaveable { mutableStateOf(false) }
    val recent = remember(state.rows, state.categories, state.hiddenCategories) { recentCategories(state) }
    val defaultCategory = recent.firstOrNull()?.id ?: visibleExpenseCategories(state).firstOrNull()?.id.orEmpty()

    val draft = EntryDraft(id, kind, amount, currency, rmb, categoryId, eventId, date, note)
    val requestClose = rememberDiscardChanges(draft != baseline, state.saving, dismiss)
    AlertDialog(
        onDismissRequest = requestClose,
        title = { Text(if (editing) "编辑记录" else "记一笔") },
        modifier = Modifier.systemBarsPadding().imePadding().fillMaxWidth().padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("expense" to "支出", "income" to "收入").forEach { (key, label) ->
                        FilterChip(selected = kind == key, enabled = !state.saving, onClick = {
                            kind = key; categoryId = if (key == "income") "income" else defaultCategory
                            if (key == "income") eventId = null
                        }, label = { Text(label) })
                    }
                }
                OutlinedTextField(amount, { amount = it }, label = { Text(if (currency == "CNY") "金额（人民币）" else "原币金额（$currency）") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.headlineSmall, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.saving)
                ChoiceField("币种", "$currency · ${MoneyCurrency.valueOf(currency).label}", MoneyCurrency.entries.map { it.name to "${it.name} · ${it.label}" }, !state.saving) { currency = it }
                if (currency != "CNY") {
                    OutlinedTextField(rmb, { rmb = it }, label = { Text("人民币金额") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.headlineSmall, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.saving)
                }
                if (kind == "expense") {
                    OutlinedButton(enabled = !state.saving, onClick = { selectingCategory = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("分类：${state.categories.find { it.id == category?.parentId }?.name.orEmpty()} / ${category?.name ?: "请选择"}")
                    }
                    if (recent.isNotEmpty()) {
                        Text("最近使用", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(recent) { c ->
                            FilterChip(selected = categoryId == c.id, enabled = !state.saving, onClick = { categoryId = c.id }, label = { Text(c.name) })
                        } }
                    }
                    ChoiceField("关联事件", state.events.find { it.id == eventId }?.name ?: "无事件", listOf("" to "无事件") + state.events.filter { e -> !editing || state.publicEvents.none { it.event.eventId == e.id } }.map { it.id to it.name }, !state.saving) {
                        if (state.publicEvents.any { b -> b.event.eventId == it }) openPublic(EntryDraft(id, kind, amount, currency, rmb, categoryId, it, date, note))
                        else eventId = it.ifEmpty { null }
                    }
                }
                LedgerDateField("记账日期", date, !state.saving) { date = it }
                OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, maxLines = 3, enabled = !state.saving)
                state.error?.let { ErrorText(it) }
                if (editing) TextButton(enabled = !state.saving, onClick = { confirmDelete = true }) { Text("删除这笔记录", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(enabled = !state.saving, onClick = { save(EntryDraft(id, kind, amount, currency, rmb, categoryId, eventId, date, note)) }) { Text(if (state.saving) "保存中…" else "保存") } },
        dismissButton = { TextButton(enabled = !state.saving, onClick = requestClose) { Text("取消") } },
    )
    if (selectingCategory) CategoryPicker(state, categoryId, if (editing) initial.categoryId else null,
        { selectingCategory = false }, { categoryId = it })
    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!state.saving) confirmDelete = false },
        title = { Text("删除这笔记录？") },
        text = { Text("删除后无法恢复。") },
        confirmButton = { TextButton(enabled = !state.saving, onClick = { delete(id) }) { Text("确认删除") } },
        dismissButton = { TextButton(enabled = !state.saving, onClick = { confirmDelete = false }) { Text("保留") } },
    )
}

