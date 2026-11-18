package ir.mums.stufood.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
    object Home : Screen("home", "Bananite", Icons.Default.Fastfood)
    object Reservation : Screen("reservation", "Reserve Food", Icons.Default.CalendarMonth)
    object BuyFood : Screen("buy_food", "Buy Food", Icons.Default.Handshake)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    
    // Settings sub-menus
    object ThemeSettings : Screen("theme_settings", "Theme & Color", Icons.Default.Palette)
    object NotificationSettings : Screen("notification_settings", "Notifications", Icons.Default.Notifications)
    object AnimationSettings : Screen("animation_settings", "Animations", Icons.Default.Animation)
    object ReleasesSettings : Screen("releases_settings", "Releases & Issues", Icons.Default.NewReleases)

    companion object {
        /** Every screen. Lazy so the object singletons are not touched during class init. */
        val all: List<Screen> by lazy {
            listOf(Login, Home, Reservation, BuyFood, Settings, ThemeSettings, NotificationSettings, AnimationSettings, ReleasesSettings)
        }

        /** Inverse of [route]; used to restore the current screen after the activity is recreated. */
        fun fromRoute(route: String?): Screen? = all.firstOrNull { it.route == route }
    }
}


