package ir.mums.stufood.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.mums.stufood.BuildConfig
import ir.mums.stufood.R
import ir.mums.stufood.ui.navigation.Screen
// --- IMPORT HAPTIC UTILITIES ---
import ir.mums.stufood.ui.components.HapticType
import ir.mums.stufood.ui.components.hapticClickable
import ir.mums.stufood.ui.components.rememberHapticFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (Screen) -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current // Used for the Master Switch Bypass
    
val hapticEnabled by vm.hapticFeedbackEnabled.collectAsState(initial = true)
val haptic = rememberHapticFeedback(enabled = hapticEnabled)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { 
                        haptic(HapticType.CLICK) // Respects the toggle
                        onNavigate(Screen.Home) 
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header: App Logo, Name, Version
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kingofwhitehartlane/Bananite"))
                        context.startActivity(intent)
                    }
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "App Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Bananite",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Thin),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- THE HAPTIC MASTER SWITCH ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Haptic Feedback", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(
                    checked = hapticEnabled,
                    onCheckedChange = { newValue ->
                        // THE PARADOX FIX: 
                        // We bypass the app-level toggle for the master switch itself.
                        // It should ALWAYS tick so the user knows the physical toggle worked.
                        view.performHapticFeedback(
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                android.view.HapticFeedbackConstants.CLOCK_TICK
                            } else {
                                android.view.HapticFeedbackConstants.VIRTUAL_KEY
                            }
                        )
                        vm.setHapticFeedbackEnabled(newValue)
                    }
                )
            }

            // Sub Menus (Passing hapticEnabled down to the children)
            SettingsGroup(
                items = listOf(
                    { SubMenuItem("Theme & Color", Icons.Default.Palette, hapticEnabled) { onNavigate(Screen.ThemeSettings) } },
                    { SubMenuItem("Animations", Icons.Default.Animation, hapticEnabled) { onNavigate(Screen.AnimationSettings) } }
                )
            )

            Spacer(modifier = Modifier.weight(1f))

            // Reset to Defaults
            Button(
                onClick = { 
                    haptic(HapticType.HEAVY) // Will only fire if hapticEnabled is true
                    vm.resetToDefaults() 
                },
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
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsGroup(
    items: List<@Composable () -> Unit>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        items.forEachIndexed { index, item ->
            item()
            if (index < items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun SubMenuItem(
    title: String,
    icon: ImageVector,
    hapticEnabled: Boolean, // Inherited from parent
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Pass the state into the custom modifier
            .hapticClickable(
                type = HapticType.CLICK, 
                hapticsEnabled = hapticEnabled, 
                onClick = onClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}