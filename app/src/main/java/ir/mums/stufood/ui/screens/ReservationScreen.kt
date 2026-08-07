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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

/**
 * Reservation screen.
 *
 * Shows a one-tap "reserve next week" button + the parsed meal options so the user can
 * pick non-defaults. While the operation runs, the status text shows what step we're on
 * (loading page, selecting meal, going to next week, day X/Y).
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
    val selectedDiet by vm.selectedDiet.collectAsState()

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
            when {
                state is ReservationUiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.height(24.dp))
                        Spacer(Modifier.padding(8.dp))
                        Text("Loading…")
                    }
                }
                state is ReservationUiState.Working -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = status ?: "Working…",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                state is ReservationUiState.Ready -> {
                    val page = (state as ReservationUiState.Ready).page
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ---- Quick action card ----
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Reserve next week",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    "One tap runs the full script: ناهار + سلف پردیس + first " +
                                    "radio, for every day.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))

                                // Meal dropdown — exposes the options parsed from the page
                                DropdownField(
                                    label = "Meal",
                                    options = page.mealOptions.map { it.first },
                                    selected = selectedMeal,
                                    onSelected = vm::updateMeal
                                )
                                Spacer(Modifier.height(8.dp))

                                // Diet dropdown (visible text used as the search key when
                                // matching against each day's parsed options).
                                DropdownField(
                                    label = "Cafeteria (diet)",
                                    options = page.days.firstOrNull()?.dietOptions?.map { it.first }
                                        ?: listOf("سلف پردیس"),
                                    selected = selectedDiet,
                                    onSelected = vm::updateDiet
                                )

                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { vm.reserveWeek() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Text("Reserve week")
                                }
                            }
                        }

                        // ---- Parsed state ----
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Parsed page state",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Meal options: ${page.mealOptions.map { it.first }}")
                                Text("Days found: ${page.days.size}")
                                page.days.forEach { day ->
                                    Text(
                                        "  • ${day.dayLabel} — diets: " +
                                        day.dietOptions.joinToString { it.first },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (page.nextWeekBtnName == null) {
                                    Text(
                                        "  (Next-week button not found — you may already " +
                                        "be on next week.)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                state is ReservationUiState.Idle -> {
                    // Initial state — nothing to show yet, load() was called by LaunchedEffect.
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { Text("Loading…") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
