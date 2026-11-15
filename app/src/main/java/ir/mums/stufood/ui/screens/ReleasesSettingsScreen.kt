package ir.mums.stufood.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.mums.stufood.BuildConfig
import ir.mums.stufood.data.AppLogs
import ir.mums.stufood.data.ReleaseNotes
import ir.mums.stufood.data.UpdateManager
import ir.mums.stufood.ui.components.GitHubLinkCard
import ir.mums.stufood.ui.components.HapticType
import ir.mums.stufood.ui.components.ObtainiumLinkCard
import ir.mums.stufood.ui.components.rememberHapticFeedback
import ir.mums.stufood.util.AppLinks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val hapticEnabled by vm.hapticFeedbackEnabled.collectAsState(initial = true)
    val haptic = rememberHapticFeedback(enabled = hapticEnabled)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Releases & Issues") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic(HapticType.CLICK)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------- Section 1: Updates & Releases ----------
            SectionHeader("Updates & Releases")

            ObtainiumLinkCard(
                title = "Obtainium",
                subtitle = "Automatic updates · step-by-step setup guide",
                hapticEnabled = hapticEnabled
            ) { AppLinks.open(context, AppLinks.OBTAINIUM_GUIDE) }

            UpdateCard(vm = vm, haptic = haptic)

            ReleaseHistoryCard(vm = vm, hapticEnabled = hapticEnabled, haptic = haptic)

            Spacer(Modifier.height(8.dp))

            // ---------- Section 2: Bugs, Logs & Issues ----------
            SectionHeader("Bugs, Logs & Issues")

            GitHubLinkCard(
                title = "Report bugs",
                subtitle = "Open an issue on GitHub",
                hapticEnabled = hapticEnabled
            ) { AppLinks.open(context, AppLinks.ISSUES) }

            LogsCard(vm = vm, haptic = haptic)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

/** Slim update box: the auto-check switch lives in the header, top right. */
@Composable
private fun UpdateCard(vm: SettingsViewModel, haptic: (HapticType) -> Unit) {
    val state by vm.updateState.collectAsState()
    val auto by vm.autoUpdateEnabled.collectAsState(initial = true)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Updates", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "Auto-check",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = auto,
                    onCheckedChange = { haptic(HapticType.TICK); vm.setAutoUpdateEnabled(it) }
                )
            }

            val s = state
            val status = when (s) {
                is UpdateManager.State.Idle -> "Current version: ${BuildConfig.VERSION_NAME}"
                is UpdateManager.State.Checking -> "Checking GitHub…"
                is UpdateManager.State.UpToDate -> "You're on the latest version (${BuildConfig.VERSION_NAME})."
                is UpdateManager.State.Available -> "Version ${s.release.versionName} is available."
                is UpdateManager.State.Error -> s.message
            }
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (s is UpdateManager.State.Error) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (s is UpdateManager.State.Available && s.release.notes.isNotBlank()) {
                Text(
                    s.release.notes, maxLines = 6,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    haptic(HapticType.CLICK)
                    when (s) {
                        is UpdateManager.State.Available -> vm.openUpdateInBrowser(s.release)
                        else -> vm.checkForUpdates()
                    }
                },
                enabled = s !is UpdateManager.State.Checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when (s) {
                        is UpdateManager.State.Available -> "Download ${s.release.versionName} in browser"
                        is UpdateManager.State.Checking -> "Checking…"
                        else -> "Check for updates"
                    }
                )
            }
        }
    }
}

/**
 * Collapsed by default. Holds the GitHub Releases card and the patch notes
 * (bundled from the repo's release-notes folder).
 */
@Composable
private fun ReleaseHistoryCard(
    vm: SettingsViewModel,
    hapticEnabled: Boolean,
    haptic: (HapticType) -> Unit
) {
    val context = LocalContext.current
    val noAnimations by vm.disableAllAnimations.collectAsState(initial = false)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val arrow by animateFloatAsState(
        if (expanded) 180f else 0f,
        animationSpec = tween(180),
        label = "releaseHistoryArrow"
    )

    val notes by produceState<List<ReleaseNotes.Entry>>(initialValue = emptyList()) {
        value = ReleaseNotes.load(context)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic(HapticType.CLICK)
                        expanded = !expanded
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Release history", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "GitHub Releases and patch notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(if (noAnimations) (if (expanded) 180f else 0f) else arrow)
                )
            }

            val content: @Composable () -> Unit = {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GitHubLinkCard(
                        title = "GitHub Releases",
                        subtitle = "Get the latest APK and release notes",
                        hapticEnabled = hapticEnabled
                    ) { AppLinks.open(context, AppLinks.LATEST_RELEASE) }

                    Text(
                        "Patch notes",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (notes.isEmpty()) {
                        Text(
                            "No patch notes bundled with this build.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        notes.forEachIndexed { index, entry ->
                            PatchNotesEntry(entry, startExpanded = index == 0, haptic = haptic)
                        }
                    }
                }
            }

            if (noAnimations) {
                if (expanded) content()
            } else {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(150)) + fadeOut(tween(100))
                ) { content() }
            }
        }
    }
}

/** One version's notes. Tap the header to fold it; the newest starts open. */
@Composable
private fun PatchNotesEntry(entry: ReleaseNotes.Entry, startExpanded: Boolean, haptic: (HapticType) -> Unit) {
    var open by rememberSaveable(entry.version) { mutableStateOf(startExpanded) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.clickable { haptic(HapticType.TICK); open = !open }.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Bananite ${entry.version}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(if (open) 180f else 0f)
                )
            }
            if (open) {
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.lines.forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Live view of the app's own log output. More tools will be added here over time. */
@Composable
private fun LogsCard(vm: SettingsViewModel, haptic: (HapticType) -> Unit) {
    val context = LocalContext.current
    val logs by vm.logs.collectAsState()
    val level by vm.logLevel.collectAsState()
    val logScroll = rememberScrollState()

    LaunchedEffect(Unit) { vm.refreshLogs() }
    // Keep the newest lines in view once the new text has been laid out.
    LaunchedEffect(logs) {
        withFrameNanos { }
        logScroll.scrollTo(logScroll.maxValue)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("App logs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { haptic(HapticType.CLICK); vm.refreshLogs() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh logs")
                }
                IconButton(onClick = {
                    haptic(HapticType.CLICK)
                    copyLogs(context, logs)
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLogs.Level.entries.forEach { l ->
                    FilterChip(
                        selected = level == l,
                        onClick = { haptic(HapticType.TICK); vm.setLogLevel(l) },
                        label = { Text(l.label) }
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 120.dp, max = 320.dp)
                        .verticalScroll(logScroll)
                        .padding(12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = logs.ifBlank { "No log entries yet." },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                "Logs only cover this app session and are not saved. They can contain personal details, so review them before posting publicly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun copyLogs(context: Context, logs: String) {
    val header = "Bananite ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
        "${Build.MANUFACTURER} ${Build.MODEL}\n\n"
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Bananite logs", header + logs))
    Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
}
