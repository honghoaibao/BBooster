package com.honghoaibao.bbooster.feature.games

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.honghoaibao.bbooster.R
import com.honghoaibao.bbooster.core.game.GameLibraryError
import com.honghoaibao.bbooster.core.game.GameLibraryItem
import com.honghoaibao.bbooster.core.game.GameLibraryState
import com.honghoaibao.bbooster.core.game.LaunchableApp
import com.honghoaibao.bbooster.ui.theme.DarkSlate
import com.honghoaibao.bbooster.ui.theme.ElectricCyan
import com.honghoaibao.bbooster.ui.theme.MutedText
import com.honghoaibao.bbooster.ui.theme.Obsidian
import com.honghoaibao.bbooster.ui.theme.SlateBorder
import com.honghoaibao.bbooster.ui.theme.SoftWhite

@Composable
fun GameLibraryScreen(
    state: GameLibraryState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddGame: (String) -> Unit,
    onRemoveGame: (String) -> Unit,
    onLaunchGame: (String) -> Unit
) {
    var libraryQuery by remember { mutableStateOf("") }
    var pickerQuery by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    val filteredGames = state.games.filter { item ->
        item.label.contains(libraryQuery, ignoreCase = true) ||
            item.packageName.contains(libraryQuery, ignoreCase = true)
    }
    val filteredApps = state.availableApps.filter { app ->
        app.label.contains(pickerQuery, ignoreCase = true) ||
            app.packageName.contains(pickerQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.optimization_back), color = ElectricCyan)
            }
            Text(
                stringResource(R.string.game_library_title),
                color = SoftWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            stringResource(R.string.game_library_subtitle),
            color = MutedText,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    pickerQuery = ""
                    showPicker = true
                },
                enabled = !state.isLoading && state.availableApps.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    disabledContainerColor = DarkSlate
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(R.string.game_library_add_game),
                    color = Obsidian,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(
                onClick = onRefresh,
                enabled = !state.isLoading && !state.isRefreshing
            ) {
                Text(
                    stringResource(
                        if (state.isRefreshing) R.string.game_library_refreshing
                        else R.string.game_library_refresh
                    ),
                    color = ElectricCyan
                )
            }
        }

        OutlinedTextField(
            value = libraryQuery,
            onValueChange = { libraryQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.game_library_search)) }
        )

        state.error?.let { error ->
            val message = when (error) {
                GameLibraryError.LOAD_FAILED -> R.string.game_library_load_failed
                GameLibraryError.SAVE_FAILED -> R.string.game_library_save_failed
                GameLibraryError.REMOVE_FAILED -> R.string.game_library_remove_failed
            }
            Text(
                stringResource(message),
                color = MutedText,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }

        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ElectricCyan)
                }
            }
            state.games.isEmpty() -> {
                EmptyLibraryState(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    title = stringResource(R.string.game_library_empty_title),
                    detail = stringResource(R.string.game_library_empty_detail)
                )
            }
            filteredGames.isEmpty() -> {
                EmptyLibraryState(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    title = stringResource(R.string.game_library_search_empty),
                    detail = stringResource(R.string.game_library_search_empty_detail)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredGames, key = { it.packageName }) { game ->
                        GameLibraryCard(
                            game = game,
                            onLaunch = { onLaunchGame(game.packageName) },
                            onRemove = { onRemoveGame(game.packageName) }
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = {
                Text(
                    stringResource(R.string.game_library_picker_title),
                    color = SoftWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = pickerQuery,
                        onValueChange = { pickerQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.game_library_picker_search)) }
                    )
                    if (filteredApps.isEmpty()) {
                        Text(
                            stringResource(R.string.game_library_no_available_apps),
                            color = MutedText,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                AvailableAppRow(
                                    app = app,
                                    onAdd = {
                                        onAddGame(app.packageName)
                                        showPicker = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.game_library_cancel), color = ElectricCyan)
                }
            },
            containerColor = DarkSlate
        )
    }
}

@Composable
private fun GameLibraryCard(
    game: GameLibraryItem,
    onLaunch: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GameAppIcon(game.packageName, game.label, game.isInstalled)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    game.label,
                    color = SoftWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    stringResource(
                        if (game.isInstalled) R.string.game_library_installed
                        else R.string.game_library_not_installed
                    ),
                    color = MutedText,
                    fontSize = 11.sp
                )
                Text(game.packageName, color = MutedText, fontSize = 9.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onLaunch, enabled = game.isInstalled) {
                    Text(
                        stringResource(R.string.game_library_open),
                        color = if (game.isInstalled) ElectricCyan else MutedText
                    )
                }
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.game_library_remove), color = MutedText)
                }
            }
        }
    }
}

@Composable
private fun AvailableAppRow(app: LaunchableApp, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GameAppIcon(app.packageName, app.label, true)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                app.label,
                color = SoftWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(app.packageName, color = MutedText, fontSize = 9.sp, maxLines = 1)
        }
        TextButton(onClick = onAdd) {
            Text(stringResource(R.string.game_library_add_action), color = ElectricCyan)
        }
    }
}

@Composable
private fun GameAppIcon(packageName: String, label: String, installed: Boolean) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName, installed) {
        if (!installed) {
            null
        } else {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
                drawable.draw(Canvas(bitmap))
                bitmap.asImageBitmap()
            }.getOrNull()
        }
    }

    if (iconBitmap != null) {
        Image(bitmap = iconBitmap, contentDescription = label, modifier = Modifier.size(48.dp))
    } else {
        Surface(
            color = DarkSlate,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    modifier: Modifier = Modifier,
    title: String,
    detail: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MutedText, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

private const val ICON_SIZE_PX = 96
