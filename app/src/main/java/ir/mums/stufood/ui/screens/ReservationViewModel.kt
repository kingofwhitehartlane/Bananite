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
 * Days and nav buttons are whatever StufoodRepository parsed off the *current* page —
 * their number, order, and enabled/disabled state can change between loads (the site
 * adds/removes rows and locks days dynamically), so nothing here assumes a fixed shape.
 * Selections are keyed by each day's live field name, not a positional index, since a
 * day's index can shift when rows appear/disappear.
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

    private val _selectedMeal = MutableStateFlow("")
    val selectedMeal: StateFlow<String> = _selectedMeal

    // day.fieldName -> chosen option value (not label). Pre-filled with each usable
    // day's current server-side value once the page loads; locked days are left out.
    private val _daySelections = MutableStateFlow<Map<String, String>>(emptyMap())
    val daySelections: StateFlow<Map<String, String>> = _daySelections

    fun updateMeal(v: String) { _selectedMeal.value = v }

    fun updateDaySelection(fieldName: String, optionValue: String) {
        _daySelections.value = _daySelections.value.toMutableMap().apply { put(fieldName, optionValue) }
    }

    private fun applyLoadedPage(page: StufoodRepository.ReservationPage) {
        _selectedMeal.value = page.mealOptions.firstOrNull()?.second ?: ""
        _daySelections.value = page.days.filter { it.isUsable }.associate { it.fieldName to it.currentValue }
    }

    /** Loads the reservation page and pre-fills dropdowns from server state. */
    fun load() {
        _uiState.value = ReservationUiState.Loading
        _statusText.value = "Loading reservation page…"
        viewModelScope.launch {
            try {
                val page = repo.fetchReservationPage()
                applyLoadedPage(page)
                _uiState.value = ReservationUiState.Ready(page)
                _statusText.value = null
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to load page: ${t.message}"
                _uiState.value = ReservationUiState.Idle
            }
        }
    }

    fun applyMeal() {
        val page = (_uiState.value as? ReservationUiState.Ready)?.page ?: return
        val meal = _selectedMeal.value
        if (meal.isEmpty()) return
        _uiState.value = ReservationUiState.Working
        viewModelScope.launch {
            try {
                val updated = repo.selectMeal(page, meal)
                applyLoadedPage(updated)
                _uiState.value = ReservationUiState.Ready(updated)
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to change meal: ${t.message}"
                _uiState.value = ReservationUiState.Ready(page)
            }
        }
    }

    fun nextWeek() {
        val page = (_uiState.value as? ReservationUiState.Ready)?.page ?: return
        if (!page.nextWeek.isUsable) return
        _uiState.value = ReservationUiState.Working
        _statusText.value = "Going to next week…"
        viewModelScope.launch {
            try {
                val updated = repo.clickNextWeek(page)
                applyLoadedPage(updated)
                _uiState.value = ReservationUiState.Ready(updated)
                _statusText.value = null
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to go to next week: ${t.message}"
                _uiState.value = ReservationUiState.Ready(page)
            }
        }
    }

    fun lastWeek() {
        val page = (_uiState.value as? ReservationUiState.Ready)?.page ?: return
        if (!page.lastWeek.isUsable) return
        _uiState.value = ReservationUiState.Working
        _statusText.value = "Going to last week…"
        viewModelScope.launch {
            try {
                val updated = repo.clickLastWeek(page)
                applyLoadedPage(updated)
                _uiState.value = ReservationUiState.Ready(updated)
                _statusText.value = null
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to go to last week: ${t.message}"
                _uiState.value = ReservationUiState.Ready(page)
            }
        }
    }

    /** Reserves every day currently shown in the dropdowns, one postback per day. */
    fun reserveAllDays() {
        val page = (_uiState.value as? ReservationUiState.Ready)?.page ?: return
        _uiState.value = ReservationUiState.Working
        _errorMessage.value = null

        viewModelScope.launch {
            val result = repo.reserveDays(
                page,
                _daySelections.value,
                onProgress = { step -> _statusText.value = step }
            )
            when (result) {
                is StufoodRepository.ReservationResult.Success -> {
                    applyLoadedPage(result.finalPage)
                    _uiState.value = ReservationUiState.Ready(result.finalPage)
                    _statusText.value = if (result.skipped.isEmpty()) {
                        "Done! Week reserved."
                    } else {
                        "Reserved, but skipped: ${result.skipped.joinToString("; ")}"
                    }
                }
                is StufoodRepository.ReservationResult.Failure -> {
                    _errorMessage.value = result.message
                    _statusText.value = null
                    load()
                }
            }
        }
    }

    /** Reserves just one day (used by the per-day dropdown's own confirm button). */
    fun reserveDay(fieldName: String) {
        val page = (_uiState.value as? ReservationUiState.Ready)?.page ?: return
        val day = page.days.firstOrNull { it.fieldName == fieldName } ?: return
        val value = _daySelections.value[fieldName] ?: return
        if (!day.isUsable) {
            _errorMessage.value = "${day.dayLabel} isn't available right now" +
                (day.lockedReason?.let { " ($it)" } ?: ".")
            return
        }
        _uiState.value = ReservationUiState.Working
        _statusText.value = "Reserving ${day.dayLabel}…"
        viewModelScope.launch {
            try {
                val updated = repo.selectDayDiet(page, day, value)
                if (updated == null) {
                    _errorMessage.value = "${day.dayLabel} became unavailable — reloading."
                    load()
                    return@launch
                }
                applyLoadedPage(updated)
                _uiState.value = ReservationUiState.Ready(updated)
                _statusText.value = "${day.dayLabel} reserved."
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to reserve ${day.dayLabel}: ${t.message}"
                _uiState.value = ReservationUiState.Ready(page)
            }
        }
    }
}

sealed class ReservationUiState {
    object Idle : ReservationUiState()
    object Loading : ReservationUiState()
    data class Ready(val page: StufoodRepository.ReservationPage) : ReservationUiState()
    object Working : ReservationUiState()
}