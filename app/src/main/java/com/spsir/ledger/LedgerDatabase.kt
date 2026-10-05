package com.spsir.ledger

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String? = null,
    val kind: String = "expense",
    val sortOrder: Int = 0,
)

@Entity(tableName = "events")
data class LedgerEvent(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(tableName = "hidden_categories")
data class HiddenCategory(@PrimaryKey val categoryId: String)

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = LedgerEvent::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("categoryId"), Index("eventId"), Index("occurredOn")],
)
data class LedgerEntry(
    @PrimaryKey val id: String,
    val kind: String,
    val originalMinor: Long,
    val currency: String,
    val rmbMinor: Long,
    val categoryId: String,
    val eventId: String?,
    val occurredOn: String,
    val note: String,
)

data class LedgerRow(
    @Embedded val entry: LedgerEntry,
    val categoryName: String,
    val groupName: String?,
    val eventName: String?,
)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM hidden_categories ORDER BY categoryId")
    fun hiddenCategories(): Flow<List<HiddenCategory>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hide(category: HiddenCategory)

    @Query("DELETE FROM hidden_categories WHERE categoryId = :id")
    suspend fun showCategory(id: String)

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    suspend fun allCategories(): List<Category>

    @Query("SELECT * FROM events ORDER BY rowid")
    suspend fun allEvents(): List<LedgerEvent>

    @Query("SELECT * FROM entries ORDER BY rowid")
    suspend fun allEntries(): List<LedgerEntry>

    @Query("SELECT * FROM hidden_categories ORDER BY categoryId")
    suspend fun allHidden(): List<HiddenCategory>

    @Query("DELETE FROM entries")
    suspend fun clearEntries()

    @Query("DELETE FROM events")
    suspend fun clearEvents()

    @Query("DELETE FROM hidden_categories")
    suspend fun clearHidden()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Insert
    suspend fun insertEntries(entries: List<LedgerEntry>)

    @Insert
    suspend fun insertEvents(events: List<LedgerEvent>)
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM events ORDER BY rowid DESC")
    fun events(): Flow<List<LedgerEvent>>

    @Query("""
        SELECT entries.*, c.name AS categoryName, p.name AS groupName, e.name AS eventName
        FROM entries JOIN categories c ON entries.categoryId = c.id
        LEFT JOIN categories p ON c.parentId = p.id
        LEFT JOIN events e ON entries.eventId = e.id
        ORDER BY occurredOn DESC, entries.rowid DESC
    """)
    fun rows(): Flow<List<LedgerRow>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun category(id: String): Category?

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun event(id: String): LedgerEvent?

    @Query("SELECT COUNT(*) FROM public_events WHERE eventId = :id")
    suspend fun isPublic(id: String): Int

    @Query("SELECT COUNT(*) FROM public_expenses WHERE entryId = :id")
    suspend fun isDerived(id: String): Int

    // Update preset labels in place; unlike REPLACE this preserves referenced rows.
    @Upsert
    suspend fun seed(categories: List<Category>)

    @Insert
    suspend fun insertEvent(event: LedgerEvent)

    @Upsert
    suspend fun save(entry: LedgerEntry)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: String)
}

@Database(entities = [Category::class, LedgerEvent::class, LedgerEntry::class, HiddenCategory::class, PublicEvent::class, EventMember::class, PublicExpense::class, ExpenseMember::class], version = 3, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao
    abstract fun publicDao(): PublicDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS hidden_categories (categoryId TEXT NOT NULL PRIMARY KEY)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                publicSchema.forEach { db.execSQL(it) }
            }
        }
        fun open(context: Context, name: String = "ledger.db"): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}

