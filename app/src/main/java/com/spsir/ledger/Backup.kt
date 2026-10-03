package com.spsir.ledger

import android.util.AtomicFile
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

data class LedgerBackup(
    val exportedAt: String,
    val categories: List<Category>,
    val events: List<LedgerEvent>,
    val entries: List<LedgerEntry>,
    val hidden: List<HiddenCategory>,
)

object BackupJson {
    fun encode(data: LedgerBackup): String = JSONObject().apply {
        put("format", "SPSir"); put("version", 1); put("exportedAt", data.exportedAt)
        put("categories", JSONArray().apply { data.categories.forEach { c -> put(JSONObject().apply {
            put("id", c.id); put("name", c.name); put("parentId", c.parentId ?: JSONObject.NULL)
            put("kind", c.kind); put("sortOrder", c.sortOrder)
        }) } })
        put("events", JSONArray().apply { data.events.forEach { e -> put(JSONObject().put("id", e.id).put("name", e.name)) } })
        put("entries", JSONArray().apply { data.entries.forEach { e -> put(JSONObject().apply {
            put("id", e.id); put("kind", e.kind); put("originalMinor", e.originalMinor); put("currency", e.currency)
            put("rmbMinor", e.rmbMinor); put("categoryId", e.categoryId); put("eventId", e.eventId ?: JSONObject.NULL)
            put("occurredOn", e.occurredOn); put("note", e.note)
        }) } })
        put("hiddenCategories", JSONArray(data.hidden.map { it.categoryId }))
    }.toString(2)

    fun decode(text: String): LedgerBackup {
        try {
            val root = JSONObject(text)
            require(root.string("format") == "SPSir" && root.integer("version") == 1L) { "不支持的备份格式或版本" }
            fun <T> objects(key: String, read: (JSONObject) -> T): List<T> {
                val array = root.getJSONArray(key)
                return (0 until array.length()).map { read(array.getJSONObject(it)) }
            }
            val data = LedgerBackup(root.string("exportedAt"),
                objects("categories") { c ->
                    val order = c.integer("sortOrder")
                    require(order in Int.MIN_VALUE..Int.MAX_VALUE) { "分类排序无效" }
                    Category(c.string("id"), c.string("name"), c.optionalId("parentId"), c.string("kind"), order.toInt())
                },
                objects("events") { LedgerEvent(it.string("id"), it.string("name")) },
                objects("entries") { e -> LedgerEntry(e.string("id"), e.string("kind"), e.integer("originalMinor"),
                    e.string("currency"), e.integer("rmbMinor"), e.string("categoryId"), e.optionalId("eventId"), e.string("occurredOn"), e.string("note")) },
                root.getJSONArray("hiddenCategories").let { a -> (0 until a.length()).map {
                    val id = a.get(it); require(id is String) { "隐藏分类无效" }; HiddenCategory(id)
                } },
            )
            validate(data)
            return data
        } catch (e: IllegalArgumentException) { throw e }
        catch (_: Exception) { throw IllegalArgumentException("备份文件损坏或缺少必要字段") }
    }

    fun validate(data: LedgerBackup) {
        Instant.parse(data.exportedAt)
        fun unique(ids: List<String>) { require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) { "备份存在空 ID 或重复 ID" } }
        unique(data.categories.map { it.id }); unique(data.events.map { it.id }); unique(data.entries.map { it.id }); unique(data.hidden.map { it.categoryId })
        val categories = data.categories.associateBy { it.id }
        val events = data.events.map { it.id }.toSet()
        require(categories["income"]?.let { it.kind == "income" && it.parentId == null } == true) { "缺少通用收入分类" }
        data.categories.forEach { c ->
            require(c.name.isNotBlank() && c.kind in listOf("income", "expense")) { "分类名称或类型无效" }
            require(c.kind != "income" || c.id == "income") { "收入分类无效" }
            if (c.parentId != null) require(c.kind == "expense" && categories[c.parentId]?.let { it.kind == "expense" && it.parentId == null } == true) { "分类层级或引用无效" }
            presetCategories().find { it.id == c.id }?.let { preset -> require(c.parentId == preset.parentId && c.kind == preset.kind) { "预设分类结构不兼容" } }
        }
        data.events.forEach { require(it.name.isNotBlank() && it.name.length <= 40) { "事件名称无效" } }
        data.hidden.forEach { require(categories[it.categoryId]?.kind == "expense") { "隐藏分类引用无效" } }
        data.entries.forEach { e ->
            val c = categories[e.categoryId]
            require(c != null && c.kind == e.kind && (e.kind == "income" || c.parentId != null)) { "流水分类引用无效" }
            require(e.eventId == null || (e.kind == "expense" && e.eventId in events)) { "流水事件引用无效" }
            val currency = MoneyCurrency.valueOf(e.currency)
            Money.parse(Money.format(e.originalMinor, currency.digits), currency.digits)
            Money.parse(Money.format(e.rmbMinor))
            require(currency != MoneyCurrency.CNY || e.originalMinor == e.rmbMinor) { "人民币金额不一致" }
            Money.date(e.occurredOn)
            require(e.note.length <= 500) { "备注超过 500 字" }
        }
    }

    private fun JSONObject.string(key: String): String {
        val value = get(key); require(value is String) { "字段 $key 必须为文本" }; return value
    }
    private fun JSONObject.optionalId(key: String): String? {
        require(has(key)) { "缺少字段 $key" }; return if (isNull(key)) null else string(key)
    }
    private fun JSONObject.integer(key: String): Long {
        val value = get(key); require(value is Int || value is Long) { "字段 $key 必须为整数" }; return (value as Number).toLong()
    }
}

class BackupStore(private val db: LedgerDatabase, private val safetyFile: File) {
    suspend fun snapshot(): LedgerBackup = db.withTransaction {
        LedgerBackup(Instant.now().toString(), db.dao().allCategories(), db.dao().allEvents(), db.dao().allEntries(), db.dao().allHidden())
    }

    suspend fun restore(data: LedgerBackup) = withContext(Dispatchers.IO) {
        BackupJson.validate(data)
        db.withTransaction {
            // Keep the pre-restore copy durable before any destructive SQL executes.
            val previous = BackupJson.encode(snapshot()).toByteArray(Charsets.UTF_8)
            val file = AtomicFile(safetyFile)
            val stream = file.startWrite()
            try { stream.write(previous); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
            check(file.openRead().use { it.readBytes() }.contentEquals(previous)) { "覆盖前副本校验失败" }
            db.dao().clearEntries(); db.dao().clearEvents(); db.dao().clearHidden(); db.dao().clearCategories()
            db.dao().seed(data.categories)
            db.dao().insertEvents(data.events)
            db.dao().insertEntries(data.entries)
            data.hidden.forEach { db.dao().hide(it) }
            // A supported older backup may omit newly introduced presets.
            db.dao().seed(presetCategories().filter { p -> data.categories.none { it.id == p.id } })
        }
    }

    fun readSafety(): LedgerBackup {
        require(safetyFile.exists() || File(safetyFile.path + ".bak").exists()) { "尚无覆盖前副本；首次成功恢复前不会生成副本" }
        return BackupJson.decode(AtomicFile(safetyFile).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
}
