package com.spsir.ledger

import android.content.SharedPreferences
import androidx.core.content.edit

data class StatisticsScope(val mode: String = "all", val eventId: String? = null) {
    fun includes(row: LedgerRow): Boolean = when {
        eventId == null -> true
        mode == "only" -> row.entry.eventId == eventId
        mode == "exclude" -> row.entry.eventId != eventId
        else -> true
    }

    fun resolved(events: List<LedgerEvent>, loading: Boolean = false): StatisticsScope = when {
        mode !in listOf("only", "exclude") || eventId == null -> StatisticsScope()
        loading -> this
        events.none { it.id == eventId } -> StatisticsScope()
        else -> this
    }

    fun label(events: List<LedgerEvent>): String {
        if (mode == "all") return "全部"
        val name = events.find { it.id == eventId }?.name ?: "加载中…"
        return "${if (mode == "only") "仅含" else "排除"}：$name"
    }
}

class StatisticsScopePreferences(private val preferences: SharedPreferences) {
    fun read() = StatisticsScope(preferences.getString("mode", "all") ?: "all", preferences.getString("eventId", null))
        .resolved(emptyList(), loading = true)

    fun write(scope: StatisticsScope) {
        preferences.edit { putString("mode", scope.mode); putString("eventId", scope.eventId) }
    }

    suspend fun restore(backups: BackupStore, data: LedgerBackup) {
        backups.restore(data)
        write(StatisticsScope())
    }
}
