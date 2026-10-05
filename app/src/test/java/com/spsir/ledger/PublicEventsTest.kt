package com.spsir.ledger

import org.junit.Assert.*
import org.junit.Test

class PublicEventsTest {
    private val members = listOf(EventMember("me", "trip", "我", true, 0), EventMember("a", "trip", "甲", false, 1), EventMember("b", "trip", "乙", false, 2))
    private fun row(id: String, amount: Long, payer: String, vararg people: String) = SharedExpense(
        PublicExpense(id, "trip", payer, amount, "CNY", amount, "food.lunch", "2026-10-04", ""), people.map { ExpenseMember(id, it) })
    @Test fun completeTripProducesPersonalConsumptionAndNetTransfers() {
        val b = PublicBundle(PublicEvent("trip"), members, listOf(row("stay",120000,"me","me","a","b"), row("dinner",30000,"a","me","a"), row("taxi",9000,"b","a","b"), row("film",18000,"me","me")))
        val balances = b.balances()
        assertEquals(listOf(73000L,59500L,44500L), balances.map { it.share })
        assertEquals(listOf(138000L,30000L,9000L), balances.map { it.paid })
        assertEquals(listOf(Settlement("b","me",35500), Settlement("a","me",29500)), settlements(balances))
        assertNull(b.personalEntry(b.expenses[2], "derived"))
    }
    @Test fun remainderUsesStableOrderAndSupportsZeroShares() {
        assertEquals(mapOf("me" to 3334L,"a" to 3333L,"b" to 3333L), splitAmount(10000,members.reversed()))
        assertEquals(mapOf("me" to 1L,"a" to 0L,"b" to 0L), splitAmount(1,members))
        assertEquals(10000L, splitAmount(10000,members.map { it.copy(name="changed") }).values.sum())
    }
    @Test fun multipleCreditorsDebtorsSettleExactly() {
        val people = members + EventMember("c","trip","丙",false,3)
        val balances = listOf(MemberBalance(people[0],100,0),MemberBalance(people[1],70,0),MemberBalance(people[2],0,80),MemberBalance(people[3],0,90))
        val remaining = balances.associate { it.member.id to it.balance }.toMutableMap()
        settlements(balances).forEach { t -> assertTrue(t.amount>0); remaining[t.from]=remaining.getValue(t.from)+t.amount; remaining[t.to]=remaining.getValue(t.to)-t.amount }
        assertTrue(remaining.values.all { it==0L })
        assertTrue(settlements(members.map { MemberBalance(it,10,10) }).isEmpty())
    }
    @Test fun foreignZeroDecimalAndSelfNotPayer() {
        val b = PublicBundle(PublicEvent("trip"),members,listOf(row("tiny",1,"a","me","a","b").let { it.copy(expense=it.expense.copy(currency="JPY",rmbMinor=5)) }))
        val e=b.personalEntry(b.expenses.single(),"e")!!
        assertEquals(1L,e.originalMinor); assertEquals(2L,e.rmbMinor)
        assertEquals(5L,b.balances().sumOf { it.share })
        assertEquals(2L,b.balances().first().share)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyParticipantsAreRejected() { splitAmount(100, emptyList()) }
    @Test(expected = ArithmeticException::class) fun aggregateOverflowIsRejected() {
        PublicBundle(PublicEvent("trip"),members,listOf(row("a",Long.MAX_VALUE,"me","me"),row("b",1,"me","me"))).balances()
    }
}
