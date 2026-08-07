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
 * Two modes:
 *   1. Manual — user walks through the same steps as the Python script but via UI:
 *        load page -> pick meal -> next week -> for each day, pick diet + radio.
 *      Useful when you want to see what's happening or pick non-default options.
 *   2. Auto — one tap runs the whole week with the script's defaults (ناهار,
 *      سلف پردیس, first radio).
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

    // Selected meal + diet (visible text)
    private val _selectedMeal = MutableStateFlow("ناهار")
    val selectedMeal: StateFlow<String> = _selectedMeal

    private val _selectedDiet = MutableStateFlow("سلف پردیس")
    val selectedDiet: StateFlow<String> = _selectedDiet

    private var currentPage: StufoodRepository.ReservationPage? = null

    fun updateMeal(v: String) { _selectedMeal.value = v }
    fun updateDiet(v: String) { _selectedDiet.value = v }

    /** Loads the initial reservation page so we can show meal options. */
    fun load() {
        _uiState.value = ReservationUiState.Loading
        _statusText.value = "Loading reservation page…"
        viewModelScope.launch {
            try {
                val page = repo.fetchReservationPage()
                currentPage = page
                _uiState.value = ReservationUiState.Ready(page)
                _statusText.value = null
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to load page: ${t.message}"
                _uiState.value = ReservationUiState.Idle
            }
        }
    }

    /**
     * Runs the whole "reserve food for next week" flow with the current meal/diet
     * selections. Reports progress via statusText.
     */
    fun reserveWeek() {
        val meal = _selectedMeal.value
        val diet = _selectedDiet.value
        _uiState.value = ReservationUiState.Working
        _errorMessage.value = null

        viewModelScope.launch {
            val result = repo.reserveWeekWithDefaults(
                mealText = meal,
                dietText = diet,
                onProgress = { step -> _statusText.value = step }
            )
            when (result) {
                is StufoodRepository.ReservationResult.Success -> {
                    currentPage = result.finalPage
                    _uiState.value = ReservationUiState.Ready(result.finalPage)
                    _statusText.value = "Done! Week reserved."
                }
                is StufoodRepository.ReservationResult.Failure -> {
                    _errorMessage.value = result.message
                    _statusText.value = null
                    // Try to reload so the user can retry
                    load()
                }
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
