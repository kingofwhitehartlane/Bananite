package ir.mums.stufood.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.mums.stufood.data.StufoodRepository
import ir.mums.stufood.data.StufoodRepository.DayInfo
import ir.mums.stufood.data.StufoodRepository.DayStatus
import ir.mums.stufood.data.StufoodRepository.DietOption
import ir.mums.stufood.data.StufoodRepository.ReservationPage

/**
 * Reservation screen.
 *
 * Days, their state, and the week-nav buttons are all read fresh from whatever the
 * server just sent — none of it is assumed to be a fixed shape, since the site
 * adds/removes days and locks/unlocks them (cutoffs, already-reserved, admin
 * changes, published/unpublished menus, etc.) on its own schedule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationScreen(
    onBack: () -> Unit,
    vm: ReservationViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val status by vm.statusText.collectAsState()
    val error by vm.errorMessage.collectAsState()
    val pendingCancel by vm.pendingCancel.collectAsState()

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbarHost.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.load() }

    pendingCancel?.let { option ->
        AlertDialog(
            onDismissRequest = vm::dismissCancelRequest,
            title = { Text("\u06a9\u0646\u0633\u0644 \u0631\u0632\u0631\u0648") }, // "کنسل رزرو"
            text = { Text("\u0622\u06cc\u0627 \u0627\u0632 \u06a9\u0646\u0633\u0644 \u06a9\u0631\u062f\u0646 \u0642\u0630\u0627 \u0627\u0637\u0645\u06cc\u0646\u0627\u0646 \u062f\u0627\u0631\u06cc\u062f\u061f") },
            confirmButton = {
                TextButton(onClick = vm::confirmCancel) { Text("\u0628\u0644\u0647") } // بله
            },
            dismissButton = {
                TextButton(onClick = vm::dismissCancelRequest) { Text("\u062e\u06cc\u0631") } // خیر
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reserve Food") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            when (val s = state) {
                is ReservationUiState.Loading, ReservationUiState.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.height(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Loading…")
                    }
                }
                is ReservationUiState.Working -> {
                    ReservationContent(page = s.previousPage, dimmed = true, status = status, vm = vm)
                }
                is ReservationUiState.Ready -> {
                    ReservationContent(page = s.page, dimmed = false, status = status, vm = vm)
                }
            }
        }
    }
}

@Composable
private fun ReservationContent(
    page: ReservationPage,
    dimmed: Boolean,
    status: String?,
    vm: ReservationViewModel
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (dimmed && status != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp))
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- Balance box ----
        page.creditToman?.let { credit ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "\u0645\u0648\u062a\u062f\u06cc \u062d\u0633\u0627\u0628", // "موجودی حساب"
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        credit,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // ---- Meal + week navigation ----
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Meal & week", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                if (page.mealOptions.isNotEmpty()) {
                    DropdownField(
                        label = "Meal",
                        options = page.mealOptions,
                        selectedValue = page.selectedMeal,
                        enabled = !dimmed,
                        onSelected = { vm.selectMeal(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // Previous week / Next week / Jump to today. Each may be absent
                // entirely (e.g. no earlier week to go back to) or present but
                // greyed out — that can change from one page load to the next.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { vm.lastWeek() },
                        enabled = !dimmed && page.lastWeek.isUsable,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null)
                        Text("Previous week")
                    }
                    OutlinedButton(
                        onClick = { vm.today() },
                        enabled = !dimmed && page.today.isUsable,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.height(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Jump to today")
                    }
                    OutlinedButton(
                        onClick = { vm.nextWeek() },
                        enabled = !dimmed && page.nextWeek.isUsable,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Next week")
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        // ---- Per-day cards ----
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            page.days.forEach { day ->
                DayCard(day = day, enabled = !dimmed, vm = vm)
            }
            if (page.days.isEmpty()) {
                Text(
                    "No days found on this page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DayCard(day: DayInfo, enabled: Boolean, vm: ReservationViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // ---- Header: date + status badge/message ----
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(day.dateLabel, style = MaterialTheme.typography.titleMedium)
                if (day.isReadOnly || day.status == DayStatus.NOT_ALLOWED) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.height(16.dp)
                    )
                }
            }
            day.statusBadge?.let { badge ->
                AssistChip(onClick = {}, enabled = false, label = { Text(badge, style = MaterialTheme.typography.labelSmall) })
            }
            day.message?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when (day.status) {
                DayStatus.NO_FOOD_DEFINED -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "\u0641\u0630\u0627\u06cc\u06cc \u0628\u0631\u0627\u06cc \u0627\u06cc\u0646 \u0631\u0648\u0632 \u062a\u0639\u0631\u06cc\u0641 \u0646\u0634\u062f\u0647 \u0627\u0633\u062a", // غذایی برای این روز تعریف نشده است
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                DayStatus.NOT_ALLOWED, DayStatus.NOT_RESERVED -> {
                    Text(
                        day.selfLabel ?: day.message ?: "\u0646\u0627\u0645\u0634\u062e\u0635", // نامشخص
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                DayStatus.RECEIVED, DayStatus.NOT_RECEIVED -> {
                    day.selfLabel?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DietList(options = day.dietOptions, selectable = false, onSelect = {}, onCancel = {})
                }

                DayStatus.SELECT_CAFETERIA -> {
                    DropdownField(
                        label = "\u0627\u0646\u062a\u062e\u0627\u0628 \u0633\u0644\u0641", // انتخاب سلف
                        options = day.cafeteriaOptions,
                        selectedValue = day.selectedCafeteria ?: "0",
                        enabled = enabled,
                        onSelected = { vm.selectCafeteria(day, it) }
                    )
                }

                DayStatus.SELECT_DIET, DayStatus.RESERVED -> {
                    DropdownField(
                        label = "\u0633\u0644\u0641 / \u0631\u0648\u0632 \u0627\u0646\u062a\u062e\u0627\u0628\u06cc", // سلف / روز انتخابی
                        options = day.cafeteriaOptions,
                        selectedValue = day.selectedCafeteria ?: "0",
                        enabled = enabled,
                        onSelected = { vm.selectCafeteria(day, it) }
                    )
                    Spacer(Modifier.height(4.dp))
                    day.reservedOption?.let { reserved ->
                        Text(
                            "\u0627\u0646\u062a\u062e\u0627\u0628 \u0641\u0639\u0644\u06cc: ${reserved.label} \u2014 ${reserved.priceToman ?: ""}", // انتخاب فعلی: <name> — <price>
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    DietList(
                        options = day.dietOptions,
                        selectable = enabled,
                        onSelect = { vm.selectDiet(it) },
                        onCancel = { vm.requestCancel(it) }
                    )
                }

                DayStatus.UNKNOWN -> {
                    Text(
                        "Couldn't determine this day's state — check for site changes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DietList(
    options: List<DietOption>,
    selectable: Boolean,
    onSelect: (DietOption) -> Unit,
    onCancel: (DietOption) -> Unit
) {
    if (options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (selectable && !option.disabled) {
                            it.selectable(selected = option.checked, onClick = { onSelect(option) })
                        } else it
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = option.checked,
                    onClick = if (selectable && !option.disabled) { { onSelect(option) } } else null,
                    enabled = selectable && !option.disabled
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    option.priceToman?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (option.checked && option.cancelFieldName != null) {
                    IconButton(onClick = { onCancel(option) }) {
                        Icon(
                            Icons.Default.RemoveCircle,
                            contentDescription = "\u06a9\u0646\u0633\u0644 \u0631\u0632\u0631\u0648", // کنسل رزرو
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

/**
 * Dropdown bound to option *values* (not just labels) so the caller can look the
 * label back up without guessing — placeholder options like "\u0627\u0646\u062a\u062e\u0627\u0628 \u0646\u0645\u0627\u06cc\u06cc\u062f" /
 * "\u0627\u0646\u062a\u062e\u0627\u0628 \u0633\u0644\u0641" are shown at the top exactly like the site until something else is picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<Pair<String, String>>, // label to value
    selectedValue: String,
    enabled: Boolean,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.second == selectedValue }?.first ?: selectedValue

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (optLabel, optValue) ->
                DropdownMenuItem(
                    text = { Text(optLabel) },
                    onClick = {
                        onSelected(optValue)
                        expanded = false
                    }
                )
            }
        }
    }
}
