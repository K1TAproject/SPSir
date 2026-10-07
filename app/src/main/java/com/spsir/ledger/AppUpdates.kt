package com.spsir.ledger

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val RELEASES_URL = "https://github.com/K1TAproject/SPSir/releases"
private const val RELEASE_API = "https://api.github.com/repos/K1TAproject/SPSir/releases/latest"
private const val DOWNLOAD_PREFIX = "$RELEASES_URL/download/"
private const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L

data class AppUpdate(val code: Int, val version: String, val minSdk: Int, val notes: String, val page: String)
data class UpdateState(val automatic: Boolean = true, val checking: Boolean = false,
    val message: String? = null, val available: AppUpdate? = null)

@Suppress("DEPRECATION")
fun installedVersion(context: android.content.Context): Pair<Int, String> {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return info.versionCode to info.versionName.orEmpty()
}

fun shouldCheckUpdate(enabled: Boolean, now: Long, previous: Long): Boolean =
    enabled && (previous == 0L || now < previous || now - previous >= CHECK_INTERVAL)

fun parseAppUpdate(release: JSONObject, manifest: JSONObject): AppUpdate {
    require(!release.getBoolean("draft") && !release.getBoolean("prerelease"))
    val version = manifest.getString("versionName")
    require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
    require(release.getString("tag_name") == "v$version")
    val page = "$RELEASES_URL/tag/v$version"
    require(release.getString("html_url") == page)
    require(manifest.getString("applicationId") == "com.spsir.ledger")
    val code = manifest.getInt("versionCode")
    val minSdk = manifest.getInt("minSdk")
    require(code > 0 && minSdk > 0 && manifest.get("versionCode").toString() == code.toString()
        && manifest.get("minSdk").toString() == minSdk.toString())
    val name = manifest.getString("apkName")
    require(name == "SPSir-$version.apk")
    require(manifest.getString("sha256").matches(Regex("[a-fA-F0-9]{64}")))
    val assets = release.getJSONArray("assets")
    require((0 until assets.length()).any {
        val asset = assets.getJSONObject(it)
        asset.getString("name") == name && asset.getString("browser_download_url") == "${DOWNLOAD_PREFIX}v$version/$name"
    })
    return AppUpdate(code, version, minSdk, release.optString("body").take(12000), page)
}

// Only release metadata is fetched. No ledger data, identifiers, or credentials are sent.
private fun readUpdateJson(address: String): JSONObject {
    var url = URL(address)
    repeat(6) {
        require(url.protocol == "https" && url.userInfo == null && (url.port == -1 || url.port == 443))
        require(url.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"))
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000; connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "SPSir-update-check")
            when (connection.responseCode) {
                301, 302, 303, 307, 308 -> url = URL(url, requireNotNull(connection.getHeaderField("Location")))
                200 -> {
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 256 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    require(bytes.size <= 256 * 1024)
                    return JSONObject(bytes.toString(Charsets.UTF_8))
                }
                else -> error("Update metadata unavailable")
            }
        } finally { connection.disconnect() }
    }
    error("Too many redirects")
}

internal suspend fun fetchAppUpdate(): AppUpdate = withContext(Dispatchers.IO) {
    val release = readUpdateJson(RELEASE_API)
    val assets = release.getJSONArray("assets")
    val metadata = (0 until assets.length()).map { assets.getJSONObject(it) }.single { it.getString("name") == "update.json" }
    val address = metadata.getString("browser_download_url")
    val tag = release.getString("tag_name")
    require(tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+")))
    require(address == "$DOWNLOAD_PREFIX$tag/update.json")
    parseAppUpdate(release, readUpdateJson(address))
}

class UpdateViewModel @JvmOverloads constructor(application: Application,
    private val fetch: suspend () -> AppUpdate = ::fetchAppUpdate) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("updates", 0)
    private val mutable = MutableStateFlow(UpdateState(automatic = preferences.getBoolean("automatic", true)))
    val state = mutable.asStateFlow()

    fun automatic(enabled: Boolean) {
        preferences.edit { putBoolean("automatic", enabled) }
        mutable.value = mutable.value.copy(automatic = enabled)
    }
    fun dismiss(ignore: Boolean = false) {
        if (ignore) mutable.value.available?.let { preferences.edit { putInt("ignored", it.code) } }
        mutable.value = mutable.value.copy(available = null)
    }
    fun check(manual: Boolean = false) {
        if (mutable.value.checking) return
        val now = System.currentTimeMillis()
        if (!manual && !shouldCheckUpdate(mutable.value.automatic, now, preferences.getLong("lastAttempt", 0))) return
        preferences.edit { putLong("lastAttempt", now) }
        mutable.value = mutable.value.copy(checking = true, message = null)
        viewModelScope.launch {
            try {
                val result = fetch()
                val newer = result.code > installedVersion(getApplication()).first
                val compatible = result.minSdk <= android.os.Build.VERSION.SDK_INT
                val show = newer && compatible && (manual || result.code != preferences.getInt("ignored", 0))
                mutable.value = mutable.value.copy(available = if (show) result else null, message = if (!manual) null else when {
                    !newer -> "当前已是最新版本"
                    !compatible -> "新版本需要更高版本的 Android"
                    else -> null
                })
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(message = if (manual) "检查失败，请稍后重试" else null) }
            finally { mutable.value = mutable.value.copy(checking = false) }
        }
    }
}
