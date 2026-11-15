package ir.mums.stufood

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.mums.stufood.ui.navigation.Screen
import ir.mums.stufood.ui.screens.* // Ensure all screens are imported
import ir.mums.stufood.ui.theme.BananiteTheme
import ir.mums.stufood.data.UpdateManager
import ir.mums.stufood.ui.components.ObtainiumAccent
import ir.mums.stufood.util.AppLinks
import ir.mums.stufood.util.Obtainium

/**
 * Single-activity host. Navigation is just a `when` on a Screen enum — small enough
 * that pulling in Navigation-Compose would be more ceremony than it's worth.
 *
 * As you add more menu pages, add more branches here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}

@Composable
private fun App() {
    // Collect theme preferences
    val prefs = BananiteApp.instance.userPrefs
    val themeMode by prefs.themeMode.collectAsState(initial = "system")
    val pureBlack by prefs.pureBlack.collectAsState(initial = false)
    val colorSchemeType by prefs.colorScheme.collectAsState(initial = "dynamic")
    val persianFont by prefs.persianFont.collectAsState(initial = "ganjnameh")
    
    var currentScreen by remember {
        mutableStateOf<Screen>(
            if (BananiteApp.instance.repository.isLoggedIn()) Screen.Home else Screen.Login
        )
    }
    var settingsOrigin by remember { mutableStateOf<Screen>(Screen.Home) }

    BackHandler(enabled = currentScreen != Screen.Home && currentScreen != Screen.Login) {
        currentScreen = when (currentScreen) {
            Screen.Reservation      -> Screen.Home
            Screen.Settings         -> settingsOrigin  
            Screen.BuyFood -> Screen.Home
            Screen.ThemeSettings    -> Screen.Settings
            Screen.AnimationSettings -> Screen.Settings
            Screen.ReleasesSettings -> Screen.Settings
            else                    -> Screen.Home
        }
    }

    BananiteTheme(
        themeMode = themeMode,
        pureBlack = pureBlack,
        colorSchemeType = colorSchemeType,
        persianFontId = persianFont
    ) {
        UpdatePrompt()
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screenTransition"
        ) { screen ->
            when (screen) {
                Screen.Login -> LoginScreen(
                    onLoggedIn = { currentScreen = Screen.Home },
                    onOpenSettings = {
                        settingsOrigin = Screen.Login
                        currentScreen = Screen.Settings
                    }
                )
                Screen.Home -> HomeScreen(onNavigate = { target ->
                    if (target == Screen.Settings) settingsOrigin = Screen.Home
                    currentScreen = target
                })
                Screen.Settings -> SettingsScreen(onNavigate = { target ->
                    // SettingsScreen's back arrow calls onNavigate(Screen.Home)
                    currentScreen = if (target == Screen.Home) settingsOrigin else target
                })
                Screen.Reservation -> ReservationScreen(onBack = { currentScreen = Screen.Home })
                Screen.ThemeSettings -> ThemeSettingsScreen(onBack = { currentScreen = Screen.Settings })
                Screen.AnimationSettings -> AnimationSettingsScreen(onBack = { currentScreen = Screen.Settings })
                Screen.ReleasesSettings -> ReleasesSettingsScreen(onBack = { currentScreen = Screen.Settings })
                Screen.BuyFood -> BuyFoodScreen(onBack = { currentScreen = Screen.Home })
            }
        }
    }
}

@Composable
private fun UpdatePrompt() {
    val context = LocalContext.current
    val mgr = BananiteApp.instance.updateManager
    val state by mgr.state.collectAsState()
    var dismissedVersion by remember { mutableStateOf<String?>(null) }
    val obtainiumInstalled = remember { Obtainium.isInstalled(context) }

    LaunchedEffect(Unit) { mgr.autoCheckOnLaunch() }

    val s = state
    if (s is UpdateManager.State.Available && s.auto && dismissedVersion != s.release.versionName) {
        val dismiss = { dismissedVersion = s.release.versionName }

        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                modifier = Modifier.padding(horizontal = 32.dp).widthIn(min = 280.dp, max = 560.dp)
            ) {
                Column(modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)) {
                    Text("Update available", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Bananite ${s.release.versionName} is available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))

                    if (!obtainiumInstalled) {
                        Button(
                            onClick = {
                                dismiss()
                                AppLinks.open(context, AppLinks.OBTAINIUM_GUIDE)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ObtainiumAccent,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_obtainium),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Setup Obtainium")
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = dismiss) { Text("Later") }
                        TextButton(onClick = {
                            dismiss()
                            if (!(obtainiumInstalled && Obtainium.launch(context))) {
                                mgr.openDownload(s.release)
                            }
                        }) { Text(if (obtainiumInstalled) "Open Obtainium" else "Download") }
                    }
                }
            }
        }
    }
}