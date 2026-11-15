package com.honghoaibao.bbooster.feature.devtools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honghoaibao.bbooster.R
import com.honghoaibao.bbooster.core.logging.AppLog
import com.honghoaibao.bbooster.core.logging.LogEntry
import com.honghoaibao.bbooster.core.logging.LogLevel
import com.honghoaibao.bbooster.core.logging.LogModule
import com.honghoaibao.bbooster.core.logging.LogQuery
import com.honghoaibao.bbooster.ui.theme.Amber
import com.honghoaibao.bbooster.ui.theme.DarkSlate
import com.honghoaibao.bbooster.ui.theme.ElectricCyan
import com.honghoaibao.bbooster.ui.theme.Emerald
import com.honghoaibao.bbooster.ui.theme.MutedText
import com.honghoaibao.bbooster.ui.theme.Obsidian
import com.honghoaibao.bbooster.ui.theme.SlateBorder
import com.honghoaibao.bbooster.ui.theme.SoftWhite
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_VISIBLE_LOGS = 500

@Composable
fun LogViewerScreen(
    onBack: () -> Unit
) {
    val revision by AppLog.revision.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var searchText by remember { mutableStateOf("") }
    var selectedLevels by remember { mutableStateOf(emptySet<LogLevel>()) }
    var selectedModules by remember { mutableStateOf(emptySet<LogModule>()) }
    var entries by remember { mutableStateOf(emptyList<LogEntry>()) }
    var storageBytes by remember { mutableStateOf(0L) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshCounter by remember { mutableIntStateOf(0) }
    var selectedEntry by remember { mutableStateOf<LogEntry?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val clearSuccessMessage = stringResource(R.string.log_clear_success)
    val clearFailedMessage = stringResource(R.string.log_clear_failed)

    LaunchedEffect(searchText, selectedLevels, selectedModules, revision, refreshCounter) {
        delay(180L)
        isLoading = true
        val result = withContext(Dispatchers.IO) {
            val query = LogQuery(
                levels = selectedLevels,
                modules = selectedModules,
                search = searchText,
                limit = MAX_VISIBLE_LOGS
            )
            AppLog.readLogs(query).asReversed() to AppLog.logSizeBytes()
        }
        entries = result.first
        storageBytes = result.second
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    stringResource(R.string.devtools_title),
                    color = SoftWhite,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.log_viewer_title),
                    color = ElectricCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.log_back), color = SoftWhite)
            }
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.log_search_label)) },
            placeholder = { Text(stringResource(R.string.log_search_hint)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricCyan,
                focusedLabelColor = ElectricCyan,
                cursorColor = ElectricCyan,
                unfocusedBorderColor = SlateBorder,
                focusedTextColor = SoftWhite,
                unfocusedTextColor = SoftWhite
            )
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.log_filter_level),
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                FilterChip(
                    selected = selectedLevels.isEmpty(),
                    onClick = { selectedLevels = emptySet() },
                    label = { Text(stringResource(R.string.log_filter_all)) }
                )
                LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = level in selectedLevels,
                        onClick = {
                            selectedLevels = if (level in selectedLevels) {
                                selectedLevels - level
                            } else {
                                selectedLevels + level
                            }
                        },
                        label = { Text(level.localizedLabel()) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.log_filter_module),
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                FilterChip(
                    selected = selectedModules.isEmpty(),
                    onClick = { selectedModules = emptySet() },
                    label = { Text(stringResource(R.string.log_filter_all)) }
                )
                LogModule.entries.forEach { module ->
                    FilterChip(
                        selected = module in selectedModules,
                        onClick = {
                            selectedModules = if (module in selectedModules) {
                                selectedModules - module
                            } else {
                                selectedModules + module
                            }
                        },
                        label = { Text(module.localizedLabel()) }
                    )
                }
            }
        }

        Surface(
            color = DarkSlate,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.log_entry_count, entries.size),
                        color = SoftWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text(
                        stringResource(R.string.log_storage_used, formatBytes(storageBytes)),
                        color = MutedText,
                        fontSize = 10.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { refreshCounter++ },
                        enabled = !isLoading && !isClearing
                    ) {
                        Text(stringResource(R.string.log_refresh), color = ElectricCyan)
                    }
                    TextButton(
                        onClick = { showClearConfirmation = true },
                        enabled = storageBytes > 0L && !isClearing
                    ) {
                        Text(stringResource(R.string.log_clear), color = Color(0xFFFF858C))
                    }
                }
            }
        }

        if (isLoading && entries.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ElectricCyan)
            }
        } else if (entries.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        stringResource(R.string.log_empty_title),
                        color = SoftWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.log_empty_description),
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = entries,
                    key = { index, entry -> "${entry.timestampMillis}-${entry.event}-$index" }
                ) { _, entry ->
                    LogEntryCard(
                        entry = entry,
                        onClick = { selectedEntry = entry }
                    )
                }
            }
        }

        if (isLoading && entries.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = ElectricCyan,
                trackColor = SlateBorder
            )
        }

        statusMessage?.let { message ->
            Text(
                message,
                color = Emerald,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    selectedEntry?.let { entry ->
        LogEntryDetailDialog(
            entry = entry,
            onDismiss = { selectedEntry = null }
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isClearing) showClearConfirmation = false },
            containerColor = DarkSlate,
            title = {
                Text(stringResource(R.string.log_clear_confirm_title), color = SoftWhite)
            },
            text = {
                Text(stringResource(R.string.log_clear_confirm_message), color = MutedText)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmation = false
                        isClearing = true
                        statusMessage = null
                        coroutineScope.launch {
                            val cleared = withContext(Dispatchers.IO) {
                                AppLog.clearLogsAndWait()
                            }
                            isClearing = false
                            statusMessage = if (cleared) {
                                clearSuccessMessage
                            } else {
                                clearFailedMessage
                            }
                        }
                    },
                    enabled = !isClearing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Obsidian
                    )
                ) {
                    Text(stringResource(R.string.log_clear))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmation = false },
                    enabled = !isClearing
                ) {
                    Text(stringResource(R.string.log_cancel), color = MutedText)
                }
            }
        )
    }
}

