package com.bbooster.app.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbooster.app.R
import com.bbooster.app.shell.ConnectionState
import com.bbooster.app.shell.ShizukuConstants
import com.bbooster.app.ui.connection.ConnectionViewModel

/**
 * Phase 1: functional 3-step skeleton driven by [ConnectionState]. Visual polish and the
 * Wireless-Debugging pairing guide are refined in phase 4.
 */
@Composable
fun OnboardingScreen(
    onBack: () -> Unit,
    viewModel: ConnectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val context = LocalContext.current
    val step = OnboardingStep.from(state)

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            StepRow(
                index = 1,
                title = stringResource(R.string.onboarding_step1_title),
                description = stringResource(R.string.onboarding_step1_desc),
                status = statusOf(step, OnboardingStep.INSTALL_SHIZUKU),
            )
            StepRow(
                index = 2,
                title = stringResource(R.string.onboarding_step2_title),
                description = stringResource(R.string.onboarding_step2_desc),
                status = statusOf(step, OnboardingStep.START_SHIZUKU),
            )
            StepRow(
                index = 3,
                title = stringResource(R.string.onboarding_step3_title),
                description = stringResource(R.string.onboarding_step3_desc),
                status = statusOf(step, OnboardingStep.GRANT_PERMISSION),
            )

            if (state is ConnectionState.Failed) {
                Text(
                    text = (state as ConnectionState.Failed).reason,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            when (step) {
                OnboardingStep.INSTALL_SHIZUKU -> Button(onClick = { openUrl(context, ShizukuConstants.RELEASES_URL) }) {
                    Text(stringResource(R.string.onboarding_action_download))
                }
                OnboardingStep.START_SHIZUKU -> Button(onClick = { openShizuku(context) }) {
                    Text(stringResource(R.string.onboarding_action_open_shizuku))
                }
                OnboardingStep.GRANT_PERMISSION ->
                    if (state == ConnectionState.PermissionRequired) {
                        Button(onClick = viewModel::requestAccess) {
                            Text(stringResource(R.string.onboarding_action_grant))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.onboarding_permission_denied_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { openShizuku(context) }) {
                            Text(stringResource(R.string.onboarding_action_open_shizuku))
                        }
                    }
                OnboardingStep.DONE -> Button(onClick = onBack) {
                    Text(stringResource(R.string.onboarding_action_done))
                }
            }
        }
    }
}

private enum class StepStatus { DONE, CURRENT, PENDING }

private fun statusOf(current: OnboardingStep, step: OnboardingStep): StepStatus = when {
    step.ordinal < current.ordinal -> StepStatus.DONE
    step == current -> StepStatus.CURRENT
    else -> StepStatus.PENDING
}

@Composable
private fun StepRow(index: Int, title: String, description: String, status: StepStatus) {
    val marker = when (status) {
        StepStatus.DONE -> stringResource(R.string.onboarding_step_done)
        StepStatus.CURRENT -> stringResource(R.string.onboarding_step_current)
        StepStatus.PENDING -> stringResource(R.string.onboarding_step_pending)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$index. $title  ·  $marker",
            style = MaterialTheme.typography.titleMedium,
            color = if (status == StepStatus.PENDING) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openShizuku(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(ShizukuConstants.PACKAGE) ?: return
    runCatching { context.startActivity(intent) }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
