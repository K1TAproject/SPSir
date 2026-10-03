package com.spsir.ledger

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class StatPeriod(val label: String) { DAY("日"), WEEK("周"), MONTH("月"), YEAR("年") }

data class StatRange(val start: LocalDate, val end: LocalDate) {
    fun contains(date: String): Boolean = date >= start.toString() && date <= end.toString()
}

fun statRange(anchor: LocalDate, period: StatPeriod): StatRange {
    val start = when (period) {
        StatPeriod.DAY -> anchor
        StatPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        StatPeriod.MONTH -> anchor.withDayOfMonth(1)
        StatPeriod.YEAR -> anchor.withDayOfYear(1)
    }
    val end = when (period) {
        StatPeriod.DAY -> start
        StatPeriod.WEEK -> start.plusDays(6)
        StatPeriod.MONTH -> start.plusMonths(1).minusDays(1)
        StatPeriod.YEAR -> start.plusYears(1).minusDays(1)
    }
    return StatRange(start, end)
}

fun shiftPeriod(anchor: LocalDate, period: StatPeriod, direction: Long): LocalDate {
    val start = statRange(anchor, period).start
    return when (period) {
        StatPeriod.DAY -> start.plusDays(direction)
        StatPeriod.WEEK -> start.plusWeeks(direction)
        StatPeriod.MONTH -> start.plusMonths(direction)
        StatPeriod.YEAR -> start.plusYears(direction)
    }
}

data class IncomeExpense(val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

fun incomeExpense(rows: List<LedgerRow>) = IncomeExpense(
    rows.filter { it.entry.kind == "income" }.sumOf { it.entry.rmbMinor },
    expenseTotal(rows),
)

data class TrendBucket(val label: String, val range: StatRange, val totals: IncomeExpense)

fun trendBuckets(rows: List<LedgerRow>, anchor: LocalDate, period: StatPeriod): List<TrendBucket> {
    val range = statRange(anchor, period)
    val datedRows = rows.filter { range.contains(it.entry.occurredOn) }
    // Entries have a date, not an hour. A daily view must not invent hourly precision.
    val groups = datedRows.groupBy {
        if (period == StatPeriod.YEAR) it.entry.occurredOn.take(7) else it.entry.occurredOn
    }
    val starts = if (period == StatPeriod.YEAR) (0L..11L).map { range.start.plusMonths(it) }
    else generateSequence(range.start) { it.plusDays(1) }.takeWhile { it <= range.end }.toList()
    return starts.map { start ->
        val end = if (period == StatPeriod.YEAR) start.plusMonths(1).minusDays(1) else start
        val key = if (period == StatPeriod.YEAR) start.toString().take(7) else start.toString()
        val label = when (period) {
            StatPeriod.YEAR -> "${start.monthValue}月"
            StatPeriod.MONTH -> "${start.dayOfMonth}"
            else -> "${start.monthValue}/${start.dayOfMonth}"
        }
        TrendBucket(label, StatRange(start, end), incomeExpense(groups[key].orEmpty()))
    }
}

data class CategoryShare(val id: String, val label: String, val amount: Long)

fun categoryShares(rows: List<LedgerRow>, categories: List<Category>, kind: String, parentId: String? = null): List<CategoryShare> {
    val byId = categories.associateBy { it.id }
    val relevant = rows.filter { row ->
        row.entry.kind == kind && (parentId == null || byId[row.entry.categoryId]?.parentId == parentId)
    }
    return relevant.groupBy { row ->
        val category = byId[row.entry.categoryId]
        if (parentId == null) category?.parentId ?: row.entry.categoryId else row.entry.categoryId
    }.map { (id, items) ->
        CategoryShare(id, byId[id]?.name ?: items.first().categoryName, items.sumOf { it.entry.rmbMinor })
    }.sortedWith(compareByDescending<CategoryShare> { it.amount }.thenBy { it.id })
}

fun sharePercent(amount: Long, total: Long): String {
    if (total == 0L) return "0.0%"
    val percent = BigDecimal.valueOf(amount).multiply(BigDecimal(100))
        .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
    return if (amount > 0 && percent.signum() == 0) "<0.1%" else "${percent.toPlainString()}%"
}
