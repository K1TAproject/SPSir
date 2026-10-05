package com.spsir.ledger

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PublicUiTest {
    @get:Rule val compose = createComposeRule()
    private val members=listOf(EventMember("me","trip","我",true,0),EventMember("a","trip","甲",false,1),EventMember("b","trip","乙",false,2))
    private fun bundle(): PublicBundle {
        fun row(id:String,amount:Long,payer:String,category:String,vararg participants:String):SharedExpense = SharedExpense(
            PublicExpense(id,"trip",payer,amount,"CNY",amount,category,LocalDate.now().toString(),"",if("me" in participants) "entry-$id" else null),participants.map { ExpenseMember(id,it) })
        return PublicBundle(PublicEvent("trip"),members,listOf(row("stay",120000,"me","stay.hotel","me","a","b"),row("meal",30000,"a","food.dinner","me","a"),row("taxi",9000,"b","transport.taxi","a","b"),row("film",18000,"me","photo.film","me")))
    }
    private fun state():LedgerState {
        val b=bundle();val categories=presetCategories()
        val rows=b.expenses.mapNotNull { r -> b.personalEntry(r,r.expense.entryId ?: "unused")?.let { e -> val c=categories.single { it.id==e.categoryId }; LedgerRow(e,c.name,categories.find { it.id==c.parentId }?.name,"北欧旅行") } }
        return LedgerState(loading=false,categories=categories,events=listOf(LedgerEvent("trip","北欧旅行")),rows=rows,publicEvents=listOf(b))
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val i=InstrumentationRegistry.getInstrumentation();val dir=File(i.targetContext.getExternalFilesDir(null),"v04");dir.mkdirs()
        val bitmap=requireNotNull(i.uiAutomation.takeScreenshot())
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @Test fun publicCreationCollectsNamedParticipants() {
        var names:List<String>?=null
        compose.setContent { LedgerTheme { LedgerScreen(LedgerState(loading=false,categories=presetCategories()), createPublic={ _,people,done -> names=people;done() }) } }
        compose.onNodeWithText("事件",useUnmergedTree=true).performClick()
        compose.onNodeWithText("＋ 新建事件").performClick()
        compose.onNodeWithText("公共事件").performClick()
        compose.onNodeWithText("同行者昵称").performTextInput("甲")
        compose.onNodeWithText("添加同行者").performClick()
        compose.onNodeWithText("事件名称").performTextInput("旅行")
        capture("01-create")
        compose.onNodeWithText("创建",substring=false).performClick()
        compose.runOnIdle { assertEquals(listOf("甲"),names) }
    }
    @Test fun eventSettlementAndLinkedLedgerUseTheSameExpenseEditor() {
        var deleted:String?=null
        compose.setContent { LedgerTheme { LedgerScreen(state(),deletePublic={ _,id,done -> deleted=id;done() }) } }
        compose.onNodeWithText("事件",useUnmergedTree=true).performClick()
        compose.onNodeWithText("北欧旅行").performClick()
        compose.onNodeWithText("我的支出 ¥ 730.00").assertIsDisplayed()
        capture("02-expenses")
        compose.onNodeWithText("结算",substring=false).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("甲 → 我  ¥ 295.00"))
        compose.onNodeWithText("甲 → 我  ¥ 295.00").assertIsDisplayed()
        capture("03-settlement")
        compose.onNodeWithText("流水",useUnmergedTree=true).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("酒店民宿"))
        compose.onNodeWithText("酒店民宿",substring=false).performClick()
        compose.onNodeWithText("编辑公共开支").assertIsDisplayed()
        compose.onNodeWithText("项目总额").assertTextContains("1200.00")
        capture("04-editor")
        compose.onNodeWithText("删除公共开支").performScrollTo().performClick()
        compose.onNodeWithText("将同步更新所有人的分摊。").assertIsDisplayed()
        compose.onNodeWithText("确认删除").performClick()
        compose.runOnIdle { assertEquals("stay",deleted) }
    }
    @Test fun selectingPublicEventCarriesAmountAndSelfCanBeExcluded() {
        var saved:PublicDraft?=null
        compose.setContent { LedgerTheme { LedgerScreen(state(),savePublic={ draft,done -> saved=draft;done() }) } }
        compose.onNodeWithText("＋ 记一笔").performClick()
        compose.onNodeWithText("金额（人民币）").performTextInput("120")
        compose.onNodeWithText("无事件",substring=false).performScrollTo()
        compose.onNodeWithText("无事件",substring=false).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("北欧旅行", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("北欧旅行",substring=false).performClick()
        compose.onNodeWithText("项目总额").assertTextContains("120")
        compose.onNodeWithTag("participant-me").performScrollTo().performClick()
        compose.onNodeWithText("我的支出 ¥ 0.00").performScrollTo().assertIsDisplayed()
        capture("05-split-preview")
        compose.onNodeWithText("保存",substring=false).performClick()
        compose.runOnIdle { assertEquals("120",saved!!.entry.amount);assertEquals("me",saved!!.payerId);assertEquals(setOf("a","b"),saved!!.participants) }
    }
    @Test fun memberManagementSupportsLongNamesAtLargeFont() {
        var renamed: String? = null
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1.5f)) {
                LedgerTheme { LedgerScreen(state(), saveMember = { _, _, name, done -> renamed = name; done() }) }
            }
        }
        compose.onNodeWithText("事件", useUnmergedTree = true).performClick()
        compose.onNodeWithText("北欧旅行").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("成员（3）"))
        compose.onNodeWithText("成员（3）").performClick()
        compose.onAllNodesWithText("改名")[0].performScrollTo().performClick()
        compose.onNodeWithText("新昵称").performScrollTo().performTextReplacement("一起旅行的摄影同行者")
        capture("06-members-large-font")
        compose.onNodeWithText("保存昵称").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("一起旅行的摄影同行者", renamed) }
        compose.onNodeWithText("完成", substring = false).performClick()
    }

}
