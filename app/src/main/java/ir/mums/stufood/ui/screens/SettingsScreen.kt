package ir.mums.stufood.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.mums.stufood.ui.components.MultiScriptText
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val animationType by vm.animationType.collectAsState(initial = "smooth")
    val bounciness by vm.bounciness.collectAsState(initial = "medium")
    val creditTransitionType by vm.creditTransitionType.collectAsState(initial = "fade")
    
    val themeMode by vm.themeMode.collectAsState(initial = "system")
    val pureBlack by vm.pureBlack.collectAsState(initial = false)
    val colorScheme by vm.colorScheme.collectAsState(initial = "dynamic")

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ==========================================
            // 1. THEME MODE (System / Light / Dark)
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.BrightnessAuto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "Theme Mode", style = MaterialTheme.typography.titleMedium)
                }
                
                val themeOptions = listOf(
                    "system" to Icons.Default.BrightnessAuto,
                    "light" to Icons.Default.LightMode,
                    "dark" to Icons.Default.DarkMode
                )
                val selectedIndex = themeOptions.indexOfFirst { it.first == themeMode }.coerceAtLeast(0)
                
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    themeOptions.forEachIndexed { index, (value, icon) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                            onClick = { vm.setThemeMode(value) },
                            selected = selectedIndex == index,
                            icon = { Icon(icon, contentDescription = value) },
                            label = {}
                        )
                    }
                }
                Text(
                    text = "Choose between System default, Light, or Dark mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ==========================================
            // 2. PURE BLACK (OLED)
            // ==========================================
            val isDarkMode = themeMode == "dark" || (themeMode == "system" && androidx.compose.foundation.isSystemInDarkTheme())
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.InvertColors, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(text = "Pure Black (OLED)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Use true black for dark mode backgrounds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = pureBlack,
                    onCheckedChange = { vm.setPureBlack(it) },
                    enabled = isDarkMode
                )
            }
            if (!isDarkMode) {
                Text(
                    text = "Pure Black only applies when Dark mode is active.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ==========================================
            // 3. COLOR SCHEME (Material You vs Custom)
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "Color Palette", style = MaterialTheme.typography.titleMedium)
                }
                
                var expanded by remember { mutableStateOf(false) }
                val colorOptions = listOf(
                    "Material You (Dynamic)" to "dynamic",
                    "Expressive (Custom)" to "custom"
                )
                val currentLabel = colorOptions.firstOrNull { it.second == colorScheme }?.first ?: colorOptions[0].first

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = currentLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Palette Style") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        colorOptions.forEach { (label, value) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    vm.setColorScheme(value)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Text(
                    text = "Material You uses dynamic wallpaper colors (Android 12+). Expressive uses the app's custom warm palette.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ==========================================
            // 4. ANIMATION SETTINGS (Existing)
            // ==========================================
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = "Animations", style = MaterialTheme.typography.titleLarge)
                
                // Welcome Name Animation
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

                // Bounciness
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

                // Credit Transition
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

            // ==========================================
            // 5. RESET TO DEFAULTS
            // ==========================================
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.resetToDefaults() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Reset All Settings to Default")
            }
            
            Spacer(Modifier.height(16.dp))
        }
    }
}