// app/src/main/java/ir/mums/stufood/data/UserPrefs.kt
package ir.mums.stufood.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPrefs(private val context: Context) {
    companion object {
        private val USERNAME = stringPreferencesKey("username")
        private val PASSWORD = stringPreferencesKey("password")
        private val REMEMBER = booleanPreferencesKey("remember")
        
        private val ANIMATION_TYPE = stringPreferencesKey("animation_type")
        private val BOUNCINESS = stringPreferencesKey("bounciness")
        private val CREDIT_TRANSITION = stringPreferencesKey("credit_transition")
        
        // NEW SETTINGS
        private val THEME_MODE = stringPreferencesKey("theme_mode") // "system", "light", "dark"
        private val PURE_BLACK = booleanPreferencesKey("pure_black")
        private val COLOR_SCHEME = stringPreferencesKey("color_scheme") // "dynamic", "custom"
        private val WELCOME_NAME_ENABLED = booleanPreferencesKey("welcome_name_enabled")
        private val DISABLE_ALL_ANIMATIONS = booleanPreferencesKey("disable_all_animations")
    }

    val username: Flow<String> = context.userDataStore.data.map { it[USERNAME] ?: "" }
    val password: Flow<String> = context.userDataStore.data.map { it[PASSWORD] ?: "" }
    val rememberMe: Flow<Boolean> = context.userDataStore.data.map { it[REMEMBER] ?: true }
    
    val animationType: Flow<String> = context.userDataStore.data.map { it[ANIMATION_TYPE] ?: "smooth"}
    val bounciness: Flow<String> = context.userDataStore.data.map { it[BOUNCINESS] ?: "medium" }
    val creditTransitionType: Flow<String> = context.userDataStore.data.map { it[CREDIT_TRANSITION] ?: "fade" }
    
    // NEW FLOWS
    val themeMode: Flow<String> = context.userDataStore.data.map { it[THEME_MODE] ?: "system" }
    val pureBlack: Flow<Boolean> = context.userDataStore.data.map { it[PURE_BLACK] ?: false }
    val colorScheme: Flow<String> = context.userDataStore.data.map { it[COLOR_SCHEME] ?: "dynamic" }

    val welcomeNameEnabled: Flow<Boolean> = context.userDataStore.data.map { it[WELCOME_NAME_ENABLED] ?: true }
    val disableAllAnimations: Flow<Boolean> = context.userDataStore.data.map { it[DISABLE_ALL_ANIMATIONS] ?: false }


    suspend fun saveCredentials(username: String, password: String, remember: Boolean) {
        context.userDataStore.edit { prefs ->
            if (remember) {
                prefs[USERNAME] = username
                prefs[PASSWORD] = password
                prefs[REMEMBER] = true
            } else {
                prefs.remove(USERNAME)
                prefs.remove(PASSWORD)
                prefs[REMEMBER] = false
            }
        }
    }

    suspend fun saveAnimationType(type: String) { context.userDataStore.edit { it[ANIMATION_TYPE] = type } }
    suspend fun saveBounciness(level: String) { context.userDataStore.edit { it[BOUNCINESS] = level } }
    suspend fun saveCreditTransitionType(type: String) { context.userDataStore.edit { it[CREDIT_TRANSITION] = type } }
    
    // NEW SAVE METHODS
    suspend fun saveThemeMode(mode: String) { context.userDataStore.edit { it[THEME_MODE] = mode } }
    suspend fun savePureBlack(enabled: Boolean) { context.userDataStore.edit { it[PURE_BLACK] = enabled } }
    suspend fun saveColorScheme(scheme: String) { context.userDataStore.edit { it[COLOR_SCHEME] = scheme } }

    suspend fun saveWelcomeNameEnabled(enabled: Boolean) { context.userDataStore.edit { it[WELCOME_NAME_ENABLED] = enabled } }
    suspend fun saveDisableAllAnimations(disabled: Boolean) { context.userDataStore.edit { it[DISABLE_ALL_ANIMATIONS] = disabled } }


    // RESET TO DEFAULTS
    suspend fun resetToDefaults() {
        context.userDataStore.edit { it.clear() }
    }
}