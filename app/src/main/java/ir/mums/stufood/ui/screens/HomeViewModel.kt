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

    fun refresh() { _isLoggedIn.value = repo.isLoggedIn() }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repo.clearSession()
            _isLoggedIn.value = false
            onLoggedOut()
        }
    }
}
