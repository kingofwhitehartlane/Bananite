package ir.mums.stufood.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single source of truth for "where am I in the app". Plain sealed class — no
 * Navigation-Compose dependency needed for an app this small.
 *
 * Each screen is identified by a string route; the active screen is just a state
 * variable in MainActivity. We use a sealed class (rather than raw strings) so the
 * compiler tells us when we forget to handle a screen.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Login : Screen("login", "Login", Icons.Default.Person)
    object Home : Screen("home", "StuFood", Icons.Default.Fastfood)
    object Reservation : Screen("reservation", "Reserve Food", Icons.Default.CalendarMonth)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Logout : Screen("logout", "Logout", Icons.Default.Logout)
}

val bottomNavScreens = listOf(Screen.Home, Screen.Reservation, Screen.Logout)
