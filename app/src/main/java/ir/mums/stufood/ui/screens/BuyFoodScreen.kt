package ir.mums.stufood.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.mums.stufood.data.StufoodRepository
import ir.mums.stufood.ui.components.HapticType
import ir.mums.stufood.ui.components.LoadingDots
import ir.mums.stufood.ui.components.MultiScriptText
import ir.mums.stufood.ui.components.rememberHapticFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyFoodScreen(
    onBack: () -> Unit,
    vm: BuyFoodViewModel = viewModel()
) {
    LaunchedEffect(Unit) { vm.load() }

    val state by vm.state.collectAsState()
    val error by vm.error.collectAsState()
    val hapticEnabled by vm.hapticFeedbackEnabled.collectAsState()
    val haptic = rememberHapticFeedback(enabled = hapticEnabled)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it.message) } }

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
            // ---- Filters ----
            OutlinedTextField(
                value = state.date,
                onValueChange = vm::setDate,
                label = { Text("Date (e.g. 1405/07/09)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            var expanded by remember { mutableStateOf(false) }
            val mealLabel = page.mealOptions.firstOrNull { it.second == state.meal }?.first.orEmpty()
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = mealLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Meal") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
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

            Button(
                onClick = { haptic(HapticType.CLICK); vm.search() },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else Text("Search")
            }

            // ---- Foods you can receive (grdFoods) ----
            SectionTitle("Available to receive")
            if (page.availableFoods.isEmpty()) EmptyHint()
            page.availableFoods.forEach { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

            // ---- Waiting list (grdAll) ----
            SectionTitle("Waiting for exchange")
            if (page.waitingFoods.isEmpty()) EmptyHint()
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
            Spacer(Modifier.height(16.dp))
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