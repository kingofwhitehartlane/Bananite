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

/**
 * Tiny persistent store for "remember me" credentials.
 *
 * The credentials are saved in plaintext in DataStore. This is fine for a personal-use
 * app on a private network — if you want stronger storage, swap this for an
 * EncryptedSharedPreferences-backed implementation later.
 */
private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPrefs(private val context: Context) {
    companion object {
        private val USERNAME = stringPreferencesKey("username")
        private val PASSWORD = stringPreferencesKey("password")
        private val REMEMBER = booleanPreferencesKey("remember")
        private val ANIMATION_TYPE = stringPreferencesKey("animation_type") // ADDED
    }

    val username: Flow<String> = context.userDataStore.data.map { it[USERNAME] ?: "" }
    val password: Flow<String> = context.userDataStore.data.map { it[PASSWORD] ?: "" }
    val rememberMe: Flow<Boolean> = context.userDataStore.data.map { it[REMEMBER] ?: true }
    val animationType: Flow<String> = context.userDataStore.data.map { it[ANIMATION_TYPE] ?: "smooth"

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

    suspend fun clear() {
        context.userDataStore.edit { it.clear() }
    }

    // ADDED
    suspend fun saveAnimationType(type: String) {
        context.userDataStore.edit { prefs ->
            prefs[ANIMATION_TYPE] = type
        }
    }
}