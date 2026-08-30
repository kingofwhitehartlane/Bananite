package ir.mums.stufood.ui.screens

import android.graphics.BitmapFactory
import android.util.Log
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

private const val TAG = "LoginViewModel"
private const val FRIENDLY_NETWORK_ERROR = "Couldn't reach the server. Check your connection and try again."

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
 * NOTE: [loadLoginPage] is intentionally *not* called from [init] anymore. This
 * ViewModel is shared by both the initial Login screen and the screen you land on
 * after logging out (see [ir.mums.stufood.ui.navigation.Screen.Login] and
 * [ir.mums.stufood.ui.navigation.Screen.Logout] in `MainActivity`) — `init` only
 * ever runs once for the lifetime of this ViewModel, so relying on it meant the
 * captcha silently never refreshed after a logout. `LoginScreen` now calls
 * [loadLoginPage] itself via a `LaunchedEffect(Unit)` every time it enters
 * composition, which covers both cold start and "logged out, back at Login" the
 * same way.
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

    private val _rememberMe = MutableStateFlow(true)
    val rememberMe: StateFlow<Boolean> = _rememberMe

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var currentPageData: StufoodRepository.LoginPageData? = null
    private var prefsLoaded = false

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
            prefsLoaded = true
        }
    }

    fun updateUsername(v: String) { _username.value = v }
    fun updatePassword(v: String) { _password.value = v }
    fun updateCaptcha(v: String) { _captcha.value = v }
    fun updateRememberMe(v: Boolean) { _rememberMe.value = v }

    /** Re-fetches the login page (which gives us a fresh captcha). Safe to call repeatedly. */
    fun loadLoginPage() {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                // FIX: landing on the Login screen (including the ↻ retry button)
                // used to GET Default.aspx with whatever cookies were already sitting
                // in the (Application-scoped, process-lifetime) cookie jar — e.g. a
                // half-expired session left over from before the app was backgrounded.
                // The server can respond to that with a different page shape (no
                // #body_imgCaptcha at all), so captchaSrc came back empty and every
                // subsequent ↻ tap kept re-requesting with those same stale cookies —
                // it never recovered without a full force-close (which happened to
                // wipe the in-memory jar). Reaching this screen at all means we're
                // treating the session as not-usable, so always start the request as
                // a clean, cookie-less client.
                repo.clearSession()

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
                Log.e(TAG, "Failed to load login page", t)
                _errorMessage.value = FRIENDLY_NETWORK_ERROR
                _uiState.value = LoginUiState.PageReady(captcha = null)
            }
        }
    }

    /** Submits the login form. On success, calls onLoggedIn(). */
    fun submit(onLoggedIn: () -> Unit) {
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

                when (val result = repo.login(user, pass, cap)) {
                    is StufoodRepository.LoginResult.Success -> {
                        _errorMessage.value = null
                        _captcha.value = ""
                        
                        // Pre-fetch name into repository cache before updating UI state
                        runCatching { repo.fetchStudentFullName() }
                            .onFailure { t -> Log.e(TAG, "Failed to pre-fetch student name after login", t) }

                        _uiState.value = LoginUiState.Success
                        onLoggedIn()
                    }
                    is StufoodRepository.LoginResult.Failure -> {
                        _errorMessage.value = result.message
                        loadLoginPage()
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Login request failed", t)
                _errorMessage.value = FRIENDLY_NETWORK_ERROR
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