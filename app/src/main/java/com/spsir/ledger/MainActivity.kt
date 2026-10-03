package com.spsir.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
                LedgerScreen(state, vm::save, vm::createEvent, vm::delete, vm::clearError, vm::setCategoryHidden) { BackupTools(state, vm) }
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
    setCategoryHidden: (String, Boolean) -> Unit = { _, _ -> },
    backupTools: @Composable () -> Unit = {},
) {
    var toolsOpen by rememberSaveable { mutableStateOf(false) }
    var filtering by rememberSaveable { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable(stateSaver = listSaver(
        save = { listOf(it.query, it.start, it.end, it.kind, it.categoryId, it.eventId) },
        restore = { LedgerFilter(it[0], it[1], it[2], it[3], it[4], it[5]) },
    )) { mutableStateOf(LedgerFilter()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedEvent by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialEvent by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingEvent by rememberSaveable { mutableStateOf(false) }
    val event = state.events.find { it.id == selectedEvent }
    val monthRows = state.rows.filter { it.entry.occurredOn.startsWith(month) }
    val displayRows = if (filtering) filterRows(state.rows, state.categories, filter) else monthRows
    val eventRows = state.rows.filter { it.entry.eventId == selectedEvent && selectedEvent != null }

    fun startEntry(row: LedgerRow? = null) {
        clearError()
        editingId = row?.entry?.id
        initialEvent = if (tab == 1) selectedEvent else null
        editorOpen = true
    }

    Scaffold(
        topBar = {
            TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), title = { Text(if (toolsOpen) "账本工具" else if (tab == 1 && event != null) event.name else listOf("随手账本", "事件", "消费结构")[tab]) },
                navigationIcon = {
                    if (toolsOpen) TextButton(enabled = !state.saving, onClick = { toolsOpen = false; clearError() }) { Text("返回") }
                    else if (tab == 1 && event != null) TextButton(onClick = { selectedEvent = null }) { Text("返回") }
                }, actions = { if (!toolsOpen) TextButton(enabled = !state.loading && !state.saving, onClick = { toolsOpen = true; clearError() }) { Text("工具") } })
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                listOf("流水", "事件", "统计").forEachIndexed { index, title ->
                    NavigationBarItem(selected = tab == index, enabled = !state.saving, onClick = { tab = index; toolsOpen = false },
                        icon = { LedgerNavIcon(index) }, label = { Text(title) })
                }
            }
        },
        floatingActionButton = {
            if (!toolsOpen && !state.loading && !state.saving && state.categories.isNotEmpty()) {
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
            state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
            if (toolsOpen) {
                item { backupTools() }
                item { HorizontalDivider() }
                item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { CategoryManager(state, setCategoryHidden) } }
            }
            if (!toolsOpen && tab == 0 && !filtering) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { month = YearMonth.parse(month).minusMonths(1).toString() }) { Text("上月") }
                        Text(month, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
                        TextButton(onClick = { month = YearMonth.parse(month).plusMonths(1).toString() }) { Text("下月") }
                    }
                }
                item { TotalCard(monthRows, "本月支出") }
            }
            if (!toolsOpen) when (tab) {
                0 -> {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { filterOpen = true }) { Text("搜索与筛选") }
                            if (filtering) TextButton(onClick = { filtering = false; filter = LedgerFilter() }) { Text("清除筛选") }
                        }
                    }
                    if (filtering) {
                        item { Text("筛选结果 · ${displayRows.size} 笔", style = MaterialTheme.typography.titleMedium) }
                        item { Text(listOfNotNull(
                            if (filter.start.isNotEmpty() || filter.end.isNotEmpty()) "${filter.start.ifEmpty { "不限" }} — ${filter.end.ifEmpty { "不限" }}" else null,
                            filter.query.takeIf { it.isNotBlank() }?.let { "备注：$it" },
                            if (filter.kind == "expense") "支出" else if (filter.kind == "income") "收入" else null,
                            state.categories.find { it.id == filter.categoryId }?.name,
                            if (filter.eventId == "none") "无事件" else state.events.find { it.id == filter.eventId }?.name,
                        ).joinToString(" · ").ifEmpty { "全部流水" }, style = MaterialTheme.typography.bodySmall) }
                        item { TotalCard(displayRows, "筛选结果支出") }
                    } else item { Text("本月流水 · ${displayRows.size} 笔", style = MaterialTheme.typography.titleMedium) }
                    if (displayRows.isEmpty()) item { EmptyText(if (filtering) "无匹配流水" else "本月暂无流水") }
                    displayRows.groupBy { it.entry.occurredOn }.toSortedMap(reverseOrder()).forEach { (date, rows) ->
                        item(key = "date-$date") { Text("$date · ${if (filtering) "筛选内" else "当日"}支出 ¥ ${Money.format(expenseTotal(rows))}", style = MaterialTheme.typography.labelLarge) }
                        items(rows, key = { "entry-${it.entry.id}" }) { row -> EntryCard(row) { startEntry(row) } }
                    }
                }
                1 -> if (event == null) {
                    item { OutlinedButton(onClick = { clearError(); creatingEvent = true }) { Text("＋ 新建事件") } }
                    if (state.events.isEmpty()) item { EmptyText("暂无事件") }
                    items(state.events, key = { it.id }) { item ->
                        val rows = state.rows.filter { it.entry.eventId == item.id }
                        OutlinedCard(onClick = { selectedEvent = item.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text("¥ ${Money.format(expenseTotal(rows))}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                Text("${rows.size} 笔支出 · ›", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    item { TotalCard(eventRows, "事件累计支出", showIncome = false) }
                    item { CategoryBreakdown(eventRows, state.categories, allowIncome = false) }
                    item { Text("事件流水", style = MaterialTheme.typography.titleMedium) }
                    if (eventRows.isEmpty()) item { EmptyText("暂无流水") }
                    items(eventRows, key = { it.entry.id }) { row -> EntryCard(row) { startEntry(row) } }
                }
                2 -> {
                    item { StatisticsPanel(state.rows, state.categories) }
                }
            }
        }
    }

    if (filterOpen) FilterDialog(filter, state, { filterOpen = false }) { filter = it; filtering = true }
    if (editorOpen) {
        val existing = state.rows.find { it.entry.id == editingId }?.entry
        EntryEditor(
            initial = existing?.toDraft() ?: EntryDraft(eventId = initialEvent, categoryId = recentCategories(state).firstOrNull()?.id ?: visibleExpenseCategories(state).firstOrNull()?.id.orEmpty()),
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
                    OutlinedTextField(name, { name = it }, label = { Text("事件名称") }, singleLine = true)
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
fun ChoiceField(label: String, value: String, choices: List<Pair<String, String>>, enabled: Boolean = true, choose: (String) -> Unit) {
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
    var selectingCategory by rememberSaveable { mutableStateOf(false) }
    val recent = recentCategories(state)
    val defaultCategory = recent.firstOrNull()?.id ?: visibleExpenseCategories(state).firstOrNull()?.id.orEmpty()

    AlertDialog(
        onDismissRequest = dismiss,
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
                    ChoiceField("关联事件", state.events.find { it.id == eventId }?.name ?: "无事件", listOf("" to "无事件") + state.events.map { it.id to it.name }, !state.saving) { eventId = it.ifEmpty { null } }
                }
                LedgerDateField("记账日期", date, !state.saving) { date = it }
                OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, maxLines = 3, enabled = !state.saving)
                state.error?.let { ErrorText(it) }
                if (editing) TextButton(enabled = !state.saving, onClick = { confirmDelete = true }) { Text("删除这笔记录", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(enabled = !state.saving, onClick = { save(EntryDraft(id, kind, amount, currency, rmb, categoryId, eventId, date, note)) }) { Text(if (state.saving) "保存中…" else "保存") } },
        dismissButton = { TextButton(enabled = !state.saving, onClick = dismiss) { Text("取消") } },
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
