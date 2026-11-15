package com.honghoaibao.bbooster.feature.devtools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honghoaibao.bbooster.R
import com.honghoaibao.bbooster.core.diagnostics.report.DiagnosticReportWriter
import com.honghoaibao.bbooster.core.logging.AppLog
import com.honghoaibao.bbooster.core.settings.DeveloperSettings
import com.honghoaibao.bbooster.ui.theme.DarkSlate
import com.honghoaibao.bbooster.ui.theme.ElectricCyan
import com.honghoaibao.bbooster.ui.theme.Emerald
import com.honghoaibao.bbooster.ui.theme.MutedText
import com.honghoaibao.bbooster.ui.theme.Obsidian
import com.honghoaibao.bbooster.ui.theme.SlateBorder
import com.honghoaibao.bbooster.ui.theme.SoftWhite
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeveloperSettingsScreen(
    settings: DeveloperSettings,
    reportCacheDirectory: File,
    onBack: () -> Unit,
    onVerboseLoggingChanged: (Boolean) -> Unit,
    onReportRetentionChanged: (Int) -> Unit
) {
    val revision by AppLog.revision.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val writer = remember(reportCacheDirectory, settings.retainedDiagnosticReports) {
        DiagnosticReportWriter(
            directory = reportCacheDirectory,
            maxRetainedReports = settings.retainedDiagnosticReports
        )
    }

    var logStorageBytes by remember { mutableStateOf(0L) }
    var reportCount by remember { mutableIntStateOf(0) }
    var refreshCounter by remember { mutableIntStateOf(0) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var clearStatus by remember { mutableStateOf<String?>(null) }

    val clearSuccessMessage = stringResource(R.string.devsettings_clear_success)
    val clearFailureMessage = stringResource(R.string.devsettings_clear_failed)

    LaunchedEffect(revision, refreshCounter) {
        logStorageBytes = withContext(Dispatchers.IO) {
            AppLog.logSizeBytes()
        }
    }

    LaunchedEffect(settings.retainedDiagnosticReports, refreshCounter) {
        reportCount = withContext(Dispatchers.IO) {
            writer.pruneToRetention()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    stringResource(R.string.devsettings_title),
                    color = SoftWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.devsettings_subtitle),
                    color = MutedText,
                    fontSize = 12.sp
                )
            }
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.log_back), color = SoftWhite)
            }
        }

        SettingsSectionCard(
            title = stringResource(R.string.devsettings_logging_title),
            description = stringResource(R.string.devsettings_logging_description)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(R.string.devsettings_verbose_title),
                        color = SoftWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.devsettings_verbose_description),
                        color = MutedText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
                Switch(
                    checked = settings.verboseLoggingEnabled,
                    onCheckedChange = onVerboseLoggingChanged
                )
            }
            SettingDivider()
            SettingValueRow(
                label = stringResource(R.string.devsettings_log_storage),
                value = formatBytes(logStorageBytes)
            )
            Text(
                stringResource(R.string.devsettings_log_storage_note),
                color = MutedText,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }

        SettingsSectionCard(
            title = stringResource(R.string.devsettings_report_title),
            description = stringResource(R.string.devsettings_report_description)
        ) {
            Text(
                stringResource(R.string.devsettings_retention_label),
                color = SoftWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeveloperSettings.RETENTION_OPTIONS.sorted().forEach { count ->
                    FilterChip(
                        selected = settings.retainedDiagnosticReports == count,
                        onClick = { onReportRetentionChanged(count) },
                        label = { Text(count.toString()) }
                    )
                }
            }
            SettingValueRow(
                label = stringResource(R.string.devsettings_reports_saved),
                value = reportCount.toString()
            )
            Text(
                stringResource(
                    R.string.devsettings_reports_retained,
                    settings.retainedDiagnosticReports
                ),
                color = MutedText,
                fontSize = 10.sp
            )
        }

        SettingsSectionCard(
            title = stringResource(R.string.devsettings_data_title),
            description = stringResource(R.string.devsettings_data_description)
        ) {
            Button(
                onClick = { showClearConfirmation = true },
                enabled = !isClearing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B303A),
                    contentColor = SoftWhite,
                    disabledContainerColor = SlateBorder,
                    disabledContentColor = MutedText
                )
            ) {
                Text(
                    stringResource(R.string.devsettings_clear_button),
                    fontWeight = FontWeight.Bold
                )
            }
            if (isClearing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = ElectricCyan,
                        strokeWidth = 2.dp
                    )
                    Text(
                        stringResource(R.string.devsettings_clearing),
                        color = MutedText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 9.dp)
                    )
                }
            }
            clearStatus?.let { message ->
                Text(
                    message,
                    color = if (message == clearSuccessMessage) Emerald else Color(0xFFFF858C),
                    fontSize = 11.sp
                )
            }
        }

        Text(
            stringResource(R.string.devsettings_local_note),
            color = MutedText,
            fontSize = 10.sp,
            lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isClearing) showClearConfirmation = false },
            containerColor = DarkSlate,
            title = {
                Text(
                    stringResource(R.string.devsettings_clear_confirm_title),
                    color = SoftWhite
                )
            },
            text = {
                Text(
                    stringResource(R.string.devsettings_clear_confirm_message),
                    color = MutedText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmation = false
                        clearStatus = null
                        isClearing = true
                        coroutineScope.launch {
                            val cleared = try {
                                withContext(Dispatchers.IO) {
                                    val logsCleared = AppLog.clearLogsAndWait()
                                    val reportsCleared = writer.clearAllReports()
                                    logsCleared && reportsCleared
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                false
                            } finally {
                                isClearing = false
                            }
                            clearStatus = if (cleared) {
                                clearSuccessMessage
                            } else {
                                clearFailureMessage
                            }
                            refreshCounter++
                        }
                    },
                    enabled = !isClearing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B303A),
                        contentColor = SoftWhite
                    )
                ) {
                    Text(stringResource(R.string.devsettings_clear_button))
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
private fun SettingsSectionCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                color = ElectricCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                description,
                color = MutedText,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
            content()
        }
    }
}

@Composable
private fun SettingDivider() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(1.dp).padding(vertical = 0.dp),
        color = SlateBorder,
        content = {}
    )
}

@Composable
private fun SettingValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MutedText, fontSize = 11.sp)
        Text(value, color = SoftWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "${bytes.coerceAtLeast(0L)} B"
    bytes < 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024.0))
}
