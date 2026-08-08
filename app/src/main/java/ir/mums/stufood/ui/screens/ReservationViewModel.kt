package ir.mums.stufood.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.StufoodApp
import ir.mums.stufood.data.StufoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Reservation screen state.
 *
 * Every action here (meal change, week nav, cafeteria pick, diet pick, cancel) is a
 * single postback that returns a brand-new [StufoodRepository.ReservationPage] — we
 * just swap the whole page in, exactly like a browser re-rendering the page. Nothing
 * about the number of days, their order, or their state is assumed to be fixed.
 */
class ReservationViewModel(
    private val repo: StufoodRepository = StufoodApp.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReservationUiState>(ReservationUiState.Idle)
    val uiState: StateFlow<ReservationUiState> = _uiState

    private val _statusText = MutableStateFlow<String?>(null)
    val statusText: StateFlow<String?> = _statusText

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    // Set while a cancel confirmation dialog ("\u0622\u06cc\u0627 \u0627\u0632 \u06a9\u0646\u0633\u0644 \u06a9\u0631\u062f\u0646 \u0642\u0630\u0627 \u0627\u0637\u0645\u06cc\u0646\u0627\u0646 \u062f\u0627\u0631\u06cc\u062f\u061f")
    // should be shown for the given diet option.
    private val _pendingCancel = MutableStateFlow<StufoodRepository.DietOption?>(null)
    val pendingCancel: StateFlow<StufoodRepository.DietOption?> = _pendingCancel

    fun load() {
        _uiState.value = ReservationUiState.Loading
        _statusText.value = "\u062f\u0631 \u062d\u0627\u0644 \u0628\u0627\u0631\u06af\u0630\u0627\u0631\u06cc \u0635\u0641\u062d\u0647\u2026" // Loading reservation page…
        viewModelScope.launch {
            try {
                val page = repo.fetchReservationPage()
                _uiState.value = ReservationUiState.Ready(page)
                _statusText.value = null
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to load page: ${t.message}"
                _uiState.value = ReservationUiState.Idle
            }
        }
    }

    private inline fun withPage(crossinline block: suspend (StufoodRepository.ReservationPage) -> StufoodRepository.ReservationPage?) {
        val page = (_uiState.value as? ReservationUiState.Ready)?.page ?: return
        _uiState.value = ReservationUiState.Working(page)
        viewModelScope.launch {
            try {
                val updated = block(page)
                _uiState.value = ReservationUiState.Ready(updated ?: page)
            } catch (t: Throwable) {
                _errorMessage.value = "Network error: ${t.message}"
                _uiState.value = ReservationUiState.Ready(page)
            }
        }
    }

    fun selectMeal(mealValue: String) = withPage { page ->
        _statusText.value = "\u062f\u0631 \u062d\u0627\u0644 \u062a\u0639\u0648\u06cc\u0636 \u0648\u0639\u062f\u0647\u2026"
        repo.selectMeal(page, mealValue).also { _statusText.value = null }
    }

    fun today() = withPage { page ->
        if (!page.today.isUsable) return@withPage page
        _statusText.value = "\u0631\u0641\u062a\u0646 \u0628\u0647 \u0627\u0645\u0631\u0648\u0632\u2026" // Jumping to today…
        repo.clickToday(page).also { _statusText.value = null }
    }

    fun nextWeek() = withPage { page ->
        if (!page.nextWeek.isUsable) return@withPage page
        _statusText.value = "\u0631\u0641\u062a\u0646 \u0628\u0647 \u0647\u0641\u062a\u0647 \u0628\u0639\u062f\u2026" // Going to next week…
        repo.clickNextWeek(page).also { _statusText.value = null }
    }

    fun lastWeek() = withPage { page ->
        if (!page.lastWeek.isUsable) return@withPage page
        _statusText.value = "\u0631\u0641\u062a\u0646 \u0628\u0647 \u0647\u0641\u062a\u0647 \u0642\u0628\u0644\u2026" // Going to previous week…
        repo.clickLastWeek(page).also { _statusText.value = null }
    }

    fun selectCafeteria(day: StufoodRepository.DayInfo, value: String) = withPage { page ->
        if (value == "0") return@withPage page
        _statusText.value = "${day.dateLabel}: \u062f\u0631 \u062d\u0627\u0644 \u0628\u0627\u0631\u06af\u0630\u0627\u0631\u06cc \u0633\u0644\u0641\u200c\u0647\u0627\u2026"
        repo.selectCafeteria(page, day, value).also { _statusText.value = null }
    }

    fun selectDiet(option: StufoodRepository.DietOption) = withPage { page ->
        _statusText.value = "\u062f\u0631 \u062d\u0627\u0644 \u0631\u0632\u0631\u0648 ${option.label}\u2026"
        repo.selectDiet(page, option).also { _statusText.value = null }
    }

    /** Called when the user taps the cancel (minus) icon — shows the confirm dialog. */
    fun requestCancel(option: StufoodRepository.DietOption) {
        if (option.cancelFieldName == null) return
        _pendingCancel.value = option
    }

    fun dismissCancelRequest() {
        _pendingCancel.value = null
    }

    /** Called after the user confirms "\u0622\u06cc\u0627 \u0627\u0632 \u06a9\u0646\u0633\u0644 \u06a9\u0631\u062f\u0646 \u0642\u0630\u0627 \u0627\u0637\u0645\u06cc\u0646\u0627\u0646 \u062f\u0627\u0631\u06cc\u062f\u061f". */
    fun confirmCancel() {
        val option = _pendingCancel.value ?: return
        _pendingCancel.value = null
        withPage { page ->
            _statusText.value = "\u062f\u0631 \u062d\u0627\u0644 \u06a9\u0646\u0633\u0644 \u06a9\u0631\u062f\u0646\u2026" // Cancelling…
            val updated = repo.cancelDiet(page, option)
            _statusText.value = null
            updated
        }
    }
}

sealed class ReservationUiState {
    object Idle : ReservationUiState()
    object Loading : ReservationUiState()
    data class Ready(val page: StufoodRepository.ReservationPage) : ReservationUiState()
    /** Keeps the previous page visible (dimmed) while a postback is in flight. */
    data class Working(val previousPage: StufoodRepository.ReservationPage) : ReservationUiState()
}
