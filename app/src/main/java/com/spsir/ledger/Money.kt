package com.spsir.ledger

import java.math.BigDecimal
import java.time.LocalDate

enum class MoneyCurrency(val label: String, val digits: Int = 2) {
    CNY("人民币"), EUR("欧元"), NOK("挪威克朗"), SEK("瑞典克朗"),
    DKK("丹麦克朗"), ISK("冰岛克朗", 0), USD("美元"), GBP("英镑"), JPY("日元", 0)
}

object Money {
    fun parse(text: String, digits: Int = 2): Long {
        val value = text.trim()
        require(value.matches(Regex("[0-9]+(\\.[0-9]+)?"))) { "请输入正确的正数金额" }
        val decimal = BigDecimal(value)
        require(decimal.scale() <= digits) { "此币种最多填写 $digits 位小数" }
        require(decimal > BigDecimal.ZERO && decimal <= BigDecimal("999999999")) {
            "金额须大于 0，且不超过 999999999"
        }
        return decimal.movePointRight(digits).longValueExact()
    }

    fun format(minor: Long, digits: Int = 2): String =
        BigDecimal.valueOf(minor, digits).toPlainString()

    fun date(text: String): String {
        require(text.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "日期请填写 YYYY-MM-DD" }
        val date = runCatching { LocalDate.parse(text) }.getOrNull()
        require(date != null) { "日期不存在，请检查年月日" }
        return date.toString()
    }
}
