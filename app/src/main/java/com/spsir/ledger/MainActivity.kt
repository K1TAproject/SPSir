package com.spsir.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.DKGRAY),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.DKGRAY),
        )
        setContent {
            LedgerTheme {
                val vm: LedgerViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                LedgerScreen(state, vm::save, vm::createEvent, vm::delete, vm::clearError)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    state: LedgerState,
    onSave: (EntryDraft, () -> Unit) -> Unit = { _, done -> done() },
    onCreateEvent: (String, () -> Unit) -> Unit = { _, done -> done() },
    onDelete: (String, () -> Unit) -> Unit = { _, done -> done() },
    clearError: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedEvent by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialEvent by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingEvent by rememberSaveable { mutableStateOf(false) }
    val event = state.events.find { it.id == selectedEvent }
    val monthRows = state.rows.filter { it.entry.occurredOn.startsWith(month) }
    val eventRows = state.rows.filter { it.entry.eventId == selectedEvent && selectedEvent != null }

    fun startEntry(row: LedgerRow? = null) {
        clearError()
        editingId = row?.entry?.id
        initialEvent = if (tab == 1) selectedEvent else null
        editorOpen = true
    }

    Scaffold(
        topBar = {
            TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), title = { Text(if (tab == 1 && event != null) event.name else listOf("随手账本", "事件", "消费结构")[tab]) },
                navigationIcon = {
                    if (tab == 1 && event != null) TextButton(onClick = { selectedEvent = null }) { Text("返回") }
                })
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                listOf("流水", "事件", "统计").forEachIndexed { index, title ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { LedgerNavIcon(index) }, label = { Text(title) })
                }
            }
        },
        floatingActionButton = {
            if (!state.loading && state.categories.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = { startEntry() }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Text("＋ 记一笔") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.error != null && !editorOpen && !creatingEvent) item { ErrorText(state.error) }
            if (tab == 0) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { month = YearMonth.parse(month).minusMonths(1).toString() }) { Text("上月") }
                        Text(month, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
                        TextButton(onClick = { month = YearMonth.parse(month).plusMonths(1).toString() }) { Text("下月") }
                    }
                }
                item { TotalCard(monthRows, "本月支出") }
            }
            when (tab) {
                0 -> {
                    item { Text("全部流水 · 按人民币汇总", style = MaterialTheme.typography.titleMedium) }
                    if (monthRows.isEmpty()) item { EmptyText("本月还没有记录。\n从一顿午饭或一卷胶卷开始。") }
                    items(monthRows, key = { it.entry.id }) { row -> EntryCard(row) { startEntry(row) } }
                }
                1 -> if (event == null) {
                    item {
                        Text("把旅行、外拍等开销放在一起。事件里的每笔记录也会出现在全部流水中。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    item { OutlinedButton(onClick = { clearError(); creatingEvent = true }) { Text("＋ 新建事件") } }
                    if (state.events.isEmpty()) item { EmptyText("还没有事件，例如可以创建「北欧旅行」。") }
                    items(state.events, key = { it.id }) { item ->
                        val rows = state.rows.filter { it.entry.eventId == item.id }
                        OutlinedCard(onClick = { selectedEvent = item.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text("¥ ${Money.format(expenseTotal(rows))}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                Text("${rows.size} 笔支出 · 查看事件 ›", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    item { TotalCard(eventRows, "事件累计支出", showIncome = false) }
                    item { Text("包含全部日期 · 金额为个人承担部分", style = MaterialTheme.typography.bodySmall) }
                    item { CategoryBreakdown(eventRows, state.categories, allowIncome = false) }
                    item { Text("事件流水", style = MaterialTheme.typography.titleMedium) }
                    if (eventRows.isEmpty()) item { EmptyText("点击「记一笔」，新支出会自动关联此事件。\n也可在全部流水中编辑已有记录，选择本事件。") }
                    items(eventRows, key = { it.entry.id }) { row -> EntryCard(row) { startEntry(row) } }
                }
                2 -> {
                    item { StatisticsPanel(state.rows, state.categories) }
                }
            }
        }
    }

    if (editorOpen) {
        val existing = state.rows.find { it.entry.id == editingId }?.entry
        EntryEditor(
            initial = existing?.toDraft() ?: EntryDraft(eventId = initialEvent),
            editing = editingId != null,
            state = state,
            dismiss = { if (!state.saving) { editorOpen = false; clearError() } },
            save = { draft -> onSave(draft) { editorOpen = false } },
            delete = { onDelete(it) { editorOpen = false } },
        )
    }
    if (creatingEvent) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!state.saving) { creatingEvent = false; clearError() } },
            title = { Text("新建事件") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("事件名称") }, placeholder = { Text("例如：北欧旅行") }, singleLine = true)
                    state.error?.let { ErrorText(it) }
                }
            },
            confirmButton = { TextButton(enabled = !state.saving, onClick = { onCreateEvent(name) { creatingEvent = false } }) { Text("创建") } },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { creatingEvent = false; clearError() }) { Text("取消") } },
        )
    }
}

@Composable
private fun TotalCard(rows: List<LedgerRow>, title: String, showIncome: Boolean = true) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
            Text("¥ ${Money.format(expenseTotal(rows))}", style = MaterialTheme.typography.headlineLarge)
            HorizontalDivider(color = Color.White.copy(alpha = 0.18f), modifier = Modifier.padding(vertical = 4.dp))
            if (showIncome) Text("收入 ¥ ${Money.format(rows.filter { it.entry.kind == "income" }.sumOf { it.entry.rmbMinor })}")
        }
    }
}

