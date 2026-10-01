package ir.mums.stufood.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.BananiteApp
import ir.mums.stufood.data.StufoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "BuyFoodViewModel"
private const val FRIENDLY_ERROR = "Something went wrong. Please try again in a moment."

class BuyFoodViewModel(
    private val repo: StufoodRepository = BananiteApp.instance.repository
) : ViewModel() {

    data class UiState(
        val page: StufoodRepository.BuyFoodPage? = null,
        val loading: Boolean = true,
        val busy: Boolean = false,
        val date: String = "",
        val meal: String = "-1"
    )

    data class ErrorEvent(val message: String, val id: Long = System.nanoTime())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _error = MutableStateFlow<ErrorEvent?>(null)
    val error: StateFlow<ErrorEvent?> = _error

    val hapticFeedbackEnabled: StateFlow<Boolean> =
        BananiteApp.instance.userPrefs.hapticFeedbackEnabled
            .stateIn(viewModelScope, SharingStarted.Lazily, true)

    private var started = false

    fun load(force: Boolean = false) {
        if (started && !force) return
        started = true
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val page = repo.fetchBuyFoodPage()
                _state.value = UiState(
                    page = page, loading = false, date = page.date, meal = page.selectedMeal
                )
            } catch (t: Throwable) {
                Log.e(TAG, "load failed", t)
                started = false
                _error.value = ErrorEvent("Couldn't load the page. Check your connection and retry.")
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun setDate(v: String) = _state.update { it.copy(date = v) }
    fun setMeal(v: String) = _state.update { it.copy(meal = v) }

    fun search() {
        val s = _state.value
        val page = s.page ?: return
        if (s.busy) return
        val date = s.date.map { if (it in '\u06F0'..'\u06F9') '0' + (it - '\u06F0') else it }
            .joinToString("").trim()
        if (!Regex("^\\d{4}/\\d{2}/\\d{2}$").matches(date)) {
            _error.value = ErrorEvent("Date must look like 1405/07/09")
            return
        }
        _state.update { it.copy(busy = true, date = date) }
        viewModelScope.launch {
            try {
                val updated = repo.searchBuyFood(page, date, s.meal)
                _state.update { it.copy(page = updated, busy = false) }
            } catch (t: Throwable) {
                Log.e(TAG, "search failed", t)
                _error.value = ErrorEvent(FRIENDLY_ERROR)
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun receive(row: StufoodRepository.ExchangeableFood) {
        val s = _state.value
        val page = s.page ?: return
        if (s.busy || row.receive == null) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val updated = repo.BuyFood(page, row)
                _state.update { it.copy(page = updated ?: page, busy = false) }
            } catch (t: Throwable) {
                Log.e(TAG, "receive failed", t)
                _error.value = ErrorEvent(FRIENDLY_ERROR)
                _state.update { it.copy(busy = false) }
            }
        }
    }
}