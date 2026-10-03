package com.spsir.ledger

import org.junit.Assert.*
import org.junit.Test

class LedgerFilterTest {
    private val categories = presetCategories()
    private fun row(id: String, date: String, category: String, event: String? = null, kind: String = "expense") =
        LedgerRow(LedgerEntry(id, kind, 100, "CNY", 100, category, event, date, "Trip 胶卷"), "分类", null, null)

    @Test fun searchSpansMonthsAndCombinesAllConditions() {
        val rows = listOf(row("a", "2026-09-01", "photo.film", "trip"), row("b", "2026-10-02", "photo.care", "trip"),
            row("c", "2026-10-03", "photo.film"), row("d", "2026-10-04", "income", kind = "income"))
        assertEquals(listOf("d", "c", "b", "a"), filterRows(rows, categories, LedgerFilter(query = "trip")).map { it.entry.id })
        val filter = LedgerFilter("胶卷", "2026-09-01", "2026-10-02", "expense", "photo", "trip")
        assertEquals(listOf("b", "a"), filterRows(rows, categories, filter).map { it.entry.id })
        assertEquals(listOf("a"), filterRows(rows, categories, filter.copy(categoryId = "photo.film")).map { it.entry.id })
        assertEquals(listOf("c"), filterRows(rows, categories, filter.copy(start = "", end = "", eventId = "none")).map { it.entry.id })
        assertTrue(filterRows(rows, categories, filter.copy(query = "不存在")).isEmpty())
        val edited = rows.filter { it.entry.id != "a" }.map { if (it.entry.id == "b") it.copy(entry = it.entry.copy(eventId = null)) else it }
        assertTrue(filterRows(edited, categories, filter).isEmpty())
    }

    @Test fun hiddenAndLegacyCategoriesNeverAppearInRecentChoicesButRemainSearchable() {
        val rows = listOf(row("a", "2026-10-01", "photo.film"), row("b", "2026-10-02", "food.lunch"), row("c", "2026-10-03", "food.snacks"))
        val state = LedgerState(categories = categories, rows = rows, hiddenCategories = setOf("photo", "food.lunch"))
        assertTrue(recentCategories(state).isEmpty())
        assertEquals(1, filterRows(rows, categories, LedgerFilter(categoryId = "photo")).size)
        assertEquals(listOf("food.lunch", "photo.film"), recentCategories(state.copy(hiddenCategories = emptySet())).map { it.id })
    }
}
