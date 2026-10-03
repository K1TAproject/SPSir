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

fun recentCategories(state: LedgerState): List<Category> {
    val visible = visibleExpenseCategories(state).associateBy { it.id }
    return state.rows.filter { it.entry.kind == "expense" }.sortedByDescending { it.entry.occurredOn }
        .mapNotNull { visible[it.entry.categoryId] }.distinctBy { it.id }.take(6)
}
