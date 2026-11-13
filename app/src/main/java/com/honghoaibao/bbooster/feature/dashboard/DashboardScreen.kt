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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honghoaibao.bbooster.ui.theme.Amber
import com.honghoaibao.bbooster.ui.theme.DarkSlate
import com.honghoaibao.bbooster.ui.theme.ElectricCyan
import com.honghoaibao.bbooster.ui.theme.MutedText
import com.honghoaibao.bbooster.ui.theme.Obsidian
import com.honghoaibao.bbooster.ui.theme.SlateBorder
import com.honghoaibao.bbooster.ui.theme.SoftWhite

@Composable
fun DashboardScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Header()

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

        ShizukuStatusCard()

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading(
                title = "DEVICE OVERVIEW",
                trailing = "PHASE 1"
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
                text = "FOUNDATION BUILD  ·  v0.1.0",
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
private fun Header() {
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
            color = Color(0xFF302819),
            shape = CircleShape,
            border = BorderStroke(1.dp, Color(0xFF66502A))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Surface(
                    modifier = Modifier.width(7.dp).height(7.dp),
                    color = Amber,
                    shape = CircleShape
                ) {}
                Text(
                    text = "NOT CONNECTED",
                    color = Amber,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun ShizukuStatusCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, SlateBorder),
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
                text = "Privilege engine is not active",
                color = SoftWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Shizuku setup and permission handling will be added in Phase 2. No system settings or other apps are modified in this build.",
                color = MutedText,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
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
