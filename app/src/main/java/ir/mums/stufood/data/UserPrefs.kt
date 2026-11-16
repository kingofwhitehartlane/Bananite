package ir.mums.stufood.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.security.GeneralSecurityException

// 1. Standard DataStore for NON-SENSITIVE UI/Animation settings
private val Context.uiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_prefs")

class UserPrefs(private val context: Context) {
    
    // 2. Encrypted SharedPreferences for SENSITIVE credentials
    // Backed by Android Keystore (AES256-GCM). Keys are hardware-bound and NOT backed up.
    private val securePrefs: SharedPreferences = createSecurePrefs()

    /**
     * Opens the encrypted store exactly as before (same file, same key scheme), so
     * existing users' saved logins keep working after an update.
     *
     * Failure handling, most careful first:
     *  1. Any failure -> try once more (the Keystore can be briefly unavailable).
     *  2. Still failing with a crypto/IO error -> the file is unreadable for good
     *     (typically restored from a backup without its Keystore key). Only then is
     *     it deleted and recreated empty; the user just types their password again.
     *  3. Anything else is rethrown untouched, so a valid file is never wiped for
     *     an unexpected reason.
     */
    private fun createSecurePrefs(): SharedPreferences {
        fun open(): SharedPreferences = EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return try {
            open()
        } catch (first: Exception) {
            Log.w(TAG, "Encrypted prefs failed to open, retrying once", first)
            try {
                open()
            } catch (second: Exception) {
                if (second !is GeneralSecurityException && second !is IOException) throw second
                Log.e(TAG, "Encrypted prefs unreadable; resetting saved credentials", second)
                context.deleteSharedPreferences(SECURE_PREFS_FILE)
                open()
            }
        }
    }

    companion object {
        private const val TAG = "UserPrefs"
        private const val SECURE_PREFS_FILE = "secure_user_prefs"

        // Secure Keys (Stored in EncryptedSharedPreferences)
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMEMBER = "remember"

        /** Default gap between automatic searches on the Buy Food screen. */
        const val DEFAULT_AUTO_SEARCH_INTERVAL_MS = 3_000L

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
        private val AUTO_SEARCH_INTERVAL = longPreferencesKey("auto_search_interval_ms")
        private val WHEEL_3D = booleanPreferencesKey("wheel_3d_enabled")
        private val AUTO_UPDATE = booleanPreferencesKey("auto_update_enabled")
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

    private val PERSIAN_FONT = stringPreferencesKey("persian_font")
    val persianFont: Flow<String> = context.uiDataStore.data.map { it[PERSIAN_FONT] ?: "ganjnameh" }

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
    val wheel3dEnabled: Flow<Boolean> = context.uiDataStore.data.map { it[WHEEL_3D] ?: true }
    val autoUpdateEnabled: Flow<Boolean> = context.uiDataStore.data.map { it[AUTO_UPDATE] ?: true }
    /** Buy Food auto-search interval (ms). Only the interval is saved — never the toggles. */
    val autoSearchIntervalMs: Flow<Long> =
        context.uiDataStore.data.map { it[AUTO_SEARCH_INTERVAL] ?: DEFAULT_AUTO_SEARCH_INTERVAL_MS }

    // -----------------------------------------------------------------------
    // SAVE METHODS
    // -----------------------------------------------------------------------
    /**
    * Saves credentials only when [remember] is true. When it's false we deliberately
    * do NOT touch what's already stored — logging in once without the checkbox must
    * not erase a previously remembered login.
    */
    suspend fun saveCredentials(username: String, password: String, remember: Boolean) {
        if (!remember) return
        securePrefs.edit().apply {
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            putBoolean(KEY_REMEMBER, true)
            apply()
        }
        _username.value = username
        _password.value = password
        _rememberMe.value = true
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
    suspend fun savePersianFont(id: String) { context.uiDataStore.edit { it[PERSIAN_FONT] = id } }
    suspend fun saveAutoSearchInterval(ms: Long) { context.uiDataStore.edit { it[AUTO_SEARCH_INTERVAL] = ms } }
    suspend fun saveWheel3dEnabled(enabled: Boolean) { context.uiDataStore.edit { it[WHEEL_3D] = enabled } }
    suspend fun saveAutoUpdateEnabled(enabled: Boolean) { context.uiDataStore.edit { it[AUTO_UPDATE] = enabled } }

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
            prefs.remove(PERSIAN_FONT)
            prefs.remove(AUTO_SEARCH_INTERVAL)
            prefs.remove(WHEEL_3D)
            prefs.remove(AUTO_UPDATE)
            // Intentionally NOT removing HAPTIC_ENABLED so the master switch remains accessible
        }
    }
}