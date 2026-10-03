package com.spsir.ledger

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class StatisticsTest {
    private fun row(id: String, date: String, amount: Long, category: String = "food.lunch", kind: String = "expense") =
        LedgerRow(LedgerEntry(id, kind, 999999, "NOK", amount, category, "trip", date, ""), "午餐", "饮食", "旅行")

    @Test fun weeksStartOnMondayAndCrossYears() {
        val expected = StatRange(LocalDate.parse("2025-12-29"), LocalDate.parse("2026-01-04"))
        assertEquals(expected, statRange(LocalDate.parse("2026-01-01"), StatPeriod.WEEK))
        assertEquals(expected, statRange(LocalDate.parse("2026-01-04"), StatPeriod.WEEK))
        assertEquals(LocalDate.parse("2026-01-05"), statRange(LocalDate.parse("2026-01-05"), StatPeriod.WEEK).start)
        assertTrue(expected.contains("2025-12-29"))
        assertTrue(expected.contains("2026-01-04"))
        assertFalse(expected.contains("2026-01-05"))
    }

    @Test fun leapMonthsYearsAndPeriodNavigationAreCalendarBased() {
        assertEquals(LocalDate.parse("2024-02-29"), statRange(LocalDate.parse("2024-02-01"), StatPeriod.MONTH).end)
        assertEquals(LocalDate.parse("2025-02-28"), statRange(LocalDate.parse("2025-02-01"), StatPeriod.MONTH).end)
        assertEquals(StatRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")), statRange(LocalDate.parse("2026-10-02"), StatPeriod.YEAR))
        assertEquals(LocalDate.parse("2026-02-01"), shiftPeriod(LocalDate.parse("2026-01-31"), StatPeriod.MONTH, 1))
        assertEquals(LocalDate.parse("2025-12-01"), shiftPeriod(LocalDate.parse("2026-01-31"), StatPeriod.MONTH, -1))
        assertEquals(LocalDate.parse("2025-01-01"), shiftPeriod(LocalDate.parse("2024-02-29"), StatPeriod.YEAR, 1))
    }

    @Test fun trendIncludesZerosAndUsesRmbWithoutCountingEventTwice() {
        val rows = listOf(row("one", "2026-01-01", 1234), row("two", "2026-01-31", 100),
            row("income", "2026-01-01", 2000, "income", "income"), row("outside", "2026-02-01", 5000))
        val buckets = trendBuckets(rows, LocalDate.parse("2026-01-15"), StatPeriod.MONTH)
        assertEquals(31, buckets.size)
        assertEquals(IncomeExpense(2000, 1234), buckets.first().totals)
        assertEquals(IncomeExpense(0, 0), buckets[1].totals)
        assertEquals(1334L, buckets.sumOf { it.totals.expense })
        assertEquals(2000L, buckets.sumOf { it.totals.income })
        val day = trendBuckets(rows, LocalDate.parse("2026-01-01"), StatPeriod.DAY)
        assertEquals(1, day.size)
        assertEquals(766L, day.single().totals.net)
    }

    @Test fun yearHasTwelveMonthlyBucketsAndWeekHasSevenDays() {
        val rows = listOf(row("one", "2025-12-31", 100), row("two", "2026-01-01", 200), row("three", "2026-12-31", 300))
        val year = trendBuckets(rows, LocalDate.parse("2026-06-01"), StatPeriod.YEAR)
        assertEquals(12, year.size)
        assertEquals(200L, year.first().totals.expense)
        assertEquals(300L, year.last().totals.expense)
        assertEquals(500L, year.sumOf { it.totals.expense })
        val week = trendBuckets(rows, LocalDate.parse("2026-01-01"), StatPeriod.WEEK)
        assertEquals(7, week.size)
        assertEquals(300L, week.sumOf { it.totals.expense })
    }

    @Test fun categorySharesKeepIncomeSeparateAndDrillDownUsesItsOwnTotal() {
        val rows = listOf(row("meal", "2026-10-02", 100, "food.breakfast"),
            row("dinner", "2026-10-02", 200, "food.dinner"), row("film", "2026-10-02", 700, "photo.film"),
            row("pay", "2026-10-02", 99999, "income", "income"))
        val shares = categoryShares(rows, presetCategories(), "expense")
        assertEquals(listOf("photo", "food"), shares.map { it.id })
        assertEquals("70.0%", sharePercent(shares.first().amount, shares.sumOf { it.amount }))
        val food = categoryShares(rows, presetCategories(), "expense", "food")
        assertEquals(300L, food.sumOf { it.amount })
        assertEquals("66.7%", sharePercent(food.first().amount, food.sumOf { it.amount }))
        assertEquals(99999L, categoryShares(rows, presetCategories(), "income").single().amount)
    }

    @Test fun emptyAndTinySharesAreSafeAndHonest() {
        assertTrue(categoryShares(emptyList(), presetCategories(), "expense").isEmpty())
        assertEquals("0.0%", sharePercent(0, 0))
        assertEquals("<0.1%", sharePercent(1, 100000))
        assertEquals(IncomeExpense(0, 0), incomeExpense(emptyList()))
        assertTrue(trendBuckets(emptyList(), LocalDate.parse("2024-02-01"), StatPeriod.MONTH).all { it.totals.expense == 0L })
    }

    @Test fun presetsHaveUniqueIdsAndValidParentsAndKeepSimpleIncome() {
        val all = presetCategories()
        assertEquals(all.size, all.map { it.id }.toSet().size)
        val parents = all.filter { it.parentId == null && it.kind == "expense" }.map { it.id }.toSet()
        assertTrue(all.filter { it.parentId != null }.all { it.parentId in parents })
        assertEquals(listOf("income"), all.filter { it.kind == "income" }.map { it.id })
        val ids = all.map { it.id }
        assertTrue(ids.containsAll(listOf("food.late", "food.alcohol", "daily.stationery", "daily.tools", "daily.books",
            "routine.bath", "routine.phone", "routine.data", "routine.delivery", "routine.subscription", "transport.subway", "transport.bus", "photo.processing")))
    }
}
