package ir.mums.stufood.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.StufoodApp
import ir.mums.stufood.data.UserPrefs
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefs: UserPrefs = StufoodApp.instance.userPrefs
) : ViewModel() {
    val animationType = prefs.animationType
    val bounciness = prefs.bounciness
    val creditTransitionType = prefs.creditTransitionType
    
    // NEW
    val themeMode = prefs.themeMode
    val pureBlack = prefs.pureBlack
    val colorScheme = prefs.colorScheme

    fun setAnimationType(type: String) { viewModelScope.launch { prefs.saveAnimationType(type) } }
    fun setBounciness(level: String) { viewModelScope.launch { prefs.saveBounciness(level) } }
    fun setCreditTransitionType(type: String) { viewModelScope.launch { prefs.saveCreditTransitionType(type) } }
    
    // NEW SETTERS
    fun setThemeMode(mode: String) { viewModelScope.launch { prefs.saveThemeMode(mode) } }
    fun setPureBlack(enabled: Boolean) { viewModelScope.launch { prefs.savePureBlack(enabled) } }
    fun setColorScheme(scheme: String) { viewModelScope.launch { prefs.saveColorScheme(scheme) } }

    // RESET
    fun resetToDefaults() {
        viewModelScope.launch { prefs.resetToDefaults() }
    }
}