package com.spsir.ledger

import java.time.OffsetDateTime

// Capture this immutable object once when opening the preview, including its timestamp.
data class EventBill(val event: LedgerEvent, val bundle: PublicBundle, val categories: List<Category>,
    val generatedAt: String = OffsetDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")))

fun EventBill.text(details: Boolean = false, notes: Boolean = false): String = buildString {
    val people = bundle.members.associateBy { it.id }
    val categoriesById = categories.associateBy { it.id }
    val balances = bundle.balances()
    appendLine("SPSir · ${event.name}")
    appendLine("生成时间：$generatedAt")
    appendLine("事件总支出：¥ ${Money.format(balances.fold(0L) { sum, b -> Math.addExact(sum, b.paid) })}")
    appendLine(bundle.settlementStatus())
    appendLine()
    balances.forEach { b ->
        appendLine(b.member.name)
        appendLine("应承担 ¥ ${Money.format(b.share)} · 费用实付 ¥ ${Money.format(b.paid)}")
        appendLine("已转出 ¥ ${Money.format(b.sent)} · 已收到 ¥ ${Money.format(b.received)}")
        appendLine(if (b.balance == 0L) "无需转账" else "剩余${if (b.balance > 0) "应收" else "应付"} ¥ ${Money.format(kotlin.math.abs(b.balance))}")
    }
    appendLine("\n转账建议")
    val suggestions = settlements(balances)
    if (suggestions.isEmpty()) appendLine("无需转账")
    suggestions.forEach { t -> appendLine("${people.getValue(t.from).name} → ${people.getValue(t.to).name}：¥ ${Money.format(t.amount)}") }
    if (details) {
        appendLine("\n费用明细")
        bundle.expenses.sortedWith(compareBy<SharedExpense> { it.expense.occurredOn }.thenBy { it.expense.id }).forEach { row ->
            val e = row.expense
            appendLine("${e.occurredOn} · ${categoriesById.getValue(e.categoryId).name} · ¥ ${Money.format(e.rmbMinor)}")
            if (e.currency != "CNY") appendLine("原币 ${e.currency} ${Money.format(e.originalMinor, MoneyCurrency.valueOf(e.currency).digits)}")
            appendLine("付款人：${people.getValue(e.payerId).name}；参与者：${bundle.members.sortedBy { it.position }.filter { m -> row.participants.any { it.memberId == m.id } }.joinToString("、") { it.name }}")
            if (notes && e.note.isNotBlank()) appendLine("备注：${e.note}")
        }
        appendLine("\n实际转账")
        bundle.transfers.sortedWith(compareBy<EventTransfer> { it.occurredOn }.thenBy { it.id }).forEach { t ->
            appendLine("${t.occurredOn} · ${people.getValue(t.fromId).name} → ${people.getValue(t.toId).name}：¥ ${Money.format(t.amountMinor)}")
            if (notes && t.note.isNotBlank()) appendLine("备注：${t.note}")
        }
    }
    appendLine("\n文字账单仅用于核对，不能恢复账本。")
}
