package ir.mums.stufood.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.mums.stufood.ui.components.HapticType
import ir.mums.stufood.ui.components.NotificationSetup
import ir.mums.stufood.ui.components.oemBackgroundTip
import ir.mums.stufood.ui.components.rememberHapticFeedback
import ir.mums.stufood.ui.components.rememberNotificationSetup

/**
 * Settings > Notifications. One section for now ("Buy food"); more sections can be added below it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val background by vm.runInBackground.collectAsState(initial = false)
    val live by vm.liveNotifications.collectAsState(initial = false)
    val hapticEnabled by vm.hapticFeedbackEnabled.collectAsState(initial = true)
    val haptic = rememberHapticFeedback(enabled = hapticEnabled)
    val setup = rememberNotificationSetup()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = { haptic(HapticType.CLICK); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Section: Buy food ----
            Text(
                "Buy food",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                ToggleRow(
                    icon = Icons.Default.Autorenew,
                    title = "Run in the background",
                    subtitle = "Keep auto-search running when the screen is off or you switch to another app.",
                    checked = background,
                    onCheckedChange = { on ->
                        haptic(HapticType.TICK)
                        vm.setRunInBackground(on)
                        if (on) setup.run(background = true, live = false)
                    }
                )
                if (background) {
                    if (!setup.batteryUnrestricted) {
                        StatusLine(
                            "Battery is restricted, so the phone may pause searches.",
                            "Fix"
                        ) { setup.run(background = true, live = false) }
                    }
                    if (!setup.notificationsGranted) {
                        StatusLine(
                            "Notification permission is off, so the \"running\" indicator is hidden.",
                            "Allow"
                        ) { setup.run(background = true, live = false) }
                    }
                    oemBackgroundTip()?.let { InfoLine(it) }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                ToggleRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Live notifications",
                    subtitle = "Show a live status with a timer and a Stop button: lock screen, status bar, " +
                        "and the Now Bar / Super Island where supported.",
                    checked = live,
                    onCheckedChange = { on ->
                        haptic(HapticType.TICK)
                        vm.setLiveNotifications(on)
                        if (on) setup.run(background = false, live = true)
                    }
                )
                if (live) LiveStatus(setup)
            }
            Text(
                "Both are off by default. With both off, auto-search works as before: it needs the screen on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun LiveStatus(setup: NotificationSetup) {
    if (!setup.notificationsGranted) {
        StatusLine("Notification permission is off.", "Allow") { setup.run(background = false, live = true) }
    }
    if (!setup.liveUpdatesSupported) {
        InfoLine("Live Updates (status chip, Now Bar, Super Island) need Android 16. On this phone you get a normal ongoing notification.")
    } else if (!setup.liveUpdatesAllowed) {
        StatusLine("Live updates are switched off for Bananite.", "Open") { setup.run(background = false, live = true) }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusLine(text: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
