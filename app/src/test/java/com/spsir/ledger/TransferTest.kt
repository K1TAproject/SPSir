package com.spsir.ledger

import org.junit.Assert.*
import org.junit.Test

class TransferTest {
    private val members = listOf(EventMember("me", "trip", "我", true, 0), EventMember("a", "trip", "甲", false, 1))
    private fun bundle() = PublicBundle(PublicEvent("trip"), members, listOf(SharedExpense(
        PublicExpense("meal", "trip", "me", 60000, "CNY", 60000, "food.lunch", "2026-10-05", "私人备注", "entry"),
        members.map { ExpenseMember("meal", it.id) })))
    private fun transfer(id: String, amount: Long, from: String = "a", to: String = "me") = EventTransfer(id, "trip", from, to, amount, "2026-10-05", "转账私密备注")

    @Test fun partialMultipleAndExcessTransfersKeepConsumptionUnchanged() {
        val original = bundle()
        val partial = original.copy(transfers = listOf(transfer("t", 10000)))
        assertEquals(listOf(20000L, -20000L), partial.balances().map { it.balance })
        assertEquals(60000L, partial.balances().sumOf { it.paid })
        assertEquals(60000L, partial.balances().sumOf { it.share })
        assertEquals(original.personalEntry(original.expenses.single(), "entry"), partial.personalEntry(partial.expenses.single(), "entry"))
        val done = partial.copy(transfers = partial.transfers + transfer("t2", 20000))
        assertEquals("已结清", done.settlementStatus()); assertTrue(settlements(done.balances()).isEmpty())
        val excess = original.copy(transfers = listOf(transfer("t", 45000)))
        assertEquals(listOf(Settlement("me", "a", 15000)), settlements(excess.balances()))
        assertEquals(30000L, excess.transferLimit("t", "a", "me"))
        assertEquals("暂无待结算费用", original.copy(expenses = emptyList()).settlementStatus())
        assertEquals("待结算", original.copy(expenses = emptyList(), transfers = listOf(transfer("t", 100))).settlementStatus())
    }

    @Test fun indirectTransfersAndExpenseChangesRecomputeBalances() {
        val people = members + EventMember("b", "trip", "乙", false, 2)
        val row = bundle().expenses.single().let { it.copy(expense = it.expense.copy(originalMinor = 90000, rmbMinor = 90000), participants = people.map { ExpenseMember("meal", it.id) }) }
        val b = PublicBundle(PublicEvent("trip"), people, listOf(row), listOf(transfer("1", 10000, "a", "b"), transfer("2", 40000, "b"), transfer("3", 20000)))
        assertTrue(b.balances().all { it.balance == 0L })
        assertEquals("已结清", b.settlementStatus())
        assertEquals(30000L, b.copy(transfers = emptyList()).balances().single { it.member.id == "a" }.share)
        val changed = b.copy(expenses = listOf(row.copy(expense = row.expense.copy(originalMinor = 120000, rmbMinor = 120000))))
        assertEquals(listOf(20000L, -10000L, -10000L), changed.balances().map { it.balance })
    }

    @Test fun billDefaultsProtectNotesAndPreserveForeignDetails() {
        val b = bundle().let { it.copy(expenses = listOf(it.expenses.single().let { row -> row.copy(expense = row.expense.copy(currency = "NOK", originalMinor = 90000)) }), transfers = listOf(transfer("t", 10000))) }
        val bill = EventBill(LedgerEvent("trip", "旅行"), b, presetCategories(), "2026-10-05T12:00:00+08:00")
        val summary = bill.text()
        assertTrue(summary.contains("应收 ¥ 200.00")); assertTrue(summary.contains("费用实付 ¥ 600.00"))
        assertFalse(summary.contains("私人备注")); assertFalse(summary.contains("转账私密备注"))
        assertFalse(bill.text(details = true).contains("私人备注"))
        val full = bill.text(details = true, notes = true)
        assertTrue(full.contains("原币 NOK 900.00")); assertTrue(full.contains("付款人：我；参与者：我、甲"))
        assertTrue(full.contains("私人备注")); assertTrue(full.contains("转账私密备注"))
        assertTrue(summary.contains("不能恢复账本"))
        assertEquals(summary, bill.text())
    }

    @Test fun invalidTransfersAreRejected() {
        for (t in listOf(transfer("t", 0), transfer("t", -1), transfer("t", 100, "a", "a"), transfer("t", 100, "unknown"), transfer("t", 100).copy(eventId = "elsewhere"), transfer("t", 100).copy(occurredOn = "2026-02-30"))) {
            try { validateTransfer(t, bundle()); fail("Invalid transfer accepted") } catch (_: IllegalArgumentException) {}
        }
    }
}
