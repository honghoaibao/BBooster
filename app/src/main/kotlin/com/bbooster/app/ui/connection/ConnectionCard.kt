package com.bbooster.app.ui.connection

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bbooster.app.R
import com.bbooster.app.shell.ConnectionState
import com.bbooster.app.ui.theme.AccentGradient
import com.bbooster.app.ui.theme.StatusBad
import com.bbooster.app.ui.theme.StatusOk
import com.bbooster.app.ui.theme.StatusWarn

@Composable
fun ConnectionCard(
    state: ConnectionState,
    onSetupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(AccentGradient),
        )
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.connection_card_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(state.indicatorColor(), CircleShape),
                )
                Column {
                    Text(
                        text = stringResource(state.titleRes()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = state.detailText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state !is ConnectionState.Ready) {
                Button(onClick = onSetupClick) {
                    Text(stringResource(R.string.connection_action_setup))
                }
            }
        }
    }
}

@StringRes
private fun ConnectionState.titleRes(): Int = when (this) {
    ConnectionState.ShizukuNotInstalled -> R.string.connection_status_not_installed
    ConnectionState.ShizukuNotRunning -> R.string.connection_status_not_running
    ConnectionState.PermissionRequired -> R.string.connection_status_permission_required
    ConnectionState.PermissionDenied -> R.string.connection_status_permission_denied
    ConnectionState.Connecting -> R.string.connection_status_connecting
    is ConnectionState.Ready -> R.string.connection_status_ready
    is ConnectionState.Failed -> R.string.connection_status_failed
}

@Composable
private fun ConnectionState.detailText(): String = when (this) {
    ConnectionState.ShizukuNotInstalled -> stringResource(R.string.connection_detail_not_installed)
    ConnectionState.ShizukuNotRunning -> stringResource(R.string.connection_detail_not_running)
    ConnectionState.PermissionRequired -> stringResource(R.string.connection_detail_permission_required)
    ConnectionState.PermissionDenied -> stringResource(R.string.connection_detail_permission_denied)
    ConnectionState.Connecting -> stringResource(R.string.connection_detail_connecting)
    is ConnectionState.Ready -> stringResource(R.string.connection_detail_ready, uid)
    is ConnectionState.Failed -> reason
}

private fun ConnectionState.indicatorColor(): Color = when (this) {
    is ConnectionState.Ready -> StatusOk
    ConnectionState.Connecting -> StatusWarn
    ConnectionState.PermissionRequired -> StatusWarn
    else -> StatusBad
}