@Composable
private fun EntryCard(row: LedgerRow, onClick: () -> Unit) {
    val entry = row.entry
    OutlinedCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.categoryName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${if (entry.kind == "expense") "−" else "+"} ¥ ${Money.format(entry.rmbMinor)}", style = MaterialTheme.typography.titleMedium, color = if (entry.kind == "expense") ExpenseColor else MaterialTheme.colorScheme.primary)
            }
            Text(listOfNotNull(entry.occurredOn, row.groupName, row.eventName).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.currency != "CNY") Text("原币 ${entry.currency} ${Money.format(entry.originalMinor, MoneyCurrency.valueOf(entry.currency).digits)}", style = MaterialTheme.typography.bodySmall)
            if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.padding(16.dp)) { LedgerNavIcon(0) }
            }
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ChoiceField(label: String, value: String, choices: List<Pair<String, String>>, enabled: Boolean = true, choose: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.small) {
                Text(value, modifier = Modifier.weight(1f)); Text("▾")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                choices.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { open = false; choose(id) })
                }
            }
        }
    }
}

@Composable
private fun EntryEditor(initial: EntryDraft, editing: Boolean, state: LedgerState, dismiss: () -> Unit, save: (EntryDraft) -> Unit, delete: (String) -> Unit) {
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
    val parentId = category?.parentId
    val groups = state.categories.filter { it.kind == "expense" && it.parentId == null }
    val children = state.categories.filter {
        it.parentId == parentId && it.kind == "expense" && it.parentId != null &&
            (it.id !in legacyCategoryIds || it.id == initial.categoryId)
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (editing) "编辑记录" else "记一笔") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("expense" to "支出", "income" to "收入").forEach { (key, label) ->
                        FilterChip(selected = kind == key, enabled = !state.saving, onClick = {
                            kind = key; categoryId = if (key == "income") "income" else "food.breakfast"
                            if (key == "income") eventId = null
                        }, label = { Text(label) })
                    }
                }
                ChoiceField("币种", "$currency · ${MoneyCurrency.valueOf(currency).label}", MoneyCurrency.entries.map { it.name to "${it.name} · ${it.label}" }, !state.saving) { currency = it }
                OutlinedTextField(amount, { amount = it }, label = { Text(if (currency == "CNY") "金额（人民币）" else "原币金额（$currency）") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.headlineSmall, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.saving)
                if (currency != "CNY") {
                    OutlinedTextField(rmb, { rmb = it }, label = { Text("人民币金额（手动填写）") }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.headlineSmall, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !state.saving)
                    Text("不自动换汇；所有统计使用此人民币金额。", style = MaterialTheme.typography.bodySmall)
                }
                if (kind == "expense") {
                    ChoiceField("分类", groups.find { it.id == parentId }?.name ?: "选择分类", groups.map { it.id to it.name }, !state.saving) { group ->
                        categoryId = state.categories.first { it.parentId == group }.id
                    }
                    ChoiceField("具体用途", category?.name ?: "选择用途", children.map { it.id to it.name }, !state.saving) { categoryId = it }
                    ChoiceField("关联事件", state.events.find { it.id == eventId }?.name ?: "无事件", listOf("" to "无事件") + state.events.map { it.id to it.name }, !state.saving) { eventId = it.ifEmpty { null } }
                }
                OutlinedTextField(date, { date = it }, label = { Text("日期（YYYY-MM-DD）") }, singleLine = true, enabled = !state.saving)
                OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, maxLines = 3, enabled = !state.saving)
                if (kind == "expense") Text("只记录你个人承担的金额，默认已经 AA。", style = MaterialTheme.typography.bodySmall)
                state.error?.let { ErrorText(it) }
                if (editing) TextButton(enabled = !state.saving, onClick = { confirmDelete = true }) { Text("删除这笔记录", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(enabled = !state.saving, onClick = { save(EntryDraft(id, kind, amount, currency, rmb, categoryId, eventId, date, note)) }) { Text(if (state.saving) "保存中…" else "保存") } },
        dismissButton = { TextButton(enabled = !state.saving, onClick = dismiss) { Text("取消") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { if (!state.saving) confirmDelete = false },
        title = { Text("删除这笔记录？") },
        text = { Text("它将同时从全部流水、事件明细和统计中移除。此操作无法撤销。") },
        confirmButton = { TextButton(enabled = !state.saving, onClick = { delete(id) }) { Text("确认删除") } },
        dismissButton = { TextButton(enabled = !state.saving, onClick = { confirmDelete = false }) { Text("保留") } },
    )
}

@Preview(showBackground = true, showSystemUi = true, name = "空白账本", locale = "zh")
@Composable
fun EmptyLedgerPreview() {
    LedgerTheme { LedgerScreen(LedgerState(loading = false, categories = presetCategories())) }
}

@Preview(showBackground = true, showSystemUi = true, name = "示例流水（不写入数据库）", locale = "zh")
@Composable
fun PopulatedLedgerPreview() {
    val sample = LedgerEntry("preview", "expense", 12000, "NOK", 8100, "food.lunch", "trip", LocalDate.now().toString(), "旅行午餐")
    LedgerTheme {
        LedgerScreen(LedgerState(loading = false, categories = presetCategories(), events = listOf(LedgerEvent("trip", "北欧旅行")), rows = listOf(LedgerRow(sample, "午餐", "餐饮", "北欧旅行"))))
    }
}
