package ir.mums.stufood.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.mums.stufood.StufoodApp
import ir.mums.stufood.data.UserPrefs
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class SettingsViewModel(
    private val prefs: UserPrefs = StufoodApp.instance.userPrefs
) : ViewModel() {
    val animationType = prefs.animationType
    val bounciness = prefs.bounciness

    fun setAnimationType(type: String) {
        viewModelScope.launch {
            prefs.saveAnimationType(type)
        }
    }
    
    fun setBounciness(level: String) {
        viewModelScope.launch { prefs.saveBounciness(level) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val animationType by vm.animationType.collectAsState(initial = "smooth")
    val bounciness by vm.bounciness.collectAsState(initial = "medium")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleLarge
            )
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Animation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Welcome Name Animation",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                
                val options = listOf("Bounce" to "bounce", "Smooth" to "smooth")
                val selectedIndex = options.indexOfFirst { it.second == animationType }.coerceAtLeast(0)

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    options.forEachIndexed { index, (label, value) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = options.size
                            ),
                            onClick = { vm.setAnimationType(value) },
                            selected = index == selectedIndex,
                            label = { Text(label) }
                        )
                    }
                }
                
                Text(
                    text = "Choose how your name animates when you log in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Animation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(text = "Bounciness", style = MaterialTheme.typography.titleMedium)
                }

                val bounceLevels = listOf("low", "medium", "high")
                // Unknown/legacy value -> fall back to medium, same as the stored default.
                val bounceIndex = bounceLevels.indexOf(bounciness).let { if (it < 0) 1 else it }

                // Local drag state so the thumb feels smooth; the real setting is only
                // written on release, and re-synced if it changes from elsewhere.
                var sliderPosition by remember { mutableFloatStateOf(bounceIndex.toFloat()) }
                LaunchedEffect(bounceIndex) { sliderPosition = bounceIndex.toFloat() }

                Slider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = {
                        vm.setBounciness(bounceLevels[sliderPosition.roundToInt().coerceIn(0, 2)])
                    },
                    valueRange = 0f..2f,
                    steps = 1 // 3 stops: low / medium / high
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Low", style = MaterialTheme.typography.labelSmall)
                    Text("Medium", style = MaterialTheme.typography.labelSmall)
                    Text("High", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    text = "How bouncy the reservation screen's animations feel. Low also " +
                        "trims the extra motion in the welcome name animation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}