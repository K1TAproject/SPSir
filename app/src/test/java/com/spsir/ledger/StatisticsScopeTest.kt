package com.spsir.ledger

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatisticsScopeTest {
    private fun row(id: String, event: String?, cents: Long, kind: String = "expense", date: String = "2026-10-01") =
        LedgerRow(LedgerEntry(id, kind, cents * 2, "NOK", cents, if (kind == "income") "income" else "food.lunch", event, date, id),
            "分类", null, event)
    private val rows = listOf(row("home", null, 200000), row("trip", "trip", 800000),
        row("photo", "photo", 50000), row("pay", null, 600000, "income"), row("next", "trip", 10000, date = "2026-11-01"))

    @Test fun sharedScopeMatchesMonthlySummaryTrendAndSharesWithoutMutatingRows() {
        val monthly = rows.filter { it.entry.occurredOn.startsWith("2026-10") }
        assertEquals(monthly, statisticsEventRows(monthly, "all", null))
        val scope = StatisticsScope("exclude", "trip")
        val selected = statisticsEventRows(monthly, scope.mode, scope.eventId)
        assertEquals(IncomeExpense(600000, 250000), incomeExpense(selected))
        assertEquals(350000L, incomeExpense(selected).net)
        assertEquals(250000L, trendBuckets(selected, LocalDate.parse("2026-10-01"), StatPeriod.MONTH).sumOf { it.totals.expense })
        assertEquals(250000L, categoryShares(selected, presetCategories(), "expense").sumOf { it.amount })
        assertEquals(250000L, categoryShares(selected, presetCategories(), "expense", "food").sumOf { it.amount })
        assertEquals(800000L, monthly.single { it.entry.id == "trip" }.entry.rmbMinor)
        assertEquals(IncomeExpense(0, 800000), incomeExpense(statisticsEventRows(monthly, "only", "trip")))
        val nextMonth = rows.filter { it.entry.occurredOn.startsWith("2026-11") }
        assertTrue(statisticsEventRows(nextMonth, "exclude", "trip").isEmpty())
        assertTrue(categoryShares(emptyList(), presetCategories(), "expense").isEmpty())
        assertEquals("0.0%", sharePercent(0, 0))
    }

    @Test fun searchControlsDisplayButScopeControlsAmountsAndRelinkingRefreshes() {
        val scope = StatisticsScope("exclude", "trip")
        val displayed = filterRows(rows, presetCategories(), LedgerFilter(eventId = "trip"))
        assertEquals(2, displayed.size)
        assertTrue(displayed.none(scope::includes))
        assertEquals(0L, expenseTotal(statisticsEventRows(displayed, scope.mode, scope.eventId)))
        val relinked = displayed.first().copy(entry = displayed.first().entry.copy(eventId = null, rmbMinor = 1234))
        assertEquals(1234L, expenseTotal(statisticsEventRows(listOf(relinked), scope.mode, scope.eventId)))
    }

    @Test fun validationWaitsForLoadingAndUsesIdsAcrossRenameAndArchive() {
        val scope = StatisticsScope("exclude", "trip")
        assertEquals(scope, scope.resolved(emptyList(), loading = true))
        assertEquals(StatisticsScope(), scope.resolved(emptyList()))
        val events = listOf(LedgerEvent("trip", "新名称", archived = true), LedgerEvent("other", "新名称"))
        assertEquals(scope, scope.resolved(events))
        assertEquals("排除：新名称", scope.label(events))
        assertTrue(scope.includes(row("other", "other", 10)))
        assertEquals(StatisticsScope(), StatisticsScope("only", null).resolved(events))
    }

    @Test fun publicExpenseOnlyCountsPersonalShareAndNeverTransfers() {
        val members = listOf(EventMember("me", "trip", "我", true, 0), EventMember("you", "trip", "同行", false, 1))
        val bundle = PublicBundle(PublicEvent("trip"), members, listOf(SharedExpense(
            PublicExpense("p", "trip", "me", 1600000, "CNY", 1600000, "food.lunch", "2026-10-01", "", "personal"),
            members.map { ExpenseMember("p", it.id) })), listOf(EventTransfer("t", "trip", "you", "me", 10000, "2026-10-01", "")))
        val personal = bundle.personalEntry(bundle.expenses.single(), "personal")!!
        assertEquals(800000L, personal.rmbMinor)
        val list = listOf(LedgerRow(personal, "午餐", "饮食", "旅行"))
        val balances = bundle.balances()
        assertEquals(0L, expenseTotal(statisticsEventRows(list, "exclude", "trip")))
        assertEquals(800000L, expenseTotal(list))
        assertEquals(balances, bundle.balances())
        assertTrue(EventBill(LedgerEvent("trip", "旅行"), bundle, presetCategories()).text().contains("16000.00"))
    }
}
