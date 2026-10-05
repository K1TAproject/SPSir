package com.spsir.ledger

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun decimalAmountsAreExact() {
        assertEquals(30L, Money.parse("0.10") + Money.parse("0.20"))
        assertEquals("12.34", Money.format(Money.parse("12.34")))
        assertEquals(120L, Money.parse("120", 0))
    }

    @Test fun rejectsInvalidOrUnrepresentableAmounts() {
        listOf("", "0", "-1", "NaN", "1e3", "0.001", "1000000000", "1,000").forEach { value ->
            assertThrows("Should reject $value", IllegalArgumentException::class.java) { Money.parse(value) }
        }
        assertThrows(IllegalArgumentException::class.java) { Money.parse("1.5", 0) }
    }

    @Test fun rejectsInvalidDatesInsteadOfMovingThemToAnotherMonth() {
        assertEquals("2024-02-29", Money.date("2024-02-29"))
        listOf("2025-02-29", "2026-13-01", "2026-2-1").forEach { date ->
            assertThrows(IllegalArgumentException::class.java) { Money.date(date) }
        }
    }

    @Test fun eventAndGlobalViewsReferToTheSameExpense() {
        val row = LedgerRow(LedgerEntry("one", "expense", 12000, "NOK", 8100,
            "food.lunch", "trip", "2026-10-02", ""), "午餐", "餐饮", "北欧旅行")
        val income = row.copy(entry = row.entry.copy(id = "income", kind = "income", rmbMinor = 10000, eventId = null))
        val all = listOf(row, income)
        assertEquals(8100L, expenseTotal(all))
        assertEquals(8100L, expenseTotal(all.filter { it.entry.eventId == "trip" }))
    }
    @org.junit.Test fun storedMoneyChecksMatchInputBounds() {
        Money.validateMinor(99999999900L)
        Money.validateMinor(999999999L, 0)
        for ((value, digits) in listOf(0L to 2, -1L to 2, 100000000000L to 2, 1000000000L to 0)) {
            try { Money.validateMinor(value, digits); org.junit.Assert.fail("Invalid amount accepted") }
            catch (_: IllegalArgumentException) {}
        }
    }
}
