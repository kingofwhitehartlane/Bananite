package ir.mums.stufood.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.BananiteApp
import ir.mums.stufood.data.UserPrefs
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefs: UserPrefs = BananiteApp.instance.userPrefs
) : ViewModel() {
    val animationType = prefs.animationType
    val bounciness = prefs.bounciness
    val creditTransitionType = prefs.creditTransitionType
    
    // NEW
    val themeMode = prefs.themeMode
    val pureBlack = prefs.pureBlack
    val colorScheme = prefs.colorScheme
    val welcomeNameEnabled = prefs.welcomeNameEnabled
    val disableAllAnimations = prefs.disableAllAnimations
    val hapticFeedbackEnabled = prefs.hapticFeedbackEnabled

    fun setAnimationType(type: String) { viewModelScope.launch { prefs.saveAnimationType(type) } }
    fun setBounciness(level: String) { viewModelScope.launch { prefs.saveBounciness(level) } }
    fun setCreditTransitionType(type: String) { viewModelScope.launch { prefs.saveCreditTransitionType(type) } }
    
    // NEW SETTERS
    fun setThemeMode(mode: String) { viewModelScope.launch { prefs.saveThemeMode(mode) } }
    fun setPureBlack(enabled: Boolean) { viewModelScope.launch { prefs.savePureBlack(enabled) } }
    fun setColorScheme(scheme: String) { viewModelScope.launch { prefs.saveColorScheme(scheme) } }
    fun setWelcomeNameEnabled(enabled: Boolean) { viewModelScope.launch { prefs.saveWelcomeNameEnabled(enabled) } }
    fun setDisableAllAnimations(disabled: Boolean) { viewModelScope.launch { prefs.saveDisableAllAnimations(disabled) } }

    fun setHapticFeedbackEnabled(enabled: Boolean) { 
        viewModelScope.launch { prefs.saveHapticFeedbackEnabled(enabled) } 
    }

    // RESET
    fun resetToDefaults() {
        viewModelScope.launch { prefs.resetToDefaults() }
    }
}