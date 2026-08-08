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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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

/**
 * Reservation screen.
 *
 * The set of days, whether each day is reservable, and whether the week-nav buttons
 * are usable are all read fresh from whatever the server just sent — none of it is
 * assumed to be a fixed shape, since the site adds/removes days and locks/unlocks
 * them (cutoffs, already-reserved, admin changes, etc.) on its own schedule.
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
    val selectedMeal by vm.selectedMeal.collectAsState()
    val daySelections by vm.daySelections.collectAsState()

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbarHost.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.load() }

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
                        Spacer(Modifier.padding(8.dp))
                        Text("Loading…")
                    }
                }
                is ReservationUiState.Working -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(status ?: "Working…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                is ReservationUiState.Ready -> {
                    val page = s.page
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ---- Meal + week navigation ----
                        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Meal & week", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(12.dp))

                                if (page.mealOptions.isNotEmpty()) {
                                    DropdownField(
                                        label = "Meal",
                                        options = page.mealOptions,
                                        selectedValue = selectedMeal,
                                        onSelected = {
                                            vm.updateMeal(it)
                                            vm.applyMeal()
                                        }
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }

                                // "هفته قبل" (last week) / "هفته بعد" (next week). Each
                                // button only shows as enabled when the server's page
                                // currently allows it — it may be absent entirely (e.g.
                                // no earlier week to go back to) or present but greyed
                                // out (e.g. can't go further back/forward), and that can
                                // change from one page load to the next.
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { vm.lastWeek() },
                                        enabled = page.lastWeek.isUsable,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = null)
                                        Text("هفته قبل")
                                    }
                                    OutlinedButton(
                                        onClick = { vm.nextWeek() },
                                        enabled = page.nextWeek.isUsable,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("هفته بعد")
                                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                                    }
                                }
                                if (!page.nextWeek.exists && !page.lastWeek.exists) {
                                    Text(
                                        "No week-navigation buttons on this page.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // ---- Per-day dropdowns ----
                        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Days this week", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "Pick an option for each day, then reserve them all at once — " +
                                        "or reserve a single day with its own button. Locked/closed " +
                                        "days are shown but can't be changed.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))

                                page.days.forEachIndexed { idx, day ->
                                    if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    DayRow(
                                        day = day,
                                        selectedValue = daySelections[day.fieldName] ?: day.currentValue,
                                        onSelected = { vm.updateDaySelection(day.fieldName, it) },
                                        onReserveThisDay = { vm.reserveDay(day.fieldName) }
                                    )
                                }

                                if (page.days.isEmpty()) {
                                    Text(
                                        "No reservable days found on this page.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(Modifier.height(16.dp))
                                val anyUsable = page.days.any { it.isUsable }
                                Button(
                                    onClick = { vm.reserveAllDays() },
                                    enabled = anyUsable,
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                ) {
                                    Text("Reserve all days")
                                }
                                if (page.days.isNotEmpty() && !anyUsable) {
                                    Text(
                                        "All days are currently locked.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayRow(
    day: StufoodRepository.DayInfo,
    selectedValue: String,
    onSelected: (String) -> Unit,
    onReserveThisDay: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(day.dayLabel, style = MaterialTheme.typography.titleMedium)
            if (!day.isUsable) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.height(16.dp)
                )
            }
        }
        if (!day.isUsable) {
            Text(
                day.lockedReason ?: "Not available for reservation right now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    DropdownField(
                        label = "Cafeteria / diet",
                        options = day.dietOptions,
                        selectedValue = selectedValue,
                        onSelected = onSelected
                    )
                }
                OutlinedButton(onClick = onReserveThisDay) {
                    Text("Reserve")
                }
            }
        }
    }
}

/**
 * Dropdown bound to option *values* (not just labels) so the caller can look the
 * label back up without guessing — meal/diet option text and value can differ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<Pair<String, String>>, // label to value
    selectedValue: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.second == selectedValue }?.first ?: selectedValue

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
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