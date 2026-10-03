package com.spsir.ledger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun BackupTools(state: LedgerState, vm: LedgerViewModel) {
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> vm.exportBackup(uri) } }
    val exportSafety = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> vm.exportBackup(uri, true) } }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::inspectBackup) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("本地备份与恢复", style = MaterialTheme.typography.titleLarge)
        Button(enabled = !state.saving, onClick = { export.launch("SPSir-${LocalDate.now()}.json") }) { Text("导出完整备份") }
        OutlinedButton(enabled = !state.saving, onClick = { restore.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) { Text("选择备份恢复") }
        HorizontalDivider()
        Text("覆盖前副本", style = MaterialTheme.typography.titleMedium)
        Text("仅保留最近一次副本，卸载后丢失。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = !state.saving, onClick = vm::inspectSafety) { Text("恢复副本") }
        TextButton(enabled = !state.saving, onClick = { exportSafety.launch("SPSir-before-restore-${LocalDate.now()}.json") }) { Text("导出副本") }
        if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    state.backupPreview?.let { backup ->
        AlertDialog(onDismissRequest = vm::dismissBackup, title = { Text("确认覆盖账本？") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("备份时间：${backup.exportedAt}\n${backup.entries.size} 笔流水 · ${backup.events.size} 个事件\n${backup.hidden.size} 个隐藏分类")
                Text("将覆盖当前账本，不合并。覆盖前会保存副本。")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { Button(enabled = !state.saving, onClick = vm::restoreBackup) { Text(if (state.saving) "恢复中…" else "确认覆盖恢复") } },
            dismissButton = { TextButton(enabled = !state.saving, onClick = vm::dismissBackup) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerDateField(label: String, value: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("date-$label"), shape = MaterialTheme.shapes.small) {
        Text("$label：${value.ifEmpty { "不限" }}")
    }
    if (open) {
        val date = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), yearRange = 1..9999)
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                onChange(Instant.ofEpochMilli(picker.selectedDateMillis!!).atOffset(ZoneOffset.UTC).toLocalDate().toString()); open = false
            }) { Text("确定日期") } },
            dismissButton = { Row {
                TextButton(onClick = { onChange(LocalDate.now().toString()); open = false }) { Text("今天") }
                TextButton(onClick = { open = false }) { Text("取消") }
            } },
        ) { DatePicker(picker, title = { Text(label, Modifier.padding(16.dp)) }) }
    }
}

@Composable
fun CategoryPicker(state: LedgerState, selected: String, original: String?, close: () -> Unit, choose: (String) -> Unit) {
    val available = visibleExpenseCategories(state).let { list -> list + state.categories.filter { it.id == original && it !in list } }
    var group by rememberSaveable { mutableStateOf(state.categories.find { it.id == selected }?.parentId ?: available.firstOrNull()?.parentId) }
    val groups = state.categories.filter { c -> c.parentId == null && available.any { it.parentId == c.id } }
    AlertDialog(onDismissRequest = close, title = { Text("选择支出分类") },
        text = {
            if (groups.isEmpty()) Text("暂无可用分类，请在工具中恢复显示。")
            else Row(Modifier.fillMaxWidth().heightIn(max = 400.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.weight(1f)) { items(groups) { c ->
                    FilterChip(selected = group == c.id, onClick = { group = c.id }, label = { Text(c.name) })
                } }
                LazyColumn(Modifier.weight(1.3f)) { items(available.filter { it.parentId == group }) { c ->
                    FilterChip(selected = selected == c.id, onClick = { choose(c.id); close() }, label = { Text(c.name) }, modifier = Modifier.testTag("choose-${c.id}"))
                } }
            }
        }, confirmButton = { TextButton(onClick = close) { Text("完成") } },
    )
}

@Composable
fun CategoryManager(state: LedgerState, setHidden: (String, Boolean) -> Unit) {
    val groups = state.categories.filter { it.kind == "expense" && it.parentId == null }
    var group by rememberSaveable { mutableStateOf(groups.firstOrNull()?.id.orEmpty()) }
    Text("分类显示", style = MaterialTheme.typography.titleLarge)
    Text("隐藏不影响历史记录。", style = MaterialTheme.typography.bodyMedium)
    ChoiceField("管理大类", groups.find { it.id == group }?.name ?: "选择分类", groups.map { it.id to it.name }) { group = it }
    state.categories.filter { it.id == group || (it.parentId == group && it.id !in legacyCategoryIds) }.forEach { c ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (c.id == group) "显示${c.name}" else c.name, modifier = Modifier.weight(1f).padding(top = 12.dp))
            Switch(checked = c.id !in state.hiddenCategories, enabled = !state.saving,
                onCheckedChange = { setHidden(c.id, !it) }, modifier = Modifier.testTag("visible-${c.id}"))
        }
    }
    if (group in state.hiddenCategories) Text("该大类已隐藏", style = MaterialTheme.typography.bodySmall)
}

@Composable
fun FilterDialog(initial: LedgerFilter, state: LedgerState, close: () -> Unit, apply: (LedgerFilter) -> Unit) {
    var query by rememberSaveable { mutableStateOf(initial.query) }
    var start by rememberSaveable { mutableStateOf(initial.start) }
    var end by rememberSaveable { mutableStateOf(initial.end) }
    var kind by rememberSaveable { mutableStateOf(initial.kind) }
    var category by rememberSaveable { mutableStateOf(initial.categoryId) }
    var event by rememberSaveable { mutableStateOf(initial.eventId) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text("查找全部流水") },
        modifier = Modifier.systemBarsPadding().imePadding(), properties = DialogProperties(decorFitsSystemWindows = false), text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("备注关键词") }, singleLine = true)
            LedgerDateField("起始日期", start) { start = it; error = null }
            LedgerDateField("结束日期", end) { end = it; error = null }
            TextButton(onClick = { start = ""; end = ""; error = null }) { Text("不限日期") }
            ChoiceField("收支类型", mapOf("" to "全部", "expense" to "支出", "income" to "收入").getValue(kind), listOf("" to "全部", "expense" to "支出", "income" to "收入")) { kind = it; category = "" }
            val categories = state.categories.filter { kind.isEmpty() || it.kind == kind }
            ChoiceField("大类或细类", state.categories.find { it.id == category }?.name ?: "全部分类",
                listOf("" to "全部分类") + categories.map { c -> c.id to (if (c.parentId == null) c.name else "${state.categories.find { it.id == c.parentId }?.name} / ${c.name}") }) { category = it }
            ChoiceField("事件", if (event == "none") "无事件" else state.events.find { it.id == event }?.name ?: "全部事件",
                listOf("" to "全部事件", "none" to "无事件") + state.events.map { it.id to it.name }) { event = it }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { Button(onClick = {
        if (start.isNotEmpty() && end.isNotEmpty() && start > end) error = "起始日期不能晚于结束日期"
        else { apply(LedgerFilter(query, start, end, kind, category, event)); close() }
    }) { Text("查找") } }, dismissButton = { TextButton(onClick = close) { Text("取消") } })
}
