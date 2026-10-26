package com.spsir.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun StatisticsScopeControl(scope: StatisticsScope, events: List<LedgerEvent>, enabled: Boolean = true, open: () -> Unit) {
    Column {
        TextButton(onClick = open, enabled = enabled, modifier = Modifier.testTag("statistics-scope")) { Text("统计范围") }
        Text(scope.label(events), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("scope-label"))
    }
}

@Composable
fun StatisticsScopeDialog(initial: StatisticsScope, events: List<LedgerEvent>, publicEventIds: Set<String>,
    close: () -> Unit, apply: (StatisticsScope) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(initial.mode) }
    var eventId by rememberSaveable { mutableStateOf(initial.eventId) }
    val valid = mode == "all" || events.any { it.id == eventId }
    AlertDialog(onDismissRequest = close, title = { Text("统计范围") },
        modifier = Modifier.systemBarsPadding().fillMaxWidth().padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "全部", "only" to "仅含指定事件", "exclude" to "排除指定事件")
                .filter { events.isNotEmpty() || it.first == "all" }.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().selectable(mode == key, role = Role.RadioButton, onClick = { mode = key })
                        .testTag("scope-mode-$key"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(mode == key, onClick = null)
                        Text(label, Modifier.weight(1f))
                    }
                }
            if (mode != "all" && events.isNotEmpty()) {
                Text("选择事件", style = MaterialTheme.typography.labelLarge)
                events.forEach { event ->
                    Row(Modifier.fillMaxWidth().selectable(eventId == event.id, role = Role.RadioButton, onClick = { eventId = event.id })
                        .testTag("scope-event-${event.id}"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(eventId == event.id, onClick = null)
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(event.name)
                            Text((if (event.id in publicEventIds) "公共事件" else "个人事件") + if (event.archived) " · 已归档" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            TextButton(onClick = { mode = "all"; eventId = null }) { Text("恢复全部") }
        } },
        confirmButton = { TextButton(enabled = valid, onClick = { apply(if (mode == "all") StatisticsScope() else StatisticsScope(mode, eventId)) }) { Text("应用") } },
        dismissButton = { TextButton(onClick = close) { Text("取消") } })
}
