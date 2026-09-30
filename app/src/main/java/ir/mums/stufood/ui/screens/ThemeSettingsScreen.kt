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
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.mums.stufood.ui.components.HapticType
import ir.mums.stufood.ui.components.rememberHapticFeedback
import ir.mums.stufood.ui.theme.PersianFontOptions

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ir.mums.stufood.ui.theme.DarkColors
import ir.mums.stufood.ui.theme.LightColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val themeMode by vm.themeMode.collectAsState(initial = "system")
    val pureBlack by vm.pureBlack.collectAsState(initial = false)
    val colorScheme by vm.colorScheme.collectAsState(initial = "dynamic")
    val hapticEnabled by vm.hapticFeedbackEnabled.collectAsState(initial = true)
    val haptic = rememberHapticFeedback(enabled = hapticEnabled)
    val persianFont by vm.persianFont.collectAsState(initial = "ganjnameh")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme & Color") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic(HapticType.CLICK)
                        onBack()
                    }) {
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
                                onClick = { 
                                    haptic(HapticType.TICK)
                                    vm.setThemeMode(value) 
                                },
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
                    onCheckedChange = { 
                        haptic(HapticType.TICK)
                        vm.setPureBlack(it) 
                    },
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
                        // Dots for the currently selected palette
                        leadingIcon = { PaletteDots(palettePreviewColors(colorScheme, isDarkMode)) },
                        trailingIcon = {
                            PaletteDots(
                                palettePreviewColors(value, isDarkMode),
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        colorOptions.forEach { (label, value) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                trailingIcon = { PaletteDots(palettePreviewColors(value, isDarkMode)) },
                                onClick = {
                                    haptic(HapticType.TICK)
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
            // 4. PERSIAN FONT
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.TextFields, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "Persian Font", style = MaterialTheme.typography.titleMedium)
                }
                var fontExpanded by remember { mutableStateOf(false) }
                val currentFont = PersianFontOptions.firstOrNull { it.id == persianFont } ?: PersianFontOptions.first()

                ExposedDropdownMenuBox(
                    expanded = fontExpanded,
                    onExpandedChange = { fontExpanded = !fontExpanded }
                ) {
                    OutlinedTextField(
                        value = currentFont.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Font") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = fontExpanded,
                        onDismissRequest = { fontExpanded = false }
                    ) {
                        PersianFontOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.label)
                                        // Live preview in the option's own font
                                        Text(
                                            text = "غذای شنبه سلف پردیس چلو ماکارونی با ماهی",
                                            fontFamily = option.family,
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * option.sizeScale,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    haptic(HapticType.TICK)
                                    vm.setPersianFont(option.id)
                                    fontExpanded = false
                                }
                            )
                        }
                    }
                }
                Text(
                    text = "Applies to Persian text such as food names and messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The 2 preview colors for a palette option, taken from the real scheme BananiteTheme would use. */
@Composable
private fun palettePreviewColors(paletteType: String, dark: Boolean): List<Color> {
    val context = LocalContext.current
    val scheme = if (paletteType == "dynamic" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors
    return listOf(scheme.primary, scheme.secondaryContainer)
}

@Composable
private fun PaletteDots(colors: List<Color>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(color, CircleShape)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        CircleShape
                    )
            )
        }
    }
}