package com.spsir.ledger

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable
fun StatisticsPanel(rows: List<LedgerRow>, categories: List<Category>) {
    var periodName by rememberSaveable { mutableStateOf(StatPeriod.MONTH.name) }
    var anchorText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val period = StatPeriod.valueOf(periodName)
    val anchor = LocalDate.parse(anchorText)
    val range = statRange(anchor, period)
    val selectedRows = remember(rows, range) { rows.filter { range.contains(it.entry.occurredOn) } }
    val totals = remember(selectedRows) { incomeExpense(selectedRows) }
    val buckets = remember(selectedRows, range, period) { trendBuckets(selectedRows, anchor, period) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPeriod.entries.forEach { option ->
                FilterChip(selected = option == period, onClick = { periodName = option.name },
                    label = { Text("${option.label}度") }, modifier = Modifier.weight(1f).testTag("period-${option.name}"), shape = MaterialTheme.shapes.small)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { anchorText = shiftPeriod(anchor, period, -1).toString() }) { Text("上一${period.label}") }
            TextButton(onClick = { pickingDate = true }) { Text("选择日期") }
            TextButton(onClick = { anchorText = shiftPeriod(anchor, period, 1).toString() }) { Text("下一${period.label}") }
        }
        Text(if (range.start == range.end) range.start.toString() else "${range.start} ～ ${range.end}",
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("stat-range"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (period == StatPeriod.WEEK) "自然周 · 周一至周日" else "按记账日期统计 · 人民币", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { anchorText = LocalDate.now().toString() }, contentPadding = PaddingValues(0.dp)) { Text("回到本${period.label}") }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${period.label}度收支一览", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TotalAmount("支出", totals.expense, Modifier.weight(1f).testTag("stat-expense"))
                    TotalAmount("收入", totals.income, Modifier.weight(1f).testTag("stat-income"))
                    TotalAmount("收支差额", totals.net, Modifier.weight(1f).testTag("stat-net"))
                }
                Text("${selectedRows.size} 笔记录 · 收支差额不是账户余额", style = MaterialTheme.typography.bodySmall)
            }
        }
        IncomeExpenseChart(buckets, period)
        key(period, range) { CategoryBreakdown(selectedRows, categories) }
        Text("事件支出包含在全部统计中，只计算一次。外币使用手动填写的人民币金额；没有记录的日期按 0 展示。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (pickingDate) {
        var input by rememberSaveable { mutableStateOf(anchorText) }
        var error by rememberSaveable { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { pickingDate = false }, title = { Text("选择统计日期") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(input, { input = it; error = null }, label = { Text("日期（YYYY-MM-DD）") },
                        singleLine = true, modifier = Modifier.testTag("stat-date-input"))
                    Text("周、月、年视图会展示该日期所在的完整周期。")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = {
                try { anchorText = Money.date(input); pickingDate = false }
                catch (e: IllegalArgumentException) { error = e.message }
            }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun TotalAmount(label: String, value: Long, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text("¥ ${Money.format(value)}", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun IncomeExpenseChart(buckets: List<TrendBucket>, period: StatPeriod) {
    val incomeColor = MaterialTheme.colorScheme.primary
    val expenseColor = ExpenseColor
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fontSize = with(LocalDensity.current) { 10.sp.toPx() }
    val max = buckets.maxOf { maxOf(it.totals.income, it.totals.expense) }
    var selected by rememberSaveable(buckets) { mutableIntStateOf(0) }
    var expanded by rememberSaveable(period) { mutableStateOf(false) }
    val current = buckets[selected.coerceIn(buckets.indices)]
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (period == StatPeriod.DAY) "当日收支对比" else "收支趋势", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Legend("支出", expenseColor)
                Legend("收入", incomeColor)
            }
            Text("单位：元 · 最高 ${Money.format(max)}", style = MaterialTheme.typography.bodySmall)
            if (max == 0L) {
                Text("本期没有收支记录", modifier = Modifier.padding(vertical = 24.dp))
            } else {
                Canvas(Modifier.fillMaxWidth().height(170.dp).testTag("income-expense-chart")
                    .semantics { contentDescription = "收支柱状图：左柱支出，右柱收入；点击柱组查看金额，下方可展开全部明细。" }
                    .pointerInput(buckets) {
                        detectTapGestures { point -> selected = (point.x / (size.width.toFloat() / buckets.size)).toInt().coerceIn(buckets.indices) }
                    }) {
                    val bottom = size.height - 26.dp.toPx()
                    val top = 8.dp.toPx()
                    val available = bottom - top
                    val step = size.width / buckets.size
                    val bar = minOf(step * 0.3f, 32.dp.toPx())
                    for (i in 0..2) {
                        val y = top + available * i / 2
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColor.toArgb(); textSize = fontSize; textAlign = Paint.Align.CENTER }
                    buckets.forEachIndexed { index, bucket ->
                        val center = (index + 0.5f) * step
                        if (index == selected) drawRect(gridColor.copy(alpha = 0.25f), Offset(index * step, top), Size(step, available))
                        val expenseHeight = (bucket.totals.expense.toDouble() / max * available).toFloat()
                        val incomeHeight = (bucket.totals.income.toDouble() / max * available).toFloat()
                        drawRect(expenseColor, Offset(center - bar - step * 0.03f, bottom - expenseHeight), Size(bar, expenseHeight))
                        drawRect(incomeColor, Offset(center + step * 0.03f, bottom - incomeHeight), Size(bar, incomeHeight))
                        val showLabel = buckets.size <= 12 || index % 5 == 0 || index == buckets.lastIndex
                        if (showLabel) drawContext.canvas.nativeCanvas.drawText(bucket.label, center, size.height - 5.dp.toPx(), paint)
                    }
                }
                Text("${current.label} · 支出 ¥ ${Money.format(current.totals.expense)} · 收入 ¥ ${Money.format(current.totals.income)}",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("trend-selection"))
            }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("trend-details")) { Text(if (expanded) "收起明细" else "展开收支明细") }
            if (expanded) buckets.forEach { bucket ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(bucket.range.start.toString(), style = MaterialTheme.typography.labelLarge)
                    Text("支出 ¥ ${Money.format(bucket.totals.expense)} · 收入 ¥ ${Money.format(bucket.totals.income)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.padding(top = 4.dp).size(10.dp).background(color, MaterialTheme.shapes.small))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CategoryBreakdown(rows: List<LedgerRow>, categories: List<Category>, allowIncome: Boolean = true) {
    var kind by rememberSaveable { mutableStateOf("expense") }
    var parentId by rememberSaveable { mutableStateOf<String?>(null) }
    val parent = categories.find { it.id == parentId }
    val slices = remember(rows, categories, kind, parentId) { categoryShares(rows, categories, kind, parentId) }
    val total = slices.sumOf { it.amount }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("各类别占比", style = MaterialTheme.typography.titleMedium)
        if (allowIncome) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("expense" to "支出占比", "income" to "收入占比").forEach { (key, label) ->
                FilterChip(selected = key == kind, onClick = { kind = key; parentId = null }, label = { Text(label) })
            }
        }
        if (parent != null) {
            TextButton(onClick = { parentId = null }) { Text("返回全部类别") }
            Text("${parent.name} · 占该类别${if (kind == "expense") "支出" else "收入"}的比例")
        } else Text("占本期${if (kind == "expense") "支出" else "收入"}的比例 · 点大类查看细分", style = MaterialTheme.typography.bodySmall)
        if (slices.isEmpty()) Text("本期暂无${if (kind == "expense") "支出" else "收入"}，不计算占比。", modifier = Modifier.padding(vertical = 12.dp))
        slices.forEach { slice ->
            val hasChildren = parentId == null && categories.any { it.parentId == slice.id }
            OutlinedCard(onClick = { if (hasChildren) parentId = slice.id }, enabled = hasChildren,
                colors = CardDefaults.outlinedCardColors(disabledContainerColor = MaterialTheme.colorScheme.surface, disabledContentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().testTag("share-${slice.id}")) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(slice.label, modifier = Modifier.weight(1f))
                        Text(sharePercent(slice.amount, total))
                    }
                    LinearProgressIndicator(progress = { if (total == 0L) 0f else (slice.amount.toDouble() / total).toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text("¥ ${Money.format(slice.amount)}${if (hasChildren) " · 查看细分 ›" else ""}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
