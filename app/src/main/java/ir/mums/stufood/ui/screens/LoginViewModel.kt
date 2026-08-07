package ir.mums.stufood.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.StufoodApp
import ir.mums.stufood.data.StufoodRepository
import ir.mums.stufood.data.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Holds the state of the login screen.
 *
 * State machine:
 *   Idle -> (user taps "Load captcha" or just opens screen) -> LoadingPage
 *        -> PageReady (captcha image shown) -> (user submits) -> Submitting
 *        -> Success  OR  Failure (back to PageReady with error)
 *
 * We also pre-fill username/password from DataStore if "remember me" was ticked last time.
 */
class LoginViewModel(
    private val repo: StufoodRepository = StufoodApp.instance.repository,
    private val prefs: UserPrefs = StufoodApp.instance.userPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val uiState: StateFlow<LoginUiState> = _uiState

    // Editable fields exposed separately so the UI can bind them with two-way binding
    // without recomposing the whole state tree on every keystroke.
    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _captcha = MutableStateFlow("")
    val captcha: StateFlow<String> = _captcha

    private val _rememberMe = MutableStateFlow(false)
    val rememberMe: StateFlow<Boolean> = _rememberMe

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var currentPageData: StufoodRepository.LoginPageData? = null

    init {
        // Pre-fill from saved prefs, then fetch the login page + captcha.
        viewModelScope.launch {
            val savedUser = prefs.username.first()
            val savedPass = prefs.password.first()
            val savedRemember = prefs.rememberMe.first()
            _username.value = savedUser
            _password.value = savedPass
            _rememberMe.value = savedRemember
            loadLoginPage()
        }
    }

    fun updateUsername(v: String) { _username.value = v }
    fun updatePassword(v: String) { _password.value = v }
    fun updateCaptcha(v: String) { _captcha.value = v }
    fun updateRememberMe(v: Boolean) { _rememberMe.value = v }

    /** Re-fetches the login page (which gives us a fresh captcha). */
    fun loadLoginPage() {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                val data = repo.fetchLoginPage()
                currentPageData = data
                val bitmap = data.captchaImage?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
                if (bitmap != null) {
                    _uiState.value = LoginUiState.PageReady(captcha = bitmap)
                } else {
                    _errorMessage.value = "Couldn't load captcha image. Tap retry."
                    _uiState.value = LoginUiState.PageReady(captcha = null)
                }
            } catch (t: Throwable) {
                _errorMessage.value = "Network error: ${t.message}"
                _uiState.value = LoginUiState.PageReady(captcha = null)
            }
        }
    }

    /** Submits the login form. On success, calls onLoggedIn(). */
    fun submit(onLoggedIn: () -> Unit) {
        val page = currentPageData ?: return
        val user = _username.value.trim()
        val pass = _password.value
        val cap = _captcha.value.trim()

        if (user.isEmpty() || pass.isEmpty() || cap.isEmpty()) {
            _errorMessage.value = "All fields are required."
            return
        }

        _uiState.value = LoginUiState.Submitting
        viewModelScope.launch {
            try {
                // Save / clear saved credentials based on the checkbox.
                prefs.saveCredentials(user, pass, _rememberMe.value)

                when (val result = repo.login(user, pass, cap, page)) {
                    is StufoodRepository.LoginResult.Success -> {
                        _errorMessage.value = null
                        _captcha.value = ""
                        _uiState.value = LoginUiState.Success
                        onLoggedIn()
                    }
                    is StufoodRepository.LoginResult.Failure -> {
                        _errorMessage.value = result.message
                        // Refresh the captcha — the old one is single-use.
                        loadLoginPage()
                    }
                }
            } catch (t: Throwable) {
                _errorMessage.value = "Network error: ${t.message}"
                loadLoginPage()
            }
        }
    }
}

sealed class LoginUiState {
    /** Initial page load (fetching login form + captcha). */
    object Loading : LoginUiState()
    /** Login page is ready, captcha image is shown (or null if it failed). */
    data class PageReady(val captcha: ImageBitmap?) : LoginUiState()
    /** User tapped submit, waiting for server response. */
    object Submitting : LoginUiState()
    /** Login succeeded — UI should navigate away. */
    object Success : LoginUiState()
}
