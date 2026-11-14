package ir.mums.stufood

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.mums.stufood.ui.navigation.Screen
import ir.mums.stufood.ui.screens.* // Ensure all screens are imported
import ir.mums.stufood.ui.theme.BananiteTheme
import ir.mums.stufood.data.UpdateManager

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
                Screen.BuyFood -> BuyFoodScreen(onBack = { currentScreen = Screen.Home })
            }
        }
    }
}

@Composable
private fun UpdatePrompt() {
    val mgr = BananiteApp.instance.updateManager
    val state by mgr.state.collectAsState()
    var dismissed by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { mgr.autoCheckOnLaunch() }

    when (val s = state) {
        is UpdateManager.State.Available -> if (s.auto && dismissed != "a${s.release.versionName}") {
            AlertDialog(
                onDismissRequest = { dismissed = "a${s.release.versionName}" },
                title = { Text("Update available") },
                text = { Text("Bananite ${s.release.versionName} is available. You're on mobile data, so it wasn't downloaded automatically.") },
                confirmButton = {
                    TextButton(onClick = {
                        dismissed = "a${s.release.versionName}"
                        mgr.startDownload(s.release, auto = true)
                    }) { Text("Download") }
                },
                dismissButton = {
                    TextButton(onClick = { dismissed = "a${s.release.versionName}" }) { Text("Later") }
                }
            )
        }
        is UpdateManager.State.Downloaded -> if (s.auto && dismissed != "d${s.release.versionName}") {
            AlertDialog(
                onDismissRequest = { dismissed = "d${s.release.versionName}" },
                title = { Text("Update ready") },
                text = { Text("Bananite ${s.release.versionName} has been downloaded.") },
                confirmButton = {
                    TextButton(onClick = {
                        dismissed = "d${s.release.versionName}"
                        mgr.install(s.file)
                    }) { Text("Install") }
                },
                dismissButton = {
                    TextButton(onClick = { dismissed = "d${s.release.versionName}" }) { Text("Later") }
                }
            )
        }
        else -> Unit
    }
}