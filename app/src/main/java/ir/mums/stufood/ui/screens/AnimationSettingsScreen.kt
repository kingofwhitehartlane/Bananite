package ir.mums.stufood.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val animationType by vm.animationType.collectAsState(initial = "smooth")
    val bounciness by vm.bounciness.collectAsState(initial = "medium")
    val creditTransitionType by vm.creditTransitionType.collectAsState(initial = "fade")
    
    val welcomeNameEnabled by vm.welcomeNameEnabled.collectAsState(initial = true)
    val disableAll by vm.disableAllAnimations.collectAsState(initial = false)

    // Derived states to override UI and logic when "Disable all animations" is ON
    val effectiveAnimationType by remember(animationType, disableAll) {
        derivedStateOf { if (disableAll) "none" else animationType }
    }
    val effectiveBounciness by remember(bounciness, disableAll) {
        derivedStateOf { if (disableAll) "none" else bounciness }
    }
    val effectiveCreditTransition by remember(creditTransitionType, disableAll) {
        derivedStateOf { if (disableAll) "fade" else creditTransitionType }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Animations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 0. GLOBAL DISABLE SWITCH
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "Disable all animations", style = MaterialTheme.typography.titleMedium)
                }
                Switch(
                    checked = disableAll,
                    onCheckedChange = { vm.setDisableAllAnimations(it) }
                )
            }
            HorizontalDivider()

            // 1. Welcome Name
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(text = "Welcome name", style = MaterialTheme.typography.titleMedium)
                    }
                    // This toggle remains active even if disableAll is true
                    Switch(
                        checked = welcomeNameEnabled,
                        onCheckedChange = { vm.setWelcomeNameEnabled(it) }
                    )
                }
                
                val options = listOf("None" to "none", "Smooth" to "smooth", "Bounce" to "bounce")
                val selectedIndex = options.indexOfFirst { it.second == effectiveAnimationType }.coerceAtLeast(0)
                
                // NEW: The selector is only enabled if animations aren't globally disabled 
                // AND the welcome name itself is enabled.
                val isAnimationSelectorEnabled = !disableAll && welcomeNameEnabled

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (label, value) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { if (isAnimationSelectorEnabled) vm.setAnimationType(value) },
                            selected = selectedIndex == index,
                            enabled = isAnimationSelectorEnabled, // UPDATED: Disables the buttons if welcomeNameEnabled is false
                            label = { Text(label) }
                        )
                    }
                }
            }

            // 2. Bounciness (Using a Slider with 4 discrete steps)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Animation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "Bounciness", style = MaterialTheme.typography.titleMedium)
                }
                
                val bounceLevels = listOf("none", "low", "medium", "high")
                val bounceIndex = bounceLevels.indexOf(effectiveBounciness).coerceIn(0, 3)
                var sliderPosition by remember(bounceIndex) { mutableFloatStateOf(bounceIndex.toFloat()) }
                
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = { 
                        val newIndex = sliderPosition.toInt().coerceIn(0, 3)
                        if (!disableAll) vm.setBounciness(bounceLevels[newIndex]) 
                    },
                    valueRange = 0f..3f,
                    steps = 2, // Creates exactly 4 ticks: 0, 1, 2, 3
                    enabled = !disableAll
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("None", style = MaterialTheme.typography.labelSmall)
                    Text("Low", style = MaterialTheme.typography.labelSmall)
                    Text("Medium", style = MaterialTheme.typography.labelSmall)
                    Text("High", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 3. Credit Balance Transition
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "Credit Balance Transition", style = MaterialTheme.typography.titleMedium)
                }
                
                val creditOptions = listOf("Fade" to "fade", "Morph" to "morph")
                val creditSelectedIndex = creditOptions.indexOfFirst { it.second == effectiveCreditTransition }.coerceAtLeast(0)
                
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    creditOptions.forEachIndexed { index, (label, value) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = creditOptions.size),
                            onClick = { if (!disableAll) vm.setCreditTransitionType(value) },
                            selected = creditSelectedIndex == index,
                            enabled = !disableAll,
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}