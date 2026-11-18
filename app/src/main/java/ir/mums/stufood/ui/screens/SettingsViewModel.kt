package ir.mums.stufood.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.BananiteApp
import ir.mums.stufood.data.AppLogs
import ir.mums.stufood.data.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefs: UserPrefs = BananiteApp.instance.userPrefs
) : ViewModel() {
    val animationType = prefs.animationType
    val bounciness = prefs.bounciness
    val creditTransitionType = prefs.creditTransitionType
    
    val themeMode = prefs.themeMode
    val pureBlack = prefs.pureBlack
    val colorScheme = prefs.colorScheme
    val welcomeNameEnabled = prefs.welcomeNameEnabled
    val disableAllAnimations = prefs.disableAllAnimations
    val hapticFeedbackEnabled = prefs.hapticFeedbackEnabled

    val wheel3dEnabled = prefs.wheel3dEnabled                        // <-- add
    fun setWheel3dEnabled(enabled: Boolean) {                        // <-- add
        viewModelScope.launch { prefs.saveWheel3dEnabled(enabled) }
    }

    val persianFont = prefs.persianFont
    fun setPersianFont(id: String) { viewModelScope.launch { prefs.savePersianFont(id) } }

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

    // NOTIFICATIONS -> BUY FOOD
    val runInBackground = prefs.runInBackground
    fun setRunInBackground(enabled: Boolean) { viewModelScope.launch { prefs.saveRunInBackground(enabled) } }
    val liveNotifications = prefs.liveNotifications
    fun setLiveNotifications(enabled: Boolean) { viewModelScope.launch { prefs.saveLiveNotifications(enabled) } }

    val autoUpdateEnabled = prefs.autoUpdateEnabled
    fun setAutoUpdateEnabled(enabled: Boolean) { viewModelScope.launch { prefs.saveAutoUpdateEnabled(enabled) } }

    private val updater get() = BananiteApp.instance.updateManager
    val updateState = BananiteApp.instance.updateManager.state
    fun checkForUpdates() = updater.check(auto = false)
    fun openUpdateInBrowser(r: ir.mums.stufood.data.UpdateManager.ReleaseInfo) = updater.openDownload(r)

    // APP LOGS (Releases & Issues screen)
    private val _logs = MutableStateFlow("")
    val logs: StateFlow<String> = _logs
    private val _logLevel = MutableStateFlow(AppLogs.Level.DEBUG)
    val logLevel: StateFlow<AppLogs.Level> = _logLevel

    fun refreshLogs() { viewModelScope.launch { _logs.value = AppLogs.read(_logLevel.value) } }
    fun setLogLevel(level: AppLogs.Level) { _logLevel.value = level; refreshLogs() }

    // RESET
    fun resetToDefaults() {
        viewModelScope.launch { prefs.resetToDefaults() }
    }
}


