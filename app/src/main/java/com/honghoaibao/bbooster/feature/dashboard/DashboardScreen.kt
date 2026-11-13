package com.honghoaibao.bbooster.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onRequestShizukuPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
    onRefreshShizuku: () -> Unit
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
                text = "Your gaming space.",
                color = SoftWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "A clear view of your device, games, and boost tools.",
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

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(
                title = "DEVICE OVERVIEW",
                trailing = "PHASE 2"
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
                        text = "Telemetry is not connected yet",
                        color = SoftWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "RAM and battery readings will appear after the device-monitor module is implemented.",
                        color = MutedText,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusTag(label = "RAM · Pending")
                        StatusTag(label = "Battery · Pending")
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(title = "BOOST TOOLS", trailing = "COMING NEXT")
            FeatureCard(
                title = "Game library",
                description = "Choose and organize the games you want to manage."
            )
            FeatureCard(
                title = "Optimization engine",
                description = "Review safe, device-supported optimizations before applying them."
            )
            FeatureCard(
                title = "Session recovery",
                description = "Track temporary changes and restore the original device settings."
            )
        }

        Surface(
            color = Color(0xFF111722),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, SlateBorder)
        ) {
            Text(
                text = "SHIZUKU FOUNDATION  ·  v0.2.0",
                color = MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
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
                text = "BBooster",
                color = SoftWhite,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "ANDROID GAME COMPANION",
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
                    text = state.status.badgeLabel(),
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
        ShizukuConnectionStatus.CHECKING -> "Checking Shizuku service"
        ShizukuConnectionStatus.SERVICE_NOT_RUNNING -> "Shizuku is not running"
        ShizukuConnectionStatus.PERMISSION_REQUIRED -> "Permission required"
        ShizukuConnectionStatus.REQUESTING_PERMISSION -> "Waiting for permission"
        ShizukuConnectionStatus.READY -> "Shizuku connection ready"
        ShizukuConnectionStatus.UNSUPPORTED_API -> "Shizuku version is too old"
        ShizukuConnectionStatus.ERROR -> "Connection check failed"
    }
    val description = state.message ?: when (state.status) {
        ShizukuConnectionStatus.CHECKING -> "Checking whether the Shizuku Binder is available."
        ShizukuConnectionStatus.SERVICE_NOT_RUNNING -> "Start Shizuku, or enable a compatible Sui service, then refresh this screen."
        ShizukuConnectionStatus.PERMISSION_REQUIRED -> "Shizuku is available. Grant BBooster access to continue."
        ShizukuConnectionStatus.REQUESTING_PERMISSION -> "Complete the permission prompt shown by Shizuku."
        ShizukuConnectionStatus.READY -> "BBooster can now detect the Shizuku service and permission state."
        ShizukuConnectionStatus.UNSUPPORTED_API -> "Update Shizuku and start the service again."
        ShizukuConnectionStatus.ERROR -> "Refresh the connection state and try again."
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
                text = "SHIZUKU CONNECTION",
                color = ElectricCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = title,
                color = SoftWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = MutedText,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            if (isReady) {
                Text(
                    text = state.connectionDetail(),
                    color = Emerald,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            when (state.status) {
                ShizukuConnectionStatus.PERMISSION_REQUIRED -> {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Obsidian
                        )
                    ) {
                        Text("Grant Shizuku permission", fontWeight = FontWeight.Bold)
                    }
                }

                ShizukuConnectionStatus.SERVICE_NOT_RUNNING,
                ShizukuConnectionStatus.UNSUPPORTED_API -> {
                    Button(
                        onClick = onOpenShizuku,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Obsidian
                        )
                    ) {
                        Text("Open Shizuku", fontWeight = FontWeight.Bold)
                    }
                }

                ShizukuConnectionStatus.ERROR -> {
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Obsidian
                        )
                    ) {
                        Text("Retry connection check", fontWeight = FontWeight.Bold)
                    }
                }

                ShizukuConnectionStatus.CHECKING,
                ShizukuConnectionStatus.REQUESTING_PERMISSION,
                ShizukuConnectionStatus.READY -> Unit
            }

            TextButton(
                onClick = onRefresh,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Refresh status", color = MutedText)
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    trailing: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = SoftWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = trailing,
            color = MutedText,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp
        )
    }
}

@Composable
private fun StatusTag(label: String) {
    Surface(
        color = Color(0xFF202938),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            color = MutedText,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String
) {
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
                Text(
                    text = title,
                    color = SoftWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = description,
                    color = MutedText,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "SOON",
                color = ElectricCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
        }
    }
}

private fun ShizukuConnectionStatus.badgeLabel(): String = when (this) {
    ShizukuConnectionStatus.CHECKING -> "CHECKING"
    ShizukuConnectionStatus.SERVICE_NOT_RUNNING -> "SERVICE OFF"
    ShizukuConnectionStatus.PERMISSION_REQUIRED -> "GRANT ACCESS"
    ShizukuConnectionStatus.REQUESTING_PERMISSION -> "WAITING"
    ShizukuConnectionStatus.READY -> "SHIZUKU READY"
    ShizukuConnectionStatus.UNSUPPORTED_API -> "UPDATE REQUIRED"
    ShizukuConnectionStatus.ERROR -> "CONNECTION ERROR"
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

private fun ShizukuConnectionState.connectionDetail(): String {
    val privilegeLabel = when (privilege) {
        ShizukuPrivilege.ADB -> "ADB / Shell"
        ShizukuPrivilege.ROOT -> "Root"
        ShizukuPrivilege.UNKNOWN, null -> "Unknown"
    }
    val uidLabel = serverUid?.let { "UID $it" } ?: "UID unavailable"
    val versionLabel = serverVersion?.let { "API $it" } ?: "API unavailable"
    return "$privilegeLabel · $uidLabel · $versionLabel"
}
