package com.spsir.ledger

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties
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
                LedgerScreen(state, vm::save, vm::createEvent, vm::delete, vm::clearError, vm::setCategoryHidden,
                    createPublic = vm::createPublic, savePublic = vm::savePublic, deletePublic = vm::deletePublic,
                    saveMember = vm::saveMember, deleteMember = vm::deleteMember, renameEvent = vm::renameEvent, archiveEvent = vm::archiveEvent,
                    saveTransfer = vm::saveTransfer, deleteTransfer = vm::deleteTransfer, backupTools = { BackupTools(state, vm) })
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
    createPublic: (String, List<String>, () -> Unit) -> Unit = { _, _, done -> done() },
    savePublic: (PublicDraft, () -> Unit) -> Unit = { _, done -> done() },
    deletePublic: (String, String, () -> Unit) -> Unit = { _, _, done -> done() },
    saveMember: (String, String?, String, () -> Unit) -> Unit = { _, _, _, done -> done() },
    deleteMember: (String, String) -> Unit = { _, _ -> },
    backupTools: @Composable () -> Unit = {},
    renameEvent: (String, String, () -> Unit) -> Unit = { _, _, done -> done() },
    archiveEvent: (String, Boolean, Boolean, () -> Unit) -> Unit = { _, _, _, done -> done() },
    saveTransfer: (TransferDraft, Boolean, () -> Unit) -> Unit = { _, _, done -> done() },
    deleteTransfer: (String, String, () -> Unit) -> Unit = { _, _, done -> done() },
) {
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var eventMenu by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var archiving by rememberSaveable { mutableStateOf(false) }
    var sharingBill by rememberSaveable { mutableStateOf(false) }
    var transferOpen by rememberSaveable { mutableStateOf(false) }
    var transferEditing by rememberSaveable { mutableStateOf(false) }
    var transferInitial by rememberSaveable(stateSaver = transferDraftSaver) {
        mutableStateOf(TransferDraft(eventId = "", fromId = "", toId = ""))
    }
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
    var publicEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var publicExpenseId by rememberSaveable { mutableStateOf<String?>(null) }
    var publicInitial by rememberSaveable(stateSaver = listSaver(
        save = { listOf(it.id, it.amount, it.currency, it.rmb, it.categoryId, it.eventId.orEmpty(), it.date, it.note) },
        restore = { EntryDraft(id = it[0], amount = it[1], currency = it[2], rmb = it[3], categoryId = it[4], eventId = it[5].ifEmpty { null }, date = it[6], note = it[7]) },
    )) { mutableStateOf(EntryDraft()) }
    var managingMembers by rememberSaveable { mutableStateOf(false) }
    var settlementTab by rememberSaveable(selectedEvent) { mutableStateOf(false) }
    val publicBundle = state.publicEvents.find { it.event.eventId == selectedEvent }
    fun startPublic(bundle: PublicBundle, row: SharedExpense? = null, carried: EntryDraft? = null) {
        clearError()
        publicEventId = bundle.event.eventId
        publicExpenseId = row?.expense?.id
        publicInitial = row?.expense?.let { e -> EntryDraft(e.id, "expense", Money.format(e.originalMinor, MoneyCurrency.valueOf(e.currency).digits), e.currency, Money.format(e.rmbMinor), e.categoryId, e.eventId, e.occurredOn, e.note) }
            ?: carried?.copy(eventId = bundle.event.eventId)
            ?: EntryDraft(eventId = bundle.event.eventId, categoryId = recentCategories(state).firstOrNull()?.id ?: visibleExpenseCategories(state).firstOrNull()?.id.orEmpty())
        editorOpen = false
    }
    val event = state.events.find { it.id == selectedEvent }
    val listedEvents = state.events.filter { it.archived == showArchived }
    val monthRows = remember(state.rows, month) { state.rows.filter { it.entry.occurredOn.startsWith(month) } }
    val displayRows = remember(state.rows, state.categories, filtering, filter, monthRows) { if (filtering) filterRows(state.rows, state.categories, filter) else monthRows }
    val rowsByEvent = remember(state.rows) { state.rows.groupBy { it.entry.eventId } }
    val eventRows = if (selectedEvent == null) emptyList() else rowsByEvent[selectedEvent].orEmpty()
    val publicBalances = remember(state.publicEvents) { state.publicEvents.associate { it.event.eventId to it.balances() } }
    val dailyRows = remember(displayRows) { displayRows.groupBy { it.entry.occurredOn }.toSortedMap(reverseOrder()) }
    val editorStates = rememberSaveableStateHolder()
    BackHandler(enabled = toolsOpen || (tab == 1 && selectedEvent != null) || tab != 0 || filtering) {
        if (!state.saving) {
            when {
                toolsOpen -> { toolsOpen = false; clearError() }
                tab == 1 && selectedEvent != null -> selectedEvent = null
                tab != 0 -> tab = 0
                filtering -> { filtering = false; filter = LedgerFilter() }
            }
        }
    }

    fun startEntry(row: LedgerRow? = null) {
        val source = state.publicEvents.firstOrNull { b -> b.expenses.any { it.expense.entryId == row?.entry?.id && row != null } }
        if (source != null) { startPublic(source, source.expenses.single { it.expense.entryId == row!!.entry.id }); return }
        if (row == null && tab == 1 && publicBundle != null) { startPublic(publicBundle); return }
        clearError()
        editingId = row?.entry?.id
        initialEvent = if (tab == 1) selectedEvent else null
        editorOpen = true
    }

    Scaffold(
        topBar = {
            TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), title = { Text(if (toolsOpen) "账本工具" else if (tab == 1 && event != null) event.name else listOf("SPSir", "事件", "消费结构")[tab]) },
                navigationIcon = {
                    if (toolsOpen) TextButton(enabled = !state.saving, onClick = { toolsOpen = false; clearError() }) { Text("返回") }
                    else if (tab == 1 && event != null) TextButton(onClick = { selectedEvent = null }) { Text("返回") }
                }, actions = {
                    if (!toolsOpen && tab == 1 && event != null) Box {
                        TextButton(enabled = !state.saving, onClick = { eventMenu = true }) { Text("更多") }
                        DropdownMenu(eventMenu, { eventMenu = false }) {
                            DropdownMenuItem(text = { Text("改名") }, onClick = { eventMenu = false; clearError(); renaming = true })
                            DropdownMenuItem(text = { Text(if (event.archived) "取消归档" else "归档") }, onClick = {
                                eventMenu = false; clearError()
                                if (event.archived) archiveEvent(event.id, false, false) {} else archiving = true
                            })
                        }
                    }
                    if (!toolsOpen) TextButton(enabled = !state.loading && !state.saving, onClick = { toolsOpen = true; clearError() }) { Text("工具") }
                })
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
            if (!toolsOpen && !state.loading && !state.saving && state.categories.isNotEmpty() && !(tab == 1 && event?.archived == true)) {
                ExtendedFloatingActionButton(onClick = { startEntry() }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Text(if (tab == 1 && publicBundle != null) "＋ 记开支" else "＋ 记一笔") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!toolsOpen && tab == 1 && event?.archived == true) item { Text("已归档 · 取消归档后可编辑", style = MaterialTheme.typography.bodySmall) }
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
                    dailyRows.forEach { (date, rows) ->
                        item(key = "date-$date") { Text("$date · ${if (filtering) "筛选内" else "当日"}支出 ¥ ${Money.format(expenseTotal(rows))}", style = MaterialTheme.typography.labelLarge) }
                        items(rows, key = { "entry-${it.entry.id}" }) { row -> EntryCard(row) { startEntry(row) } }
                    }
                }
                1 -> if (event == null) {
                    item { OutlinedButton(onClick = { clearError(); creatingEvent = true }) { Text("＋ 新建事件") } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!showArchived, { showArchived = false }, label = { Text("进行中") })
                        FilterChip(showArchived, { showArchived = true }, label = { Text("已归档") })
                    } }
                    if (listedEvents.isEmpty()) item { EmptyText(if (showArchived) "暂无已归档事件" else "暂无事件") }
                    items(listedEvents, key = { it.id }) { item ->
                        val rows = rowsByEvent[item.id].orEmpty()
                        val shared = state.publicEvents.find { it.event.eventId == item.id }
                        val balances = publicBalances[item.id].orEmpty()
                        OutlinedCard(onClick = { selectedEvent = item.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                if (shared != null) Text("公共 · ${shared.members.size} 人")
                                Text("${if (shared != null) "事件总额 " else ""}¥ ${Money.format(if (shared != null) balances.fold(0L) { sum, b -> Math.addExact(sum, b.paid) } else expenseTotal(rows))}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                if (shared != null) Text("我的支出 ¥ ${Money.format(balances.single { it.member.isSelf }.share)}")
                                Text("${shared?.expenses?.size ?: rows.size} 笔支出 · ›", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (publicBundle != null) {
                    publicEventContent(publicBundle, publicBalances.getValue(publicBundle.event.eventId), state, settlementTab,
                        { settlementTab = it }, { clearError(); managingMembers = true },
                        transfer = { existing, suggestion ->
                            clearError(); transferEditing = existing != null
                            val members = publicBundle.members.sortedBy { it.position }
                            transferInitial = existing?.toDraft() ?: TransferDraft(eventId = event.id,
                                fromId = suggestion?.from ?: members.first().id,
                                toId = suggestion?.to ?: members.getOrNull(1)?.id.orEmpty(),
                                amount = suggestion?.amount?.let { Money.format(it) }.orEmpty())
                            transferOpen = true
                        }, share = { sharingBill = true }) { startPublic(publicBundle, it) }
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

    if (event != null && !state.loading) {
        if (renaming) editorStates.SaveableStateProvider("rename-${event.id}") {
            RenameEventDialog(event, state,
                { renaming = false; editorStates.removeState("rename-${event.id}"); clearError() },
                { name -> renameEvent(event.id, name) { renaming = false; editorStates.removeState("rename-${event.id}") } })
        }
        if (archiving) AlertDialog(onDismissRequest = { if (!state.saving) archiving = false },
            title = { Text(if (publicBalances[event.id]?.any { it.balance != 0L } == true) "尚未结清，仍要归档？" else "归档事件？") },
            text = { Column { Text("归档后保留统计，编辑前需取消归档。"); state.error?.let { ErrorText(it) } } },
            confirmButton = { TextButton(enabled = !state.saving, onClick = { archiveEvent(event.id, true, true) { archiving = false } }) { Text("确认归档") } },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { archiving = false }) { Text("取消") } })
        if (sharingBill && publicBundle != null) EventBillDialog(event, publicBundle, state.categories) { sharingBill = false }
    }
    val transferBundle = state.publicEvents.find { it.event.eventId == transferInitial.eventId }
    if (transferOpen && transferBundle != null && !state.loading) editorStates.SaveableStateProvider("transfer-${transferInitial.id}") {
        fun closeTransfer() { transferOpen = false; editorStates.removeState("transfer-${transferInitial.id}"); clearError() }
        TransferEditor(transferInitial, transferEditing, transferBundle, state,
            close = { if (!state.saving) closeTransfer() },
            save = { draft, confirmed -> saveTransfer(draft, confirmed) { closeTransfer() } },
            delete = { deleteTransfer(transferInitial.eventId, transferInitial.id) { closeTransfer() } })
    }
    if (filterOpen) FilterDialog(filter, state, { filterOpen = false }) { filter = it; filtering = true }
    if (editorOpen && !state.loading) editorStates.SaveableStateProvider("entry") {
        val existing = state.rows.find { it.entry.id == editingId }?.entry
        EntryEditor(
            initial = existing?.toDraft() ?: EntryDraft(eventId = initialEvent, categoryId = recentCategories(state).firstOrNull()?.id ?: visibleExpenseCategories(state).firstOrNull()?.id.orEmpty()),
            editing = editingId != null,
            state = state,
            dismiss = { if (!state.saving) { editorOpen = false; editorStates.removeState("entry"); clearError() } },
            save = { draft -> onSave(draft) { editorOpen = false; editorStates.removeState("entry") } },
            delete = { onDelete(it) { editorOpen = false; editorStates.removeState("entry") } },
            openPublic = { draft -> editorStates.removeState("entry"); startPublic(state.publicEvents.single { it.event.eventId == draft.eventId }, carried = draft) },
        )
    }
    val editingBundle = state.publicEvents.find { it.event.eventId == publicEventId }
    if (editingBundle != null && !state.loading) editorStates.SaveableStateProvider("public-${publicInitial.id}") {
        PublicExpenseEditor(publicInitial, editingBundle.expenses.find { it.expense.id == publicExpenseId }, editingBundle, state,
            close = { if (!state.saving) { publicEventId = null; editorStates.removeState("public-${publicInitial.id}"); clearError() } },
            save = { draft -> savePublic(draft) { publicEventId = null; editorStates.removeState("public-${publicInitial.id}") } },
            delete = { deletePublic(editingBundle.event.eventId, publicInitial.id) { publicEventId = null; editorStates.removeState("public-${publicInitial.id}") } })
    }
    if (managingMembers && publicBundle != null) PublicMembersDialog(publicBundle, state,
        { if (!state.saving) { managingMembers = false; clearError() } },
        { id, name, done -> saveMember(publicBundle.event.eventId, id, name, done) },
        { id -> deleteMember(publicBundle.event.eventId, id) })
    if (creatingEvent) {
        var shared by rememberSaveable { mutableStateOf(false) }
        var names by rememberSaveable { mutableStateOf(listOf<String>()) }
        var memberName by rememberSaveable { mutableStateOf("") }
        var memberError by rememberSaveable { mutableStateOf<String?>(null) }
        var name by rememberSaveable { mutableStateOf("") }
        val closeEvent = rememberDiscardChanges(name.isNotEmpty() || names.isNotEmpty() || memberName.isNotEmpty(), state.saving) {
            creatingEvent = false; clearError()
        }
        AlertDialog(
            onDismissRequest = closeEvent,
            title = { Text("新建事件") },
            modifier = Modifier.systemBarsPadding().imePadding().fillMaxWidth().padding(horizontal = 16.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("事件名称") }, singleLine = true, enabled = !state.saving)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!shared, { shared = false }, enabled = !state.saving, label = { Text("个人事件") })
                        FilterChip(shared, { shared = true }, enabled = !state.saving, label = { Text("公共事件") })
                    }
                    if (shared) {
                        Text("本机记录，多人分摊", style = MaterialTheme.typography.bodySmall)
                        Text("我")
                        names.forEach { n -> TextButton(enabled = !state.saving, onClick = { names = names - n }) { Text("$n ×") } }
                        OutlinedTextField(memberName, { memberName = it; memberError = null }, label = { Text("同行者昵称") }, enabled = !state.saving, singleLine = true)
                        TextButton(enabled = !state.saving, onClick = {
                            val n = memberName.trim()
                            if (n.length !in 1..20 || n == "我" || n in names) memberError = "昵称须为 1～20 字且不能重复"
                            else { names = names + n; memberName = ""; memberError = null }
                        }) { Text("添加同行者") }
                        memberError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    state.error?.let { ErrorText(it) }
                }
            },
            confirmButton = { TextButton(enabled = !state.saving, onClick = {
                if (shared) createPublic(name, names + listOfNotNull(memberName.trim().takeIf { it.isNotEmpty() })) { creatingEvent = false }
                else onCreateEvent(name) { creatingEvent = false }
            }) { Text("创建") } },
            dismissButton = { TextButton(enabled = !state.saving, onClick = closeEvent) { Text("取消") } },
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
fun ErrorText(text: String) {
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
