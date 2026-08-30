package ir.mums.stufood.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.StufoodApp
import ir.mums.stufood.data.StufoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn // ADDED
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repo: StufoodRepository = StufoodApp.instance.repository
) : ViewModel() {
    private val _isLoggedIn = MutableStateFlow(repo.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn
    
    // Observe the repository's student name state directly
    val studentName: StateFlow<String?> = repo.studentName
    
    // FIXED: Convert Flow to StateFlow with a default value
    val animationType: StateFlow<String> = StufoodApp.instance.userPrefs.animationType.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        "bounce"
    )

    fun refresh() {
        _isLoggedIn.value = repo.isLoggedIn()
    }

    fun loadStudentName() {
        // If the name is already in the repo (from login), it will be emitted automatically.
        // If it's null (edge case), we fetch it.
        if (repo.studentName.value == null) {
            viewModelScope.launch {
                try {
                    repo.fetchStudentFullName()
                } catch (t: Throwable) {
                    // silent
                }
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repo.clearSession()
            _isLoggedIn.value = false
            onLoggedOut()
        }
    }
}