fun presetCategories(): List<Category> = buildList {
    val groups = listOf(
        Triple("food", "饮食", listOf(
            "breakfast" to "早餐", "lunch" to "午餐", "dinner" to "晚餐", "late" to "宵夜",
            "coffee" to "咖啡", "tea" to "奶茶／茶饮", "drinks" to "饮料", "water" to "饮用水",
            "snack" to "零食", "fruit" to "水果", "dessert" to "甜品烘焙", "alcohol" to "酒水",
            "groceries" to "食材调料", "other" to "其他饮食",
        )),
        // The existing daily.* IDs keep all previous shopping records intact.
        Triple("daily", "购物", listOf(
            "supplies" to "日用品", "stationery" to "文具", "tools" to "工具", "books" to "书籍",
            "clothing" to "衣物鞋包", "digital" to "数码产品", "accessories" to "数码配件",
            "home" to "家居用品", "appliances" to "家用电器", "sports" to "运动户外用品",
            "collectibles" to "玩具收藏", "other" to "其他购物",
        )),
        Triple("routine", "日常开支", listOf(
            "bath" to "洗澡", "phone" to "手机话费", "data" to "流量包", "internet" to "宽带网络",
            "delivery" to "快递邮寄", "laundry" to "洗衣烘干", "haircut" to "理发",
            "printing" to "打印复印", "subscription" to "软件订阅", "media" to "影音会员",
            "cloud" to "网盘云存储", "repairs" to "日常维修", "fees" to "手续费", "other" to "其他日常开支",
        )),
        Triple("transport", "交通", listOf(
            "subway" to "地铁", "bus" to "公车", "taxi" to "打车", "rail" to "火车", "flight" to "飞机",
            "coach" to "长途汽车", "bike" to "共享单车", "ferry" to "轮船渡轮", "rental" to "租车",
            "fuel" to "燃油", "charging" to "车辆充电", "parking" to "停车费", "toll" to "路桥费", "other" to "其他交通",
        )),
        Triple("photo", "摄影", listOf(
            "film" to "胶卷购买", "processing" to "冲洗", "scanning" to "扫描", "develop" to "冲洗扫描（套餐）",
            "gear" to "器材购买", "accessories" to "摄影配件", "care" to "保养维修", "rental" to "器材租赁",
            "printing" to "照片打印／装裱", "venue" to "拍摄场地／许可", "course" to "摄影课程", "other" to "其他摄影",
        )),
        Triple("stay", "住宿", listOf("hotel" to "酒店民宿", "hostel" to "青年旅舍", "camping" to "露营营地", "other" to "其他住宿")),
        Triple("housing", "居住", listOf(
            "rent" to "房租", "water" to "水费", "electricity" to "电费", "gas" to "燃气费",
            "heating" to "取暖费", "property" to "物业费", "cleaning" to "家政保洁", "repair" to "房屋维修", "other" to "其他居住",
        )),
        Triple("health", "医疗健康", listOf(
            "clinic" to "挂号诊疗", "medicine" to "药品", "checkup" to "体检", "dental" to "牙科",
            "glasses" to "眼镜配镜", "vaccination" to "疫苗", "therapy" to "康复理疗", "other" to "其他医疗健康",
        )),
        Triple("learning", "学习", listOf(
            "tuition" to "学费", "course" to "课程培训", "exam" to "考试报名", "materials" to "学习资料", "other" to "其他学习",
        )),
        Triple("leisure", "休闲运动", listOf(
            "cinema" to "电影", "shows" to "演出演唱会", "games" to "游戏", "tickets" to "景点展览门票",
            "fitness" to "健身", "sport" to "运动场地", "entertainment" to "游乐桌游", "other" to "其他休闲运动",
        )),
        Triple("travel", "旅行服务", listOf(
            "documents" to "护照签证", "insurance" to "旅行保险", "luggage" to "行李托运／寄存",
            "guide" to "导游讲解", "other" to "其他旅行服务",
        )),
        Triple("social", "人情往来", listOf("gifts" to "礼物", "redpacket" to "红包礼金", "donation" to "捐赠", "other" to "其他人情往来")),
        Triple("other", "其他", listOf("other" to "其他支出")),
    )
    groups.forEachIndexed { groupIndex, (id, name, children) ->
        add(Category(id, name, sortOrder = groupIndex * 100))
        children.forEachIndexed { index, (childId, childName) ->
            add(Category("$id.$childId", childName, id, sortOrder = groupIndex * 100 + index + 1))
        }
    }
    // Preserve ambiguous historical categories without guessing how to split old entries.
    add(Category("food.snacks", "零食水果（原分类）", "food", sortOrder = 98))
    add(Category("transport.transit", "公交地铁（原分类）", "transport", sortOrder = 398))
    add(Category("income", "收入", kind = "income", sortOrder = 2000))
}

val legacyCategoryIds = setOf("food.snacks", "transport.transit")
