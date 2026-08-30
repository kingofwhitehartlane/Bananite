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

    companion object {
        /**
         * Student name fetched right after a successful login (see LoginViewModel.submit).
         * The login screen deliberately holds its loading state until this is in, so the
         * Home screen's welcome animation can play immediately on arrival. Consumed once
         * by [loadStudentName]; if it's missing (e.g. that fetch failed), Home falls back
         * to fetching the name itself.
         */
        @Volatile
        var preloadedStudentName: String? = null
    }

    private val _isLoggedIn = MutableStateFlow(repo.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _studentName = MutableStateFlow<String?>(null)
    val studentName: StateFlow<String?> = _studentName

    fun refresh() {
        _isLoggedIn.value = repo.isLoggedIn()
    }

    fun loadStudentName() {
        // 1) Just logged in — use the name LoginViewModel already fetched, so there's
        // no second network round-trip and no delayed "pop-in" of the banner.
        val preloaded = preloadedStudentName
        if (!preloaded.isNullOrBlank()) {
            preloadedStudentName = null // consume once
            _studentName.value = preloaded
            return
        }

        // 2) Already have a name (config change / recomposition) — keep it.
        if (_studentName.value != null) return

        // 3) Otherwise fetch it now (also covers a login-time fetch failure).
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
            // Drop the cached name too — the next login may be a different student.
            _studentName.value = null
            preloadedStudentName = null
            onLoggedOut()
        }
    }
}