@Composable
private fun LogEntryCard(
    entry: LogEntry,
    onClick: () -> Unit
) {
    val levelColor = entry.level.color()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = levelColor.copy(alpha = 0.13f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            entry.level.localizedLabel(),
                            color = levelColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        entry.module.localizedLabel(),
                        color = MutedText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    formatTimestamp(entry.timestampMillis),
                    color = MutedText,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
            Text(
                entry.event,
                color = SoftWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                entry.message,
                color = MutedText,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LogEntryDetailDialog(
    entry: LogEntry,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSlate,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    entry.event,
                    color = SoftWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${entry.level.localizedLabel()} · ${entry.module.localizedLabel()}",
                    color = entry.level.color(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    formatTimestamp(entry.timestampMillis),
                    color = MutedText,
                    fontSize = 10.sp
                )
                Surface(
                    color = Color(0xFF0D121B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SlateBorder)
                ) {
                    SelectionContainer {
                        Text(
                            entry.message.ifBlank { stringResource(R.string.log_no_message) },
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            color = SoftWhite,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.log_close), color = ElectricCyan)
            }
        }
    )
}

@Composable
private fun LogLevel.localizedLabel(): String = when (this) {
    LogLevel.DEBUG -> stringResource(R.string.log_level_debug)
    LogLevel.INFO -> stringResource(R.string.log_level_info)
    LogLevel.WARN -> stringResource(R.string.log_level_warn)
    LogLevel.ERROR -> stringResource(R.string.log_level_error)
}

@Composable
private fun LogModule.localizedLabel(): String = when (this) {
    LogModule.APP -> stringResource(R.string.log_module_app)
    LogModule.SHIZUKU -> stringResource(R.string.log_module_shizuku)
    LogModule.TRANSPORT -> stringResource(R.string.log_module_transport)
    LogModule.COMMAND_ENGINE -> stringResource(R.string.log_module_command)
    LogModule.DEVICE -> stringResource(R.string.log_module_device)
    LogModule.UI -> stringResource(R.string.log_module_ui)
    LogModule.REPORT -> stringResource(R.string.log_module_report)
}

private fun LogLevel.color(): Color = when (this) {
    LogLevel.DEBUG -> MutedText
    LogLevel.INFO -> ElectricCyan
    LogLevel.WARN -> Amber
    LogLevel.ERROR -> Color(0xFFFF737D)
}

private fun formatTimestamp(timestampMillis: Long): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestampMillis))
}.getOrDefault(timestampMillis.toString())

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "${bytes.coerceAtLeast(0L)} B"
    bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
}
