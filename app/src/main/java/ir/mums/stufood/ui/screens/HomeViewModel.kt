package ir.mums.stufood.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.StufoodApp
import ir.mums.stufood.data.StufoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Home screen is intentionally minimal — it's a launchpad to the things you can do.
 * As you add more menu pages, add more buttons here.
 */
class HomeViewModel(
    private val repo: StufoodRepository = StufoodApp.instance.repository
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(repo.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _studentName = MutableStateFlow<String?>(null)
    val studentName: StateFlow<String?> = _studentName

    fun refresh() { 
        _isLoggedIn.value = repo.isLoggedIn() 
    }

    fun loadStudentName() {
        if (_studentName.value != null) return // already have it, don't re-fetch on every recomposition

        viewModelScope.launch {
            try {
                _studentName.value = repo.fetchStudentFullName()
            } catch (t: Throwable) {
                // silent — the welcome banner just won't show if this fails
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