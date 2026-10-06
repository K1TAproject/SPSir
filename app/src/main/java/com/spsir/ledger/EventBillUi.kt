package com.spsir.ledger

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun EventBillDialog(event: LedgerEvent, bundle: PublicBundle, categories: List<Category>, close: () -> Unit) {
    val bill = remember { EventBill(event, bundle, categories) }
    var details by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf(false) }
    val text = remember(bill, details, notes) { bill.text(details, notes) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    suspend fun freeze(): File = withContext(Dispatchers.IO) {
        cacheEventBill(context, text)
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val source = pendingPath?.let { File(it) }
        if (uri == null) { source?.delete(); pendingPath = null }
        else scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(source) { "账单预览已失效，请重新保存" }
                    saveEventBill(context.contentResolver, uri, source)
                }
                message = "账单已保存"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "保存失败，请重新选择位置" }
            finally { source?.delete(); pendingPath = null; busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy && pendingPath == null) close() }, title = { Text("账单预览") },
        modifier = Modifier.systemBarsPadding().fillMaxWidth().padding(horizontal = 12.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row { Checkbox(details, { details = it }, enabled = !busy && pendingPath == null, modifier = Modifier.testTag("bill-details")); Text("附带明细", Modifier.padding(top = 12.dp)) }
            Row { Checkbox(notes, { notes = it }, enabled = details && !busy && pendingPath == null, modifier = Modifier.testTag("bill-notes")); Text("包含备注", Modifier.padding(top = 12.dp)) }
            Text(text, style = MaterialTheme.typography.bodySmall)
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy && pendingPath == null, onClick = { scope.launch {
                    busy = true
                    try { pendingPath = freeze().absolutePath; save.launch("SPSir-账单.txt") }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { pendingPath?.let { File(it).delete() }; pendingPath = null; message = "无法打开保存位置" }
                    finally { busy = false }
                } }) { Text("保存文本") }
                Button(enabled = !busy && pendingPath == null, onClick = { scope.launch {
                    busy = true
                    try {
                        val file = freeze()
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("SPSir 账单", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享账单"))
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { message = "分享失败，请重试" }
                    finally { busy = false }
                } }) { Text("分享") }
            }
        } }, confirmButton = { TextButton(enabled = !busy && pendingPath == null, onClick = close) { Text("关闭") } })
}

// Call on Dispatchers.IO. These helpers never access the ledger database.
fun cacheEventBill(context: android.content.Context, text: String): File {
    val directory = File(context.cacheDir, "bills").apply { mkdirs() }
    return File.createTempFile("SPSir-", ".txt", directory).apply { writeText(text, Charsets.UTF_8) }
}

fun saveEventBill(resolver: android.content.ContentResolver, uri: android.net.Uri, source: File) {
    source.inputStream().use { input ->
        requireNotNull(resolver.openOutputStream(uri, "wt")).use { input.copyTo(it) }
    }
}
