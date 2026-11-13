package com.honghoaibao.bbooster.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honghoaibao.bbooster.R
import com.honghoaibao.bbooster.core.shizuku.CommandBatchResult
import com.honghoaibao.bbooster.core.shizuku.CommandEngineError
import com.honghoaibao.bbooster.core.shizuku.CommandEngineState
import com.honghoaibao.bbooster.core.shizuku.CommandServiceStatus
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionState
import com.honghoaibao.bbooster.core.shizuku.ShizukuConnectionStatus
import com.honghoaibao.bbooster.core.shizuku.ShizukuPrivilege
import com.honghoaibao.bbooster.ui.theme.Amber
import com.honghoaibao.bbooster.ui.theme.DarkSlate
import com.honghoaibao.bbooster.ui.theme.ElectricCyan
import com.honghoaibao.bbooster.ui.theme.Emerald
import com.honghoaibao.bbooster.ui.theme.MutedText
import com.honghoaibao.bbooster.ui.theme.Obsidian
import com.honghoaibao.bbooster.ui.theme.SlateBorder
import com.honghoaibao.bbooster.ui.theme.SoftWhite

@Composable
fun DashboardScreen(
    shizukuState: ShizukuConnectionState,
    commandState: CommandEngineState,
    selectedLanguage: String,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRefreshShizuku: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Header(shizukuState)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.hero_title),
                color = SoftWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.hero_description),
                color = MutedText,
                fontSize = 14.sp
            )
        }

        ShizukuStatusCard(
            state = shizukuState,
            onRequestPermission = onRequestShizukuPermission,
            onOpenShizuku = onOpenShizuku,
            onRefresh = onRefreshShizuku
        )

        CommandEngineCard(
            shizukuReady = shizukuState.status == ShizukuConnectionStatus.READY,
            state = commandState,
            onRunDiagnostics = onRunDiagnostics
        )

        LanguageSelectorCard(selectedLanguage, onLanguageSelected)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(
                title = stringResource(R.string.section_device_overview),
                trailing = stringResource(R.string.phase_three)
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSlate),
                border = BorderStroke(1.dp, SlateBorder),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        stringResource(R.string.telemetry_title),
                        color = SoftWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        stringResource(R.string.telemetry_description),
                        color = MutedText,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusTag(stringResource(R.string.ram_pending))
                        StatusTag(stringResource(R.string.battery_pending))
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(
                title = stringResource(R.string.boost_tools),
                trailing = stringResource(R.string.coming_next)
            )
            FeatureCard(
                stringResource(R.string.game_library),
                stringResource(R.string.game_library_description)
            )
            FeatureCard(
                stringResource(R.string.optimization_engine),
                stringResource(R.string.optimization_engine_description)
            )
            FeatureCard(
                stringResource(R.string.session_recovery),
                stringResource(R.string.session_recovery_description)
            )
        }

        Surface(
            color = Color(0xFF111722),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Text(
                text = stringResource(R.string.version_footer),
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth().padding(14.dp)
            )
        }
    }
}

