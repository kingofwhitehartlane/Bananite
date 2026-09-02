package ir.mums.stufood.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

// 1. Standard DataStore for NON-SENSITIVE UI/Animation settings
private val Context.uiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_prefs")

class UserPrefs(private val context: Context) {
    
    // 2. Encrypted SharedPreferences for SENSITIVE credentials
    // Backed by Android Keystore (AES256-GCM). Keys are hardware-bound and NOT backed up.
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_user_prefs", // File name
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        // Secure Keys (Stored in EncryptedSharedPreferences)
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMEMBER = "remember"

        // UI Keys (Stored in DataStore)
        private val ANIMATION_TYPE = stringPreferencesKey("animation_type")
        private val BOUNCINESS = stringPreferencesKey("bounciness")
        private val CREDIT_TRANSITION = stringPreferencesKey("credit_transition")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val PURE_BLACK = booleanPreferencesKey("pure_black")
        private val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        private val WELCOME_NAME_ENABLED = booleanPreferencesKey("welcome_name_enabled")
        private val DISABLE_ALL_ANIMATIONS = booleanPreferencesKey("disable_all_animations")
        private val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
    }

    // -----------------------------------------------------------------------
    // SENSITIVE CREDENTIALS (Encrypted, wrapped in StateFlow to match old API)
    // -----------------------------------------------------------------------
    private val _username = MutableStateFlow(securePrefs.getString(KEY_USERNAME, "") ?: "")
    val username: Flow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow(securePrefs.getString(KEY_PASSWORD, "") ?: "")
    val password: Flow<String> = _password.asStateFlow()

    private val _rememberMe = MutableStateFlow(securePrefs.getBoolean(KEY_REMEMBER, true))
    val rememberMe: Flow<Boolean> = _rememberMe.asStateFlow()

    // -----------------------------------------------------------------------
    // NON-SENSITIVE UI SETTINGS (Standard DataStore)
    // -----------------------------------------------------------------------
    val animationType: Flow<String> = context.uiDataStore.data.map { it[ANIMATION_TYPE] ?: "smooth" }
    val bounciness: Flow<String> = context.uiDataStore.data.map { it[BOUNCINESS] ?: "medium" }
    val creditTransitionType: Flow<String> = context.uiDataStore.data.map { it[CREDIT_TRANSITION] ?: "fade" }
    
    val themeMode: Flow<String> = context.uiDataStore.data.map { it[THEME_MODE] ?: "system" }
    val pureBlack: Flow<Boolean> = context.uiDataStore.data.map { it[PURE_BLACK] ?: false }
    val colorScheme: Flow<String> = context.uiDataStore.data.map { it[COLOR_SCHEME] ?: "dynamic" }
    val welcomeNameEnabled: Flow<Boolean> = context.uiDataStore.data.map { it[WELCOME_NAME_ENABLED] ?: true }
    val disableAllAnimations: Flow<Boolean> = context.uiDataStore.data.map { it[DISABLE_ALL_ANIMATIONS] ?: false }
    val hapticFeedbackEnabled: Flow<Boolean> = context.uiDataStore.data.map { it[HAPTIC_ENABLED] ?: true }

    // -----------------------------------------------------------------------
    // SAVE METHODS
    // -----------------------------------------------------------------------
    suspend fun saveCredentials(username: String, password: String, remember: Boolean) {
        securePrefs.edit().apply {
            if (remember) {
                putString(KEY_USERNAME, username)
                putString(KEY_PASSWORD, password)
                putBoolean(KEY_REMEMBER, true)
            } else {
                remove(KEY_USERNAME)
                remove(KEY_PASSWORD)
                putBoolean(KEY_REMEMBER, false)
            }
            apply()
        }
        // Update flows immediately for reactive UI
        _username.value = if (remember) username else ""
        _password.value = if (remember) password else ""
        _rememberMe.value = remember
    }

    suspend fun saveAnimationType(type: String) { context.uiDataStore.edit { it[ANIMATION_TYPE] = type } }
    suspend fun saveBounciness(level: String) { context.uiDataStore.edit { it[BOUNCINESS] = level } }
    suspend fun saveCreditTransitionType(type: String) { context.uiDataStore.edit { it[CREDIT_TRANSITION] = type } }
    
    suspend fun saveThemeMode(mode: String) { context.uiDataStore.edit { it[THEME_MODE] = mode } }
    suspend fun savePureBlack(enabled: Boolean) { context.uiDataStore.edit { it[PURE_BLACK] = enabled } }
    suspend fun saveColorScheme(scheme: String) { context.uiDataStore.edit { it[COLOR_SCHEME] = scheme } }
    suspend fun saveWelcomeNameEnabled(enabled: Boolean) { context.uiDataStore.edit { it[WELCOME_NAME_ENABLED] = enabled } }
    suspend fun saveDisableAllAnimations(disabled: Boolean) { context.uiDataStore.edit { it[DISABLE_ALL_ANIMATIONS] = disabled } }
    suspend fun saveHapticFeedbackEnabled(enabled: Boolean) { context.uiDataStore.edit { it[HAPTIC_ENABLED] = enabled } }

    // -----------------------------------------------------------------------
    // RESET TO DEFAULTS (Preserves credentials, clears UI prefs)
    // -----------------------------------------------------------------------
    suspend fun resetToDefaults() {
        context.uiDataStore.edit { prefs ->
            prefs.remove(ANIMATION_TYPE)
            prefs.remove(BOUNCINESS)
            prefs.remove(CREDIT_TRANSITION)
            prefs.remove(THEME_MODE)
            prefs.remove(PURE_BLACK)
            prefs.remove(COLOR_SCHEME)
            prefs.remove(WELCOME_NAME_ENABLED)
            prefs.remove(DISABLE_ALL_ANIMATIONS)
            // Intentionally NOT removing HAPTIC_ENABLED so the master switch remains accessible
        }
    }
}