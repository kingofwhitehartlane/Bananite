package ir.mums.stufood.ui.screens

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.BananiteApp
import ir.mums.stufood.data.StufoodRepository
import ir.mums.stufood.data.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

private const val TAG = "LoginViewModel"
private const val FRIENDLY_NETWORK_ERROR = "Couldn't reach the server. Check your connection and try again."
data class LoginError(val message: String, val id: Long = System.nanoTime())

/**
 * Holds the state of the login screen.
 *
 * State machine:
 *   Idle -> (screen enters composition) -> LoadingPage
 *        -> PageReady (captcha image shown) -> (user submits) -> Submitting
 *        -> Success  OR  Failure (back to PageReady with error)
 *
 * We also pre-fill username/password from DataStore if "remember me" was ticked last time.
 *
 * NOTE: [loadLoginPage] is deliberately not called from `init`. The same ViewModel
 * serves the cold-start login and the login you land on after logging out
 * ([ir.mums.stufood.ui.navigation.Screen.Login]); `init` runs only once, so the
 * captcha would never refresh after a logout. `LoginScreen` calls [loadLoginPage]
 * from a `LaunchedEffect(Unit)` every time it enters composition instead.
 */
class LoginViewModel(
    private val repo: StufoodRepository = BananiteApp.instance.repository,
    private val prefs: UserPrefs = BananiteApp.instance.userPrefs
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

    private val _rememberMe = MutableStateFlow(true)
    val rememberMe: StateFlow<Boolean> = _rememberMe

    private val _errorMessage = MutableStateFlow<LoginError?>(null)
    val errorMessage: StateFlow<LoginError?> = _errorMessage
    private fun fail(msg: String) { _errorMessage.value = LoginError(msg) }

    private var loadJob: Job? = null

    init {
        // Pre-fill from saved prefs (once). The actual login-page/captcha fetch is
        // triggered by the screen itself — see the class doc comment above.
        viewModelScope.launch {
            val savedUser = prefs.username.first()
            val savedPass = prefs.password.first()
            val savedRemember = prefs.rememberMe.first()
            _username.value = savedUser
            _password.value = savedPass
            _rememberMe.value = savedRemember
        }
    }

    fun updateUsername(v: String) { _username.value = v }
    fun updatePassword(v: String) { _password.value = v }
    fun updateCaptcha(v: String) { _captcha.value = v }
    fun updateRememberMe(v: Boolean) { _rememberMe.value = v }

    /** Re-fetches the login page (which gives us a fresh captcha). Safe to call repeatedly. */
    fun loadLoginPage() {
        if (loadJob?.isActive == true) return // ignore double taps
        _uiState.value = LoginUiState.Loading
        loadJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                repo.clearSession()
                val data = repo.fetchLoginPage()
                val bitmap = data.captchaImage?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
                awaitMinimumSpinnerTime(startedAt)
                if (bitmap != null) {
                    _uiState.value = LoginUiState.PageReady(captcha = bitmap)
                } else {
                    fail("Couldn't load captcha image. Tap retry.")
                    _uiState.value = LoginUiState.PageReady(captcha = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load login page", t)
                awaitMinimumSpinnerTime(startedAt)
                fail(FRIENDLY_NETWORK_ERROR)
                _uiState.value = LoginUiState.PageReady(captcha = null)
            }
        }
    }

    /** Keeps the spinner on screen for at least [minMillis] so a fast failure (e.g. no
     *  network) doesn't just flash and look like a glitch. Doesn't slow down the happy
     *  path when the server is actually slow — only tops up the difference. */
    private suspend fun awaitMinimumSpinnerTime(startedAtMillis: Long, minMillis: Long = 450L) {
        val elapsed = System.currentTimeMillis() - startedAtMillis
        if (elapsed < minMillis) delay(minMillis - elapsed)
    }

    /** Submits the login form. On success, calls onLoggedIn(). */
    fun submit(onLoggedIn: () -> Unit) {
        if (_uiState.value is LoginUiState.Submitting) return
        val user = _username.value.trim()
        val pass = _password.value
        val cap = _captcha.value.trim()

        if (user.isEmpty() || pass.isEmpty() || cap.isEmpty()) {
            fail("All fields are required.")
            return
        }

        _uiState.value = LoginUiState.Submitting
        viewModelScope.launch {
            try {
                when (val result = repo.login(user, pass, cap)) {
                    is StufoodRepository.LoginResult.Success -> {
                        prefs.saveCredentials(user, pass, _rememberMe.value)
                        _errorMessage.value = null
                        _captcha.value = ""

                        runCatching { repo.fetchStudentFullName() }
                            .onFailure { t -> Log.e(TAG, "Failed to pre-fetch student name after login", t) }

                        _uiState.value = LoginUiState.Success
                        onLoggedIn()
                    }
                    is StufoodRepository.LoginResult.Failure -> {
                        fail(result.message)
                        _captcha.value = ""
                        loadLoginPage()
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Login request failed", t)
                fail(FRIENDLY_NETWORK_ERROR)
                _captcha.value = ""
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