@Composable
private fun Header(state: ShizukuConnectionState) {
    val badgeColor = state.status.badgeColor()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.app_name),
                color = SoftWhite,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                stringResource(R.string.header_subtitle),
                color = MutedText,
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Surface(
            color = badgeColor.copy(alpha = 0.13f),
            shape = CircleShape,
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Surface(
                    modifier = Modifier.width(7.dp).height(7.dp),
                    color = badgeColor,
                    shape = CircleShape
                ) {}
                Text(
                    state.status.badgeLabel(),
                    color = badgeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun ShizukuStatusCard(
    state: ShizukuConnectionState,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRefresh: () -> Unit
) {
    val isReady = state.status == ShizukuConnectionStatus.READY
    val title = when (state.status) {
        ShizukuConnectionStatus.CHECKING -> stringResource(R.string.shizuku_checking_title)
        ShizukuConnectionStatus.SERVICE_NOT_RUNNING -> stringResource(R.string.shizuku_off_title)
        ShizukuConnectionStatus.PERMISSION_REQUIRED -> stringResource(R.string.shizuku_permission_title)
        ShizukuConnectionStatus.REQUESTING_PERMISSION -> stringResource(R.string.shizuku_requesting_title)
        ShizukuConnectionStatus.READY -> stringResource(R.string.shizuku_ready_title)
        ShizukuConnectionStatus.UNSUPPORTED_API -> stringResource(R.string.shizuku_unsupported_title)
        ShizukuConnectionStatus.ERROR -> stringResource(R.string.shizuku_error_title)
    }
    val description = when (state.status) {
        ShizukuConnectionStatus.CHECKING -> stringResource(R.string.shizuku_checking_description)
        ShizukuConnectionStatus.SERVICE_NOT_RUNNING -> stringResource(R.string.shizuku_off_description)
        ShizukuConnectionStatus.PERMISSION_REQUIRED -> stringResource(R.string.shizuku_permission_description)
        ShizukuConnectionStatus.REQUESTING_PERMISSION -> stringResource(R.string.shizuku_requesting_description)
        ShizukuConnectionStatus.READY -> stringResource(R.string.shizuku_ready_description)
        ShizukuConnectionStatus.UNSUPPORTED_API -> stringResource(R.string.shizuku_unsupported_description)
        ShizukuConnectionStatus.ERROR -> stringResource(R.string.shizuku_error_description)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, if (isReady) Emerald.copy(alpha = 0.55f) else SlateBorder),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.shizuku_section),
                color = ElectricCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(title, color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(description, color = MutedText, fontSize = 13.sp, lineHeight = 19.sp)

            if (isReady) {
                Text(
                    state.connectionDetail(),
                    color = Emerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            when (state.status) {
                ShizukuConnectionStatus.PERMISSION_REQUIRED -> ActionButton(
                    stringResource(R.string.grant_permission), onRequestPermission
                )
                ShizukuConnectionStatus.SERVICE_NOT_RUNNING,
                ShizukuConnectionStatus.UNSUPPORTED_API -> ActionButton(
                    stringResource(R.string.open_shizuku), onOpenShizuku
                )
                ShizukuConnectionStatus.ERROR -> ActionButton(
                    stringResource(R.string.retry_connection), onRefresh
                )
                else -> Unit
            }

            TextButton(
                onClick = onRefresh,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.refresh_status), color = MutedText)
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = ElectricCyan,
            contentColor = Obsidian
        )
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CommandEngineCard(
    shizukuReady: Boolean,
    state: CommandEngineState,
    onRunDiagnostics: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(
            1.dp,
            if (state.serviceStatus == CommandServiceStatus.READY) Emerald.copy(alpha = 0.55f)
            else SlateBorder
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.command_engine_title),
                color = ElectricCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                stringResource(R.string.command_engine_description),
                color = MutedText,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            if (state.isRunning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp).height(18.dp),
                        strokeWidth = 2.dp,
                        color = ElectricCyan
                    )
                    Text(stringResource(R.string.command_running), color = Amber, fontSize = 12.sp)
                }
            } else if (state.serviceStatus == CommandServiceStatus.BINDING) {
                Text(stringResource(R.string.command_binding), color = Amber, fontSize = 12.sp)
            } else if (state.serviceStatus == CommandServiceStatus.READY) {
                Text(stringResource(R.string.command_service_ready), color = Emerald, fontSize = 12.sp)
            }

            state.error?.let { error ->
                Text(
                    error.localizedMessage(),
                    color = Color(0xFFFF7A83),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                state.errorDetail?.takeIf(String::isNotBlank)?.let { detail ->
                    Text(
                        stringResource(R.string.command_error_detail, detail),
                        color = MutedText,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
            state.lastResult?.let { DiagnosticResult(it) }

            Button(
                onClick = onRunDiagnostics,
                enabled = shizukuReady && !state.isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = Obsidian,
                    disabledContainerColor = SlateBorder,
                    disabledContentColor = MutedText
                )
            ) {
                Text(stringResource(R.string.run_diagnostics), fontWeight = FontWeight.Bold)
            }

            if (!shizukuReady) {
                Text(
                    stringResource(R.string.command_connect_first),
                    color = MutedText,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun DiagnosticResult(result: CommandBatchResult) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val status = when {
            result.timedOut -> stringResource(R.string.command_timed_out)
            result.exitCode == 0 -> stringResource(R.string.command_completed)
            else -> stringResource(R.string.command_failed)
        }
        Text(
            status,
            color = if (result.exitCode == 0 && !result.timedOut) Emerald else Amber,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(R.string.command_exit_code, result.exitCode),
            color = MutedText,
            fontSize = 11.sp
        )
        val output = result.stdout.ifBlank { result.stderr }.trim()
        if (output.isNotBlank()) {
            Surface(
                color = Color(0xFF0D121B),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SlateBorder)
            ) {
                Text(
                    output.take(1200),
                    color = SoftWhite,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun LanguageSelectorCard(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.language_title),
                color = SoftWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LanguageOption(
                    modifier = Modifier.weight(1f),
                    label = "Tiếng Việt",
                    selected = selectedLanguage == "vi",
                    onClick = { onLanguageSelected("vi") }
                )
                LanguageOption(
                    modifier = Modifier.weight(1f),
                    label = "English",
                    selected = selectedLanguage != "vi",
                    onClick = { onLanguageSelected("en") }
                )
            }
        }
    }
}

@Composable
private fun LanguageOption(
    modifier: Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ElectricCyan else Color(0xFF202938),
            contentColor = if (selected) Obsidian else SoftWhite
        )
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = SoftWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(trailing, color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
    }
}

@Composable
private fun StatusTag(label: String) {
    Surface(color = Color(0xFF202938), shape = RoundedCornerShape(8.dp)) {
        Text(
            label,
            color = MutedText,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun FeatureCard(title: String, description: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = Color(0xFF173039),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(4.dp).height(42.dp)
            ) {}
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, color = SoftWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    description,
                    color = MutedText,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                stringResource(R.string.soon),
                color = ElectricCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }
    }
}

@Composable
private fun ShizukuConnectionStatus.badgeLabel(): String = when (this) {
    ShizukuConnectionStatus.CHECKING -> stringResource(R.string.badge_checking)
    ShizukuConnectionStatus.SERVICE_NOT_RUNNING -> stringResource(R.string.badge_service_off)
    ShizukuConnectionStatus.PERMISSION_REQUIRED -> stringResource(R.string.badge_grant_access)
    ShizukuConnectionStatus.REQUESTING_PERMISSION -> stringResource(R.string.badge_waiting)
    ShizukuConnectionStatus.READY -> stringResource(R.string.badge_shizuku_ready)
    ShizukuConnectionStatus.UNSUPPORTED_API -> stringResource(R.string.badge_update_required)
    ShizukuConnectionStatus.ERROR -> stringResource(R.string.badge_connection_error)
}

@Composable
private fun ShizukuConnectionState.connectionDetail(): String {
    val privilegeLabel = when (privilege) {
        ShizukuPrivilege.ADB -> stringResource(R.string.privilege_adb)
        ShizukuPrivilege.ROOT -> stringResource(R.string.privilege_root)
        ShizukuPrivilege.UNKNOWN, null -> stringResource(R.string.privilege_unknown)
    }
    val uidLabel = serverUid?.let { stringResource(R.string.uid_value, it) }
        ?: stringResource(R.string.uid_unavailable)
    val apiLabel = serverVersion?.let { stringResource(R.string.api_value, it) }
        ?: stringResource(R.string.api_unavailable)
    return stringResource(R.string.connection_detail, privilegeLabel, uidLabel, apiLabel)
}

@Composable
private fun CommandEngineError.localizedMessage(): String = when (this) {
    CommandEngineError.SHIZUKU_UNAVAILABLE -> stringResource(R.string.command_error_shizuku)
    CommandEngineError.BIND_FAILED -> stringResource(R.string.command_error_bind)
    CommandEngineError.PERMISSION_REQUIRED -> stringResource(R.string.command_error_permission)
    CommandEngineError.BIND_TIMEOUT -> stringResource(R.string.command_error_timeout)
    CommandEngineError.SERVICE_DISCONNECTED -> stringResource(R.string.command_error_disconnected)
    CommandEngineError.REMOTE_CALL_FAILED -> stringResource(R.string.command_error_remote)
}

private fun ShizukuConnectionStatus.badgeColor(): Color = when (this) {
    ShizukuConnectionStatus.READY -> Emerald
    ShizukuConnectionStatus.CHECKING,
    ShizukuConnectionStatus.PERMISSION_REQUIRED,
    ShizukuConnectionStatus.REQUESTING_PERMISSION -> Amber
    ShizukuConnectionStatus.SERVICE_NOT_RUNNING,
    ShizukuConnectionStatus.UNSUPPORTED_API,
    ShizukuConnectionStatus.ERROR -> Color(0xFFFF6470)
}
