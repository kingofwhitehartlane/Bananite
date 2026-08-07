package ir.mums.stufood

import android.os.Bundle
import androidx.activity.ComponentActivity
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
    // We start at Login. After a successful login we hop to Home; from Home we can
    // navigate to Reservation or back to Login (logout).
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Login) }

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
