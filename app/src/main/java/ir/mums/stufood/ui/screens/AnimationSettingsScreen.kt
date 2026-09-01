// app/src/main/java/ir/mums/stufood/ui/screens/AnimationSettingsScreen.kt
package ir.mums.stufood.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val animationType by vm.animationType.collectAsState(initial = "smooth")
    val bounciness by vm.bounciness.collectAsState(initial = "medium")
    val creditTransitionType by vm.creditTransitionType.collectAsState(initial = "fade")

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
            // 1. Welcome Name Animation
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Animation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "Welcome Name Animation", style = MaterialTheme.typography.titleMedium)
                }
                val options = listOf("Bounce" to "bounce", "Smooth" to "smooth")
                val selectedIndex = options.indexOfFirst { it.second == animationType }.coerceAtLeast(0)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (label, value) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { vm.setAnimationType(value) },
                            selected = selectedIndex == index,
                            label = { Text(label) }
                        )
                    }
                }
            }

            // 2. Bounciness
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Bounciness", style = MaterialTheme.typography.titleMedium)
                val bounceLevels = listOf("low", "medium", "high")
                val bounceIndex = bounceLevels.indexOf(bounciness).let { if (it < 0) 1 else it }
                var sliderPosition by remember { mutableFloatStateOf(bounceIndex.toFloat()) }
                
                LaunchedEffect(bounceIndex) { sliderPosition = bounceIndex.toFloat() }
                
                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = { vm.setBounciness(bounceLevels[sliderPosition.roundToInt().coerceIn(0, 2)]) },
                    valueRange = 0f..2f,
                    steps = 1
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Low", style = MaterialTheme.typography.labelSmall)
                    Text("Medium", style = MaterialTheme.typography.labelSmall)
                    Text("High", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 3. Credit Balance Transition
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Credit Balance Transition", style = MaterialTheme.typography.titleMedium)
                val creditOptions = listOf("Fade" to "fade", "Morph" to "morph")
                val creditSelectedIndex = creditOptions.indexOfFirst { it.second == creditTransitionType }.coerceAtLeast(0)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    creditOptions.forEachIndexed { index, (label, value) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = creditOptions.size),
                            onClick = { vm.setCreditTransitionType(value) },
                            selected = creditSelectedIndex == index,
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}