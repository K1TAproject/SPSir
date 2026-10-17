package com.spsir.ledger

data class LedgerFilter(
    val query: String = "",
    val start: String = "",
    val end: String = "",
    val kind: String = "",
    val categoryId: String = "",
    val eventId: String = "",
)

fun filterRows(rows: List<LedgerRow>, categories: List<Category>, filter: LedgerFilter): List<LedgerRow> {
    val categoryIds = categories.filter { it.id == filter.categoryId || it.parentId == filter.categoryId }.map { it.id }.toSet()
    return rows.filter { row ->
        val e = row.entry
        e.note.contains(filter.query.trim(), ignoreCase = true) &&
            (filter.start.isEmpty() || e.occurredOn >= filter.start) &&
            (filter.end.isEmpty() || e.occurredOn <= filter.end) &&
            (filter.kind.isEmpty() || e.kind == filter.kind) &&
            (filter.categoryId.isEmpty() || e.categoryId in categoryIds) &&
            (filter.eventId.isEmpty() || if (filter.eventId == "none") e.eventId == null else e.eventId == filter.eventId)
    }.sortedByDescending { it.entry.occurredOn }
}

fun visibleExpenseCategories(state: LedgerState): List<Category> = state.categories.filter {
    it.kind == "expense" && it.parentId != null && it.id !in legacyCategoryIds &&
        it.id !in state.hiddenCategories && it.parentId !in state.hiddenCategories
}

/**
 * 最近使用按记账笔数排序：先比次数多的，次数相同再比最近一次记账日期新的，
 * 仍相同则按预设分类顺序，保证列表稳定。隐藏分类和旧分类不进入候选。
 */
fun recentCategories(state: LedgerState): List<Category> {
    val visible = visibleExpenseCategories(state).associateBy { it.id }
    val counts = HashMap<String, Int>()
    val lastUsed = HashMap<String, String>()
    state.rows.forEach { row ->
        val entry = row.entry
        if (entry.kind != "expense" || entry.categoryId !in visible) return@forEach
        counts[entry.categoryId] = (counts[entry.categoryId] ?: 0) + 1
        val seen = lastUsed[entry.categoryId]
        if (seen == null || entry.occurredOn > seen) lastUsed[entry.categoryId] = entry.occurredOn
    }
    return counts.keys.sortedWith(
        compareByDescending<String> { counts[it] }
            .thenByDescending { lastUsed[it].orEmpty() }
            .thenBy { visible.getValue(it).sortOrder },
    ).take(6).mapNotNull { visible[it] }
}
