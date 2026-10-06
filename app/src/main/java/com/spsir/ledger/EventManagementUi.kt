package com.spsir.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

val transferDraftSaver = listSaver<TransferDraft, String>(
    save = { listOf(it.id, it.eventId, it.fromId, it.toId, it.amount, it.date, it.note) },
    restore = { TransferDraft(it[0], it[1], it[2], it[3], it[4], it[5], it[6]) },
)

@Composable
fun TransferEditor(initial: TransferDraft, editing: Boolean, bundle: PublicBundle, state: LedgerState,
    close: () -> Unit, save: (TransferDraft, Boolean) -> Unit, delete: () -> Unit) {
    var draft by rememberSaveable(stateSaver = transferDraftSaver) { mutableStateOf(initial) }
    val baseline by rememberSaveable(stateSaver = transferDraftSaver) { mutableStateOf(initial) }
    var confirmOver by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val closeForm = rememberDiscardChanges(draft != baseline || (!editing && draft.amount.isNotEmpty()), state.saving, close)
    val choices = bundle.members.sortedBy { it.position }.map { it.id to it.name }
    val limit = bundle.transferLimit(draft.id, draft.fromId, draft.toId)
    val archived = state.events.find { it.id == draft.eventId }?.archived == true
    val enabled = !state.saving && !archived
    AlertDialog(onDismissRequest = closeForm, title = { Text(if (editing) "编辑转账" else "记录转账") },
        modifier = Modifier.systemBarsPadding().imePadding().fillMaxWidth().padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (archived) Text("事件已归档，请先取消归档")
            ChoiceField("转出人", choices.find { it.first == draft.fromId }?.second.orEmpty(), choices, enabled) { draft = draft.copy(fromId = it); error = null }
            ChoiceField("收款人", choices.find { it.first == draft.toId }?.second.orEmpty(), choices, enabled) { draft = draft.copy(toId = it); error = null }
            OutlinedTextField(draft.amount, { draft = draft.copy(amount = it); error = null }, label = { Text("转账金额（人民币）") }, singleLine = true, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Text("当前可结算 ¥ ${Money.format(limit)}", style = MaterialTheme.typography.bodySmall)
            LedgerDateField("转账日期", draft.date, enabled) { draft = draft.copy(date = it) }
            OutlinedTextField(draft.note, { draft = draft.copy(note = it) }, label = { Text("备注（可选）") }, maxLines = 3, enabled = enabled)
            (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (editing) TextButton(enabled = enabled, onClick = { confirmDelete = true }) { Text("删除转账", color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(enabled = enabled, onClick = {
            try {
                val amount = Money.parse(draft.amount)
                validateTransfer(EventTransfer(draft.id, draft.eventId, draft.fromId, draft.toId, amount, draft.date, draft.note), bundle)
                if (amount > limit) confirmOver = true else save(draft, false)
            } catch (e: IllegalArgumentException) { error = e.message }
        }) { Text(if (state.saving) "保存中…" else "保存") } },
        dismissButton = { TextButton(enabled = !state.saving, onClick = closeForm) { Text("取消") } })
    if (confirmOver) AlertDialog(onDismissRequest = { if (!state.saving) confirmOver = false }, title = { Text("确认超额转账？") },
        text = { Text("超过当前可结算金额，保存后可能产生反向应收应付。") },
        confirmButton = { TextButton(enabled = enabled, onClick = { save(draft, true) }) { Text("确认保存") } },
        dismissButton = { TextButton(enabled = !state.saving, onClick = { confirmOver = false }) { Text("返回修改") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!state.saving) confirmDelete = false }, title = { Text("删除这笔转账？") },
        text = { Text("将重新计算剩余结算金额。") },
        confirmButton = { TextButton(enabled = enabled, onClick = delete) { Text("确认删除") } },
        dismissButton = { TextButton(enabled = !state.saving, onClick = { confirmDelete = false }) { Text("保留") } })
}

@Composable
fun RenameEventDialog(event: LedgerEvent, state: LedgerState, close: () -> Unit, save: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(event.name) }
    val baseline by rememberSaveable { mutableStateOf(event.name) }
    val dismiss = rememberDiscardChanges(name != baseline, state.saving, close)
    AlertDialog(onDismissRequest = dismiss, title = { Text("事件改名") }, modifier = Modifier.systemBarsPadding().imePadding(),
        text = { Column { OutlinedTextField(name, { name = it }, label = { Text("事件名称") }, singleLine = true, enabled = !state.saving); state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(enabled = !state.saving, onClick = { save(name) }) { Text("保存") } },
        dismissButton = { TextButton(enabled = !state.saving, onClick = dismiss) { Text("取消") } })
}
