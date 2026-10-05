package com.spsir.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

fun LazyListScope.publicEventContent(bundle: PublicBundle, state: LedgerState, settlement: Boolean,
    selectTab: (Boolean) -> Unit, members: () -> Unit, edit: (SharedExpense) -> Unit) {
    val balances = bundle.balances()
    val total = balances.fold(0L) { sum, b -> Math.addExact(sum, b.paid) }
    item {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("事件总额", style = MaterialTheme.typography.labelLarge)
                Text("¥ ${Money.format(total)}", style = MaterialTheme.typography.headlineLarge)
                Text("我的支出 ¥ ${Money.format(balances.single { it.member.isSelf }.share)}")
            }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(!settlement, { selectTab(false) }, label = { Text("开支") })
            FilterChip(settlement, { selectTab(true) }, label = { Text("结算") })
            TextButton(onClick = members) { Text("成员（${bundle.members.size}）") }
        }
    }
    if (settlement) {
        if (bundle.expenses.isEmpty()) item { Text("暂无待结算费用") }
        else {
            items(balances, key = { "balance-${it.member.id}" }) { b ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(b.member.name, style = MaterialTheme.typography.titleMedium)
                        Text("应承担 ¥ ${Money.format(b.share)}")
                        Text("已付 ¥ ${Money.format(b.paid)}")
                        Text(if (b.balance == 0L) "无需转账" else "${if (b.balance > 0) "应收" else "应付"} ¥ ${Money.format(kotlin.math.abs(b.balance))}", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { Text("转账建议", style = MaterialTheme.typography.titleMedium) }
            val transfers = settlements(balances)
            if (transfers.isEmpty()) item { Text("无需转账") }
            items(transfers) { t ->
                Text("${bundle.members.single { it.id == t.from }.name} → ${bundle.members.single { it.id == t.to }.name}  ¥ ${Money.format(t.amount)}")
            }
            item { Text("未扣除成员间已转账", style = MaterialTheme.typography.bodySmall) }
        }
    } else {
        if (bundle.expenses.isEmpty()) item { Text("暂无开支") }
        bundle.expenses.sortedWith(compareByDescending<SharedExpense> { it.expense.occurredOn }.thenBy { it.expense.id }).groupBy { it.expense.occurredOn }.forEach { (date, rows) ->
            item { Text(date, style = MaterialTheme.typography.labelLarge) }
            items(rows, key = { it.expense.id }) { row ->
                val e = row.expense
                val selected = bundle.members.filter { m -> row.participants.any { it.memberId == m.id } }.sortedBy { it.position }
                val self = bundle.members.single { it.isSelf }
                OutlinedCard(onClick = { edit(row) }, modifier = Modifier.fillMaxWidth().testTag("public-expense-${e.id}")) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.categories.find { it.id == e.categoryId }?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text("¥ ${Money.format(e.rmbMinor)}", style = MaterialTheme.typography.titleLarge)
                        Text("${bundle.members.single { it.id == e.payerId }.name}付 · ${if (selected.size <= 3) selected.joinToString("、") { it.name } else "${selected.size} 人分摊"}")
                        Text(if (self in selected) "我 ¥ ${Money.format(row.shares(bundle).getValue(self.id))}" else "我未参与", color = MaterialTheme.colorScheme.primary)
                        if (e.currency != "CNY") Text("${e.currency} ${Money.format(e.originalMinor, MoneyCurrency.valueOf(e.currency).digits)}")
                        if (e.note.isNotBlank()) Text(e.note)
                    }
                }
            }
        }
        if (bundle.expenses.isNotEmpty()) item {
            val rows = bundle.expenses.map { row ->
                val e = row.expense; val c = state.categories.single { it.id == e.categoryId }
                LedgerRow(LedgerEntry(e.id, "expense", e.originalMinor, e.currency, e.rmbMinor, e.categoryId, e.eventId, e.occurredOn, e.note), c.name, state.categories.find { it.id == c.parentId }?.name, null)
            }
            CategoryBreakdown(rows, state.categories, allowIncome = false, title = "事件类别占比")
        }
    }
}

