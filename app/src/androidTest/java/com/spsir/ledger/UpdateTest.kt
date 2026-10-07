package com.spsir.ledger

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdateTest {
    @get:Rule val compose = createComposeRule()
    private val sample = AppUpdate(installedVersion(InstrumentationRegistry.getInstrumentation().targetContext).first + 1, "0.4.5", 26, "测试更新", "$RELEASES_URL/tag/v0.4.5")
    private fun release() = JSONObject("""{"draft":false,"prerelease":false,"tag_name":"v0.4.5","html_url":"$RELEASES_URL/tag/v0.4.5","body":"测试更新","assets":[{"name":"SPSir-0.4.5.apk","browser_download_url":"$RELEASES_URL/download/v0.4.5/SPSir-0.4.5.apk"}]}""")
    private fun manifest() = JSONObject("""{"applicationId":"com.spsir.ledger","versionName":"0.4.5","versionCode":${installedVersion(InstrumentationRegistry.getInstrumentation().targetContext).first + 1},"minSdk":26,"apkName":"SPSir-0.4.5.apk","sha256":"${"a".repeat(64)}"}""")

    @Test fun metadataRejectsWrongIdentityVersionAndUnsafeLinks() {
        assertEquals(sample, parseAppUpdate(release(), manifest()))
        for (invalid in listOf(manifest().put("applicationId", "other"), manifest().put("versionName", "0.4.6"),
            manifest().put("versionCode", 0), manifest().put("sha256", "bad"), manifest().put("apkName", "other.apk"))) {
            assertThrows(Exception::class.java) { parseAppUpdate(release(), invalid) }
        }
        assertThrows(Exception::class.java) { parseAppUpdate(release().put("prerelease", true), manifest()) }
        assertThrows(Exception::class.java) { parseAppUpdate(release().put("html_url", "https://example.com"), manifest()) }
    }

    @Test fun throttleIncludesClockRollbackAndDisabledSetting() {
        assertTrue(shouldCheckUpdate(true, 100, 0))
        assertFalse(shouldCheckUpdate(false, 100, 0))
        assertFalse(shouldCheckUpdate(true, 1000, 999))
        assertTrue(shouldCheckUpdate(true, 100, 200))
        assertTrue(shouldCheckUpdate(true, 86400001, 1))
    }

    @Test fun manualBypassesIgnoreAndThrottleAndFailuresStayQuietAutomatically() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "update-test-${System.nanoTime()}"
        val app = object : Application() {
            init { attachBaseContext(context) }
            override fun getSharedPreferences(ignored: String, mode: Int) = context.getSharedPreferences(name, mode)
        }
        var calls = 0
        var fail = false
        lateinit var vm: UpdateViewModel
        try {
            compose.runOnIdle { vm = UpdateViewModel(app) { calls++; if (fail) error("offline"); sample }; vm.check() }
            compose.waitUntil(5000) { !vm.state.value.checking }
            assertEquals(sample, vm.state.value.available)
            compose.runOnIdle { vm.dismiss(true); vm.check() }
            assertEquals(1, calls)
            compose.runOnIdle { vm.check(true) }
            compose.waitUntil(5000) { !vm.state.value.checking }
            assertEquals(sample, vm.state.value.available)
            compose.runOnIdle { vm.dismiss(); fail = true; vm.check(true) }
            compose.waitUntil(5000) { !vm.state.value.checking }
            assertEquals("检查失败，请稍后重试", vm.state.value.message)
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putLong("lastAttempt", 0).commit()
            compose.runOnIdle { vm.check() }
            compose.waitUntil(5000) { !vm.state.value.checking }
            assertNull(vm.state.value.message)
            compose.runOnIdle { vm.automatic(false) }
            assertFalse(context.getSharedPreferences(name, 0).getBoolean("automatic", true))
        } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun browserOnlyOpensOnUserAction() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        var opened: Intent? = null
        var dismissed = false
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? {
                if (intent.action == Intent.ACTION_VIEW) {
                    opened = intent
                    return android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null)
                }
                return null
            }
        }
        instrument.addMonitor(monitor)
        try {
            compose.setContent { LedgerTheme { UpdatePrompt(sample) { dismissed = true } } }
            assertNull(opened)
            compose.onNodeWithText("前往下载").performClick()
            compose.waitUntil(5000) { opened != null }
            assertEquals(sample.page, opened!!.dataString)
            compose.runOnIdle { assertTrue(dismissed) }
        } finally { instrument.removeMonitor(monitor) }
    }

    @Test fun currentAndIncompatibleVersionsNeverOfferDownload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "update-test-${System.nanoTime()}"
        val app = object : Application() {
            init { attachBaseContext(context) }
            override fun getSharedPreferences(ignored: String, mode: Int) = context.getSharedPreferences(name, mode)
        }
        var result = sample.copy(code = installedVersion(InstrumentationRegistry.getInstrumentation().targetContext).first)
        lateinit var vm: UpdateViewModel
        try {
            compose.runOnIdle { vm = UpdateViewModel(app) { result }; vm.check(true) }
            compose.waitUntil(5000) { !vm.state.value.checking }
            assertNull(vm.state.value.available)
            assertEquals("当前已是最新版本", vm.state.value.message)
            compose.runOnIdle { result = sample.copy(minSdk = 999); vm.check(true) }
            compose.waitUntil(5000) { !vm.state.value.checking }
            assertNull(vm.state.value.available)
            assertEquals("新版本需要更高版本的 Android", vm.state.value.message)
        } finally { context.deleteSharedPreferences(name) }
    }
}
