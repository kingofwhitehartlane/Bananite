package ir.mums.stufood.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.mums.stufood.data.StufoodRepository
import ir.mums.stufood.ui.components.HapticType
import ir.mums.stufood.ui.components.JalaliDateField
import ir.mums.stufood.ui.components.LoadingDots
import ir.mums.stufood.ui.components.MultiScriptText
import ir.mums.stufood.ui.components.rememberHapticFeedback

// Kept so a playing ringtone isn't garbage-collected (which can cut the sound short).
private var activeRingtone: Ringtone? = null

private fun playNotificationSound(context: Context) {
    runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri) ?: return
        ringtone.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        activeRingtone?.stop()
        activeRingtone = ringtone
        ringtone.play()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyFoodScreen(
    onBack: () -> Unit,
    vm: BuyFoodViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Both toggles are OFF every time this menu is opened and again when it's left.
    LaunchedEffect(Unit) {
        vm.resetToggles()
        vm.load()
    }
    DisposableEffect(Unit) { onDispose { vm.resetToggles() } }

    val state by vm.state.collectAsState()
    val error by vm.error.collectAsState()
    val hapticEnabled by vm.hapticFeedbackEnabled.collectAsState()
    val haptic = rememberHapticFeedback(enabled = hapticEnabled)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it.message) } }

    // Phone's default notification sound when food shows up.
    LaunchedEffect(Unit) {
        vm.foodAvailable.collect {
            haptic(HapticType.SUCCESS)
            playNotificationSound(context)
        }
    }

    // Keep the screen awake while auto-refreshing so the loop and sound keep working.
    DisposableEffect(state.autoRefresh) {
        view.keepScreenOn = state.autoRefresh
        onDispose { view.keepScreenOn = false }
    }

    // Countdown ring around the auto-refresh toggle.
    val ring = remember { Animatable(0f) }
    LaunchedEffect(state.refreshTick, state.autoRefresh) {
        if (state.autoRefresh) {
            ring.snapTo(0f)
            ring.animateTo(
                1f,
                tween(BuyFoodViewModel.AUTO_REFRESH_INTERVAL_MS.toInt(), easing = LinearEasing)
            )
        } else {
            ring.snapTo(0f)
        }
    }

    var pending by remember { mutableStateOf<StufoodRepository.ExchangeableFood?>(null) }
    pending?.let { row ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Receive this food?") },
            text = { MultiScriptText("${row.food} — ${row.self}") },
            confirmButton = {
                TextButton(onClick = {
                    haptic(HapticType.SUCCESS)
                    vm.receive(row)
                    pending = null
                }) { Text("Receive") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buy Food") },
                navigationIcon = {
                    IconButton(onClick = { haptic(HapticType.CLICK); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val page = state.page
        if (page == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.loading) LoadingDots()
                else Button(onClick = { vm.load(force = true) }) { Text("Retry") }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Filters: date (half) + meal (half) on one row ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                JalaliDateField(
                    value = state.date,
                    onValueChange = vm::setDate,
                    modifier = Modifier.weight(1f),
                    label = "Date",
                    haptic = haptic
                )

                var expanded by remember { mutableStateOf(false) }
                val mealLabel = page.mealOptions.firstOrNull { it.second == state.meal }?.first.orEmpty()
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = mealLabel,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        isError = state.mealError,
                        label = { Text("Meal") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        supportingText = {
                            if (state.mealError) {
                                MultiScriptText(
                                    "وعده را انتخاب نمایید",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        page.mealOptions.forEach { (label, value) ->
                            DropdownMenuItem(
                                text = { MultiScriptText(label) },
                                onClick = {
                                    haptic(HapticType.TICK)
                                    vm.setMeal(value)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // ---- Search | auto-refresh (magnifier) | sound (bell) ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { haptic(HapticType.CLICK); vm.search() },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else Text("Search")
                }

                CircleToggle(
                    checked = state.autoRefresh,
                    onCheckedChange = {
                        haptic(HapticType.TICK)
                        vm.setAutoRefresh(it)
                    },
                    icon = Icons.Default.Search,
                    description = "Auto refresh",
                    ringProgress = if (state.autoRefresh) ring.value else null
                )

                CircleToggle(
                    checked = state.soundEnabled,
                    onCheckedChange = {
                        haptic(HapticType.TICK)
                        vm.setSoundEnabled(it)
                    },
                    icon = Icons.Default.Notifications,
                    description = "Sound when food is available"
                )
            }

            // Food cards are right-to-left; section titles/hints stay as they were.
            // ---- Foods you can receive (grdFoods) ----
            SectionTitle("Available to receive")
            if (page.availableFoods.isEmpty()) EmptyHint()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    page.availableFoods.forEach { row ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                MultiScriptText(row.food, style = MaterialTheme.typography.titleMedium)
                                MultiScriptText(
                                    listOf(row.meal, row.menu, row.self).filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (row.receive != null) {
                                    Button(
                                        onClick = { haptic(HapticType.CLICK); pending = row },
                                        enabled = !state.busy,
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) { Text("Receive") }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Waiting list (grdAll) ----
            SectionTitle("Waiting for exchange")
            if (page.waitingFoods.isEmpty()) EmptyHint()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    page.waitingFoods.forEach { row ->
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    MultiScriptText(row.food, style = MaterialTheme.typography.titleSmall)
                                    MultiScriptText(
                                        "${row.date} · ${row.day} · ${row.meal} · ${row.self}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                row.stock?.let {
                                    Text("×$it", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Circular on/off button: coloured when on, greyed out when off. When [ringProgress]
 * is non-null a thin ring fills around it (used as the auto-refresh countdown).
 */
@Composable
private fun CircleToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    ringProgress: Float? = null
) {
    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        FilledIconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(icon, contentDescription = description)
        }
        if (ringProgress != null) {
            CircularProgressIndicator(
                progress = { ringProgress },
                modifier = Modifier.size(42.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun EmptyHint() = Text(
    "Nothing to show right now.",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)