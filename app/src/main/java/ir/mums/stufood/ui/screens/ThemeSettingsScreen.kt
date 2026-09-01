package ir.mums.stufood.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
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
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val themeMode by vm.themeMode.collectAsState(initial = "system")
    val pureBlack by vm.pureBlack.collectAsState(initial = false)
    val colorScheme by vm.colorScheme.collectAsState(initial = "dynamic")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme & Color") },
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
            // 1. THEME MODE
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.BrightnessAuto, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(text = "Theme", style = MaterialTheme.typography.titleMedium)
                    }
                    val themeOptions = listOf(
                        "system" to Icons.Default.BrightnessAuto,
                        "light" to Icons.Default.LightMode,
                        "dark" to Icons.Default.DarkMode
                    )
                    val selectedIndex = themeOptions.indexOfFirst { it.first == themeMode }.coerceAtLeast(0)
                    SingleChoiceSegmentedButtonRow {
                        themeOptions.forEachIndexed { index, (value, icon) ->
                            val isSelected = selectedIndex == index
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = themeOptions.size),
                                onClick = { vm.setThemeMode(value) },
                                selected = isSelected,
                                icon = { SegmentedButtonDefaults.Icon(active = isSelected) },
                                label = { Icon(icon, contentDescription = value) }
                            )
                        }
                    }
                }
                Text(
                    text = "Choose between System default, Light, or Dark mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 2. PURE BLACK (OLED)
            val isDarkMode = themeMode == "dark" || (themeMode == "system" && isSystemInDarkTheme())
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

            // 3. COLOR SCHEME
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "Color Palette", style = MaterialTheme.typography.titleMedium)
                }
                var expanded by remember { mutableStateOf(false) }
                val colorOptions = listOf(
                    "Material You (Dynamic)" to "dynamic",
                    "Banana Yellow (App Default)" to "custom"
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
                    text = "Material You uses dynamic wallpaper colors (Android 12+). Banana Yellow is the app's default vibrant theme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}