package com.spsir.ledger

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun UpdateTools(state: UpdateState, automatic: (Boolean) -> Unit, check: () -> Unit) {
    val context = LocalContext.current
    val version = remember { installedVersion(context).second }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("版本更新", style = MaterialTheme.typography.titleLarge)
        Text("SPSir $version")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("启动时检查更新", Modifier.weight(1f).padding(top = 12.dp))
            Switch(state.automatic, automatic)
        }
        Text("仅连接 GitHub 获取版本信息，不上传账本。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = !state.checking, onClick = check) { Text(if (state.checking) "检查中…" else "检查更新") }
        state.message?.let { Text(it) }
    }
}

@Composable
fun UpdatePrompt(update: AppUpdate?, dismiss: (Boolean) -> Unit) {
    if (update == null) return
    val context = LocalContext.current
    var error by remember(update) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { dismiss(false) }, title = { Text("发现新版本 ${update.version}") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(update.notes.ifBlank { "发布页提供版本详情。" })
            Text("将使用浏览器打开发布页。更新前请导出备份。")
            if (error) Text("无法打开浏览器，请稍后重试", color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, update.page.toUri())); dismiss(false) }
            catch (_: android.content.ActivityNotFoundException) { error = true }
        }) { Text("前往下载") } },
        dismissButton = { Row {
            TextButton(onClick = { dismiss(true) }) { Text("忽略此版本") }
            TextButton(onClick = { dismiss(false) }) { Text("稍后") }
        } })
}
