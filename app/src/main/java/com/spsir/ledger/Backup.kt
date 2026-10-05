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
    val publicEvents: List<PublicBundle> = emptyList(),
)

object BackupJson {
    const val MAX_BYTES = 20 * 1024 * 1024

    fun exportBytes(data: LedgerBackup): ByteArray {
        val bytes = encode(data).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "备份超过 20 MB，未导出；请保留当前账本并联系开发者" }
        return bytes
    }

    fun read(input: java.io.InputStream): LedgerBackup {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(count <= MAX_BYTES - output.size()) { "备份超过 20 MB，无法恢复" }
            output.write(buffer, 0, count)
        }
        return decode(output.toString(Charsets.UTF_8.name()))
    }

    fun encode(data: LedgerBackup): String = JSONObject().apply {
        put("format", "SPSir"); put("version", 2); put("exportedAt", data.exportedAt)
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
        put("publicEvents", JSONArray().apply { data.publicEvents.forEach { bundle -> put(JSONObject().apply {
            put("eventId", bundle.event.eventId)
            put("members", JSONArray().apply { bundle.members.forEach { m -> put(JSONObject().apply {
                put("id", m.id); put("eventId", m.eventId); put("name", m.name); put("isSelf", m.isSelf); put("position", m.position)
            }) } })
            put("expenses", JSONArray().apply { bundle.expenses.forEach { row -> put(JSONObject().apply {
                val e = row.expense
                put("id", e.id); put("eventId", e.eventId); put("payerId", e.payerId)
                put("originalMinor", e.originalMinor); put("currency", e.currency); put("rmbMinor", e.rmbMinor)
                put("categoryId", e.categoryId); put("occurredOn", e.occurredOn); put("note", e.note); put("entryId", e.entryId ?: JSONObject.NULL)
                put("participants", JSONArray(row.participants.map { it.memberId }))
            }) } })
        }) } })
    }.toString()

    fun decode(text: String): LedgerBackup {
        try {
            val root = JSONObject(text)
            require(root.string("format") == "SPSir" && root.integer("version") in 1L..2L) { "不支持的备份格式或版本" }
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
                if (root.integer("version") == 1L) emptyList() else objects("publicEvents") { b ->
                    val members = b.getJSONArray("members").let { a -> (0 until a.length()).map { i ->
                        val m = a.getJSONObject(i)
                        val position = m.integer("position")
                        require(position in 0..Int.MAX_VALUE.toLong()) { "成员顺序无效" }
                        val self = m.get("isSelf"); require(self is Boolean) { "成员身份无效" }
                        EventMember(m.string("id"), m.string("eventId"), m.string("name"), self, position.toInt())
                    } }
                    val expenses = b.getJSONArray("expenses").let { a -> (0 until a.length()).map { i ->
                        val e = a.getJSONObject(i)
                        val expense = PublicExpense(e.string("id"), e.string("eventId"), e.string("payerId"), e.integer("originalMinor"), e.string("currency"), e.integer("rmbMinor"), e.string("categoryId"), e.string("occurredOn"), e.string("note"), e.optionalId("entryId"))
                        val participants = e.getJSONArray("participants").let { p -> (0 until p.length()).map { j ->
                            val id = p.get(j); require(id is String) { "参与者无效" }; ExpenseMember(expense.id, id)
                        } }
                        SharedExpense(expense, participants)
                    } }
                    PublicBundle(PublicEvent(b.string("eventId")), members, expenses)
                },
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
        val presets = presetCategories().associateBy { it.id }
        data.categories.forEach { c ->
            require(c.name.isNotBlank() && c.kind in listOf("income", "expense")) { "分类名称或类型无效" }
            require(c.kind != "income" || c.id == "income") { "收入分类无效" }
            if (c.parentId != null) require(c.kind == "expense" && categories[c.parentId]?.let { it.kind == "expense" && it.parentId == null } == true) { "分类层级或引用无效" }
            presets[c.id]?.let { preset -> require(c.parentId == preset.parentId && c.kind == preset.kind) { "预设分类结构不兼容" } }
        }
        data.events.forEach { require(it.name.isNotBlank() && it.name.length <= 40) { "事件名称无效" } }
        data.hidden.forEach { require(categories[it.categoryId]?.kind == "expense") { "隐藏分类引用无效" } }
        val derived = validatePublicBackup(data)
        data.entries.forEach { e ->
            val c = categories[e.categoryId]
            require(c != null && c.kind == e.kind && (e.kind == "income" || c.parentId != null)) { "流水分类引用无效" }
            require(e.eventId == null || (e.kind == "expense" && e.eventId in events)) { "流水事件引用无效" }
            val currency = MoneyCurrency.valueOf(e.currency)
            if (e.id !in derived) {
                Money.validateMinor(e.originalMinor, currency.digits)
                Money.validateMinor(e.rmbMinor)
            }
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
        LedgerBackup(Instant.now().toString(), db.dao().allCategories(), db.dao().allEvents(), db.dao().allEntries(), db.dao().allHidden(), db.publicDao().all())
    }

    suspend fun restore(data: LedgerBackup) = withContext(Dispatchers.IO) {
        BackupJson.validate(data)
        db.withTransaction {
            // Keep the pre-restore copy durable before any destructive SQL executes.
            val previous = BackupJson.exportBytes(snapshot())
            val file = AtomicFile(safetyFile)
            val stream = file.startWrite()
            try { stream.write(previous); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
            check(file.openRead().use { it.readBytes() }.contentEquals(previous)) { "覆盖前副本校验失败" }
            db.publicDao().clearParticipants(); db.publicDao().clearExpenses(); db.publicDao().clearMembers(); db.publicDao().clearEvents()
            db.dao().clearEntries(); db.dao().clearEvents(); db.dao().clearHidden(); db.dao().clearCategories()
            db.dao().seed(data.categories)
            db.dao().insertEvents(data.events)
            db.dao().insertEntries(data.entries)
            data.publicEvents.forEach { b ->
                db.publicDao().insertEvent(b.event)
                b.members.forEach { db.publicDao().member(it) }
                b.expenses.forEach { row -> db.publicDao().expense(row.expense); db.publicDao().participants(row.participants) }
            }
            data.hidden.forEach { db.dao().hide(it) }
            // A supported older backup may omit newly introduced presets.
            db.dao().seed(presetCategories().filter { p -> data.categories.none { it.id == p.id } })
        }
    }

    fun readSafety(): LedgerBackup {
        require(safetyFile.exists() || File(safetyFile.path + ".bak").exists()) { "尚无覆盖前副本；首次成功恢复前不会生成副本" }
        return AtomicFile(safetyFile).openRead().use { BackupJson.read(it) }
    }
}

fun validatePublicBackup(data: LedgerBackup): Set<String> {
    val eventIds = mutableSetOf<String>()
    val memberIds = mutableSetOf<String>()
    val expenseIds = mutableSetOf<String>()
    val entryIds = mutableSetOf<String>()
    val entries = data.entries.associateBy { it.id }
    data.publicEvents.forEach { bundle ->
        val event = bundle.event.eventId
        require(eventIds.add(event) && data.events.any { it.id == event }) { "公共事件引用无效" }
        require(bundle.members.count { it.isSelf } == 1) { "公共事件必须有一个本人" }
        val names = mutableSetOf<String>(); val positions = mutableSetOf<Int>()
        bundle.members.forEach { m ->
            require(m.id.isNotBlank() && memberIds.add(m.id) && m.eventId == event) { "成员引用无效" }
            require(m.name == m.name.trim() && m.name.length in 1..20 && names.add(m.name)) { "成员昵称无效" }
            require(m.isSelf == (m.name == "我") && m.position >= 0 && positions.add(m.position)) { "成员身份或顺序无效" }
        }
        bundle.expenses.forEach { row ->
            val e = row.expense
            require(e.id.isNotBlank() && expenseIds.add(e.id) && e.eventId == event) { "公共开支引用无效" }
            require(bundle.members.any { it.id == e.payerId }) { "付款人不属于本事件" }
            require(row.participants.isNotEmpty() && row.participants.map { it.memberId }.distinct().size == row.participants.size && row.participants.all { p -> p.expenseId == e.id && bundle.members.any { it.id == p.memberId } }) { "参与者无效" }
            val currency = MoneyCurrency.valueOf(e.currency)
            Money.validateMinor(e.originalMinor, currency.digits); Money.validateMinor(e.rmbMinor)
            require(currency != MoneyCurrency.CNY || e.originalMinor == e.rmbMinor) { "公共开支人民币金额不一致" }
            require(data.categories.any { it.id == e.categoryId && it.kind == "expense" && it.parentId != null }) { "公共开支分类无效" }
            Money.date(e.occurredOn); require(e.note.length <= 500) { "备注超过 500 字" }
            val expected = bundle.personalEntry(row, e.entryId ?: "missing")
            if (expected == null) require(e.entryId == null) { "零份额不应关联流水" }
            else require(e.entryId != null && entryIds.add(e.entryId) && entries[e.entryId] == expected) { "公共开支与个人流水不一致" }
        }
        bundle.balances() // Checked arithmetic also validates aggregate overflow.
    }
    require(data.entries.none { it.eventId in eventIds && it.id !in entryIds }) { "公共事件存在未关联流水" }
    return entryIds
}