@Composable
fun PublicExpenseEditor(initial: EntryDraft, existing: SharedExpense?, bundle: PublicBundle, state: LedgerState,
    close: () -> Unit, save: (PublicDraft) -> Unit, delete: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf(initial.amount) }
    var currency by rememberSaveable { mutableStateOf(initial.currency) }
    var rmb by rememberSaveable { mutableStateOf(initial.rmb) }
    var category by rememberSaveable { mutableStateOf(initial.categoryId) }
    var date by rememberSaveable { mutableStateOf(initial.date) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var payer by rememberSaveable { mutableStateOf(existing?.expense?.payerId ?: bundle.members.single { it.isSelf }.id) }
    var selected by rememberSaveable { mutableStateOf(existing?.participants?.map { it.memberId } ?: bundle.members.sortedBy { it.position }.map { it.id }) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val participants = bundle.members.filter { it.id in selected }.sortedBy { it.position }
    val preview = runCatching {
        val original = splitAmount(Money.parse(amount, MoneyCurrency.valueOf(currency).digits), participants)
        val yuan = splitAmount(if (currency == "CNY") Money.parse(amount) else Money.parse(rmb), participants)
        original to yuan
    }.getOrNull()
    AlertDialog(onDismissRequest = close, title = { Text(if (existing == null) "记开支" else "编辑公共开支") },
        modifier = Modifier.systemBarsPadding().imePadding().fillMaxWidth().padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text(if (currency == "CNY") "项目总额" else "原币总额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.saving)
                ChoiceField("币种", currency, MoneyCurrency.entries.map { it.name to "${it.name} · ${it.label}" }, !state.saving) { currency = it }
                if (currency != "CNY") OutlinedTextField(rmb, { rmb = it }, label = { Text("人民币总额") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.saving)
                OutlinedButton(onClick = { picking = true }, enabled = !state.saving) { Text("分类：${state.categories.find { it.id == category }?.name ?: "请选择"}") }
                ChoiceField("付款人", bundle.members.find { it.id == payer }?.name.orEmpty(), bundle.members.sortedBy { it.position }.map { it.id to it.name }, !state.saving) { payer = it }
                Text("参与者", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { selected = bundle.members.map { it.id } }, enabled = !state.saving) { Text("全选") }
                bundle.members.sortedBy { it.position }.forEach { m ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("participant-${m.id}").toggleable(value = m.id in selected, enabled = !state.saving, role = Role.Checkbox,
                        onValueChange = { checked -> selected = if (checked) selected + m.id else selected - m.id }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(m.id in selected, null, enabled = !state.saving)
                        Text(m.name, Modifier.weight(1f))
                    }
                }
                if (preview != null) {
                    Text("分摊预览", style = MaterialTheme.typography.titleSmall)
                    participants.forEach { m ->
                        Text("${m.name}：¥ ${Money.format(preview.second.getValue(m.id))}${if (currency == "CNY") "" else " · $currency ${Money.format(preview.first.getValue(m.id), MoneyCurrency.valueOf(currency).digits)}"}")
                    }
                    if (preview.second.values.distinct().size > 1 || preview.first.values.distinct().size > 1) Text("尾差已分配", style = MaterialTheme.typography.bodySmall)
                    Text("我的支出 ¥ ${Money.format(preview.second[bundle.members.single { it.isSelf }.id] ?: 0L)}", color = MaterialTheme.colorScheme.primary)
                }
                LedgerDateField("日期", date, !state.saving) { date = it }
                OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, enabled = !state.saving, maxLines = 3)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (existing != null) TextButton(onClick = { confirming = true }, enabled = !state.saving) { Text("删除公共开支", color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { Button(enabled = !state.saving, onClick = { save(PublicDraft(initial.copy(kind = "expense", amount = amount, currency = currency, rmb = rmb, categoryId = category, date = date, note = note, eventId = bundle.event.eventId), payer, selected.toSet())) }) { Text("保存") } },
        dismissButton = { TextButton(enabled = !state.saving, onClick = close) { Text("取消") } })
    if (picking) CategoryPicker(state, category, initial.categoryId, { picking = false }) { category = it }
    if (confirming) AlertDialog(onDismissRequest = { if (!state.saving) confirming = false }, title = { Text("删除整笔公共开支？") }, text = { Text("将同步更新所有人的分摊。") },
        confirmButton = { TextButton(enabled = !state.saving, onClick = delete) { Text("确认删除") } }, dismissButton = { TextButton(enabled = !state.saving, onClick = { confirming = false }) { Text("取消") } })
}

@Composable
fun PublicMembersDialog(bundle: PublicBundle, state: LedgerState, close: () -> Unit,
    save: (String?, String, () -> Unit) -> Unit, delete: (String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("成员") },
        modifier = Modifier.systemBarsPadding().imePadding(),
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bundle.members.sortedBy { it.position }.forEach { m ->
                Text(m.name, style = MaterialTheme.typography.titleMedium)
                if (!m.isSelf) Row {
                    TextButton(enabled = !state.saving, onClick = { editing = m.id; name = m.name }) { Text("改名") }
                    TextButton(enabled = !state.saving, onClick = { delete(m.id); if (editing == m.id) { editing = null; name = "" } }) { Text("移除") }
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text(if (editing == null) "同行者昵称" else "新昵称") }, singleLine = true, enabled = !state.saving)
            Button(enabled = !state.saving, onClick = { save(editing, name) { name = ""; editing = null } }) { Text(if (editing == null) "添加" else "保存昵称") }
            if (editing != null) TextButton(onClick = { editing = null; name = "" }) { Text("取消改名") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(enabled = !state.saving, onClick = close) { Text("完成") } })
}
