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

    @Test fun recentChoicesRankByUsageCountNotByLatestDate() {
        val rows = listOf(
            row("a", "2026-01-05", "photo.film"),
            row("b", "2026-02-05", "photo.film"),
            row("c", "2026-03-05", "photo.film"),
            row("d", "2026-10-09", "food.dinner"),
            row("e", "2026-10-08", "food.breakfast"),
            row("f", "2026-10-07", "food.breakfast"),
        )
        val state = LedgerState(categories = categories, rows = rows)
        // 3 次 > 2 次 > 1 次；次数相同再比最近一次记账日期。
        assertEquals(listOf("photo.film", "food.breakfast", "food.dinner"), recentCategories(state).map { it.id })
    }

    @Test fun recentChoicesKeepSixVisibleExpenseCategoriesAndIgnoreIncome() {
        val rows = listOf(
            row("a", "2026-10-01", "food.breakfast"),
            row("b", "2026-10-02", "food.lunch"),
            row("c", "2026-10-03", "food.dinner"),
            row("d", "2026-10-04", "photo.film"),
            row("e", "2026-10-05", "photo.care"),
            row("f", "2026-10-06", "photo.gear"),
            row("g", "2026-10-07", "daily.books"),
            row("h", "2026-10-08", "income", kind = "income"),
            row("i", "2026-10-09", "food.snacks"),
        )
        val state = LedgerState(categories = categories, rows = rows)
        // 收入与旧分类不参与；同等次数下保留最近记账的 6 项。
        val expected = listOf("daily.books", "photo.gear", "photo.care", "photo.film", "food.dinner", "food.lunch")
        assertEquals(expected, recentCategories(state).map { it.id })
        assertEquals(expected, recentCategories(state.copy(rows = rows.reversed())).map { it.id })
    }
}
