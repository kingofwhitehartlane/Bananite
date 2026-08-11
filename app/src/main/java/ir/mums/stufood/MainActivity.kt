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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.mums.stufood.ui.navigation.Screen
import ir.mums.stufood.ui.screens.HomeScreen
import ir.mums.stufood.ui.screens.LoginScreen
import ir.mums.stufood.ui.screens.ReservationScreen
import ir.mums.stufood.ui.theme.StuFoodTheme

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
            StuFoodTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    // FIX: previously this always started at Screen.Login, regardless of whether we
    // already had a live session. The Activity (and this whole composable tree) can
    // get torn down and recreated by Android just from backgrounding the app — the
    // process itself survives, so StufoodApp.instance.repository (and its cookies)
    // are untouched, but `currentScreen` used to reset to Login anyway, which made it
    // *look* like the session was lost. Seeding the initial value from the actual
    // session state fixes that: if we're still logged in, go straight to Home.
    var currentScreen by remember {
        mutableStateOf<Screen>(
            if (StufoodApp.instance.repository.isLoggedIn()) Screen.Home else Screen.Login
        )
    }

    // FIX: there was no BackHandler at all, so the system back button had nothing to
    // intercept inside the app and fell through to the default Activity behavior —
    // finishing (and thus closing) the app, even from Reservation or Home.
    // This makes back navigate one level up ("menus") instead, and only lets it fall
    // through to the default close-app behavior once we're already at Home.
    BackHandler(enabled = currentScreen == Screen.Reservation) {
        currentScreen = Screen.Home
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screenTransition"
    ) { screen ->
        when (screen) {
            Screen.Login -> LoginScreen(
                onLoggedIn = { currentScreen = Screen.Home }
            )
            Screen.Home -> HomeScreen(
                onNavigate = { target -> currentScreen = target }
            )
            Screen.Reservation -> ReservationScreen(
                onBack = { currentScreen = Screen.Home }
            )
            // Logout is handled inside HomeScreen's menu card; this branch is just a
            // safety net so the `when` is exhaustive.
            Screen.Logout -> LoginScreen(
                onLoggedIn = { currentScreen = Screen.Home }
            )
        }
    }
}