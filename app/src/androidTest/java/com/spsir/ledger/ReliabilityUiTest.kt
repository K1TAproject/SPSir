package com.spsir.ledger

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReliabilityUiTest {
    @get:Rule val compose = createComposeRule()
    private fun state() = LedgerState(loading = false, categories = presetCategories(), events = listOf(LedgerEvent("trip", "旅行")))

    @Test fun dirtyEntryRequiresConfirmationAndDiscardStartsFresh() {
        compose.setContent { LedgerTheme { LedgerScreen(state()) } }
        compose.onNodeWithText("＋ 记一笔").performClick()
        compose.onNodeWithText("取消", substring = false).performClick()
        compose.onNodeWithText("放弃修改？").assertDoesNotExist()
        compose.onNodeWithText("＋ 记一笔").performClick()
        compose.onNodeWithText("金额（人民币）").performTextInput("12.34")
        compose.onNodeWithText("取消", substring = false).performClick()
        compose.onNodeWithText("放弃修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("12.34").assertExists()
        compose.onNodeWithText("取消", substring = false).performClick()
        compose.onNodeWithText("放弃修改", substring = false).performClick()
        compose.onNodeWithText("＋ 记一笔").performClick()
        compose.onNodeWithText("12.34").assertDoesNotExist()
    }

    @Test fun systemBackReturnsThroughToolsEventAndTabs() {
        lateinit var back: OnBackPressedDispatcher
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            LedgerTheme { LedgerScreen(state()) }
        }
        compose.onNodeWithText("事件", substring = false).performClick()
        compose.onNodeWithText("旅行", substring = false).performClick()
        compose.onNodeWithText("工具", substring = false).performClick()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("旅行", substring = false).assertExists()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("＋ 新建事件").assertExists()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("搜索与筛选").assertExists()
    }

    @Test fun publicDraftSurvivesRestorationWhileDatabaseLoads() {
        val members = listOf(EventMember("me", "trip", "我", true, 0), EventMember("a", "trip", "甲", false, 1))
        val ready = state().copy(publicEvents = listOf(PublicBundle(PublicEvent("trip"), members, emptyList())))
        var data by mutableStateOf(ready)
        val restoration = StateRestorationTester(compose)
        var saved: PublicDraft? = null
        restoration.setContent { LedgerTheme { LedgerScreen(data, savePublic = { d, done -> saved = d; done() }) } }
        compose.onNodeWithText("事件", substring = false).performClick()
        compose.onNodeWithText("旅行", substring = false).performClick()
        compose.onNodeWithText("＋ 记开支").performClick()
        compose.onNodeWithText("项目总额").performTextInput("90")
        compose.onNodeWithTag("participant-a").performScrollTo().performClick()
        compose.runOnIdle { data = LedgerState() }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { data = ready }
        compose.onNodeWithText("90").assertExists()
        compose.onNodeWithTag("participant-a").assertIsOff()
        compose.onNodeWithText("取消", substring = false).performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.runOnIdle { assertEquals("90", saved!!.entry.amount); assertEquals(setOf("me"), saved!!.participants) }
    }

    @Test fun ordinaryDraftSurvivesRestorationAndSavesOnce() {
        val restoration = StateRestorationTester(compose)
        var saved: EntryDraft? = null
        var count = 0
        restoration.setContent { LedgerTheme { LedgerScreen(state(), onSave = { d, done -> saved = d; count++; done() }) } }
        compose.onNodeWithText("＋ 记一笔").performClick()
        compose.onNodeWithText("金额（人民币）").performTextInput("8.50")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("8.50").assertExists()
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.runOnIdle { assertEquals("8.50", saved!!.amount); assertEquals(1, count) }
    }
}
