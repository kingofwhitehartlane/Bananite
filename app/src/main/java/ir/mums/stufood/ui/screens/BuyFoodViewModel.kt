package ir.mums.stufood.ui.screens

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.BananiteApp
import ir.mums.stufood.data.StufoodRepository
import ir.mums.stufood.util.JalaliCalendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "BuyFoodViewModel"
private const val FRIENDLY_ERROR = "Something went wrong. Please try again in a moment."

/** The site's "همه وعده ها" option (also detected by label, see [isAllMeals]). */
private const val ALL_MEALS_VALUE = "-1"

class BuyFoodViewModel(
    private val repo: StufoodRepository = BananiteApp.instance.repository
) : ViewModel() {

    companion object {
        const val DEFAULT_AUTO_INTERVAL_MS = 2_000L
        const val MIN_AUTO_INTERVAL_MS = 0L
        const val MAX_AUTO_INTERVAL_MS = 30_000L
    }

    data class UiState(
        val page: StufoodRepository.BuyFoodPage? = null,
        val loading: Boolean = true,
        val busy: Boolean = false,
        val date: String = "",
        val meal: String = ALL_MEALS_VALUE,
        /** Shows the red "وعده را انتخاب نمایید" hint under the meal dropdown. */
        val mealError: Boolean = false,
        val autoRefresh: Boolean = false,
        val soundEnabled: Boolean = false,
        /** Minimum time from the START of one auto search to the START of the next. */
        val autoIntervalMs: Long = BuyFoodViewModel.DEFAULT_AUTO_INTERVAL_MS,
        /** Incremented every time an auto cycle (re)starts — drives the countdown ring. */
        val refreshTick: Int = 0,
        /** SystemClock.elapsedRealtime() when the current auto cycle began — drives the ring. */
        val cycleStartedAt: Long = 0L
    )

    data class ErrorEvent(val message: String, val id: Long = System.nanoTime())

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _error = MutableStateFlow<ErrorEvent?>(null)
    val error: StateFlow<ErrorEvent?> = _error

    /** Fires when a search turns up food that can be received/bought (and sound is on). */
    private val _foodAvailable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val foodAvailable: SharedFlow<Unit> = _foodAvailable.asSharedFlow()

    val hapticFeedbackEnabled: StateFlow<Boolean> =
        BananiteApp.instance.userPrefs.hapticFeedbackEnabled
            .stateIn(viewModelScope, SharingStarted.Lazily, true)

    private var started = false
    private var autoJob: Job? = null

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    fun load(force: Boolean = false) {
        if (started && !force) return
        started = true
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val page = repo.fetchBuyFoodPage()
                _state.update {
                    it.copy(
                        page = page,
                        loading = false,
                        date = defaultDate(page),
                        meal = page.selectedMeal
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "load failed", t)
                started = false
                _error.value = ErrorEvent("Couldn't load the page. Check your connection and retry.")
                _state.update { it.copy(loading = false) }
            }
        }
    }

    /** Earliest food waiting for exchange, otherwise today (both Jalali). */
    private fun defaultDate(page: StufoodRepository.BuyFoodPage): String {
        val earliest = page.waitingFoods.mapNotNull { JalaliCalendar.parse(it.date) }.minOrNull()
        return (earliest ?: JalaliCalendar.today()).format()
    }

    // ------------------------------------------------------------------
    // Filters
    // ------------------------------------------------------------------

    fun setDate(v: String) = _state.update { it.copy(date = v) }

    fun setMeal(v: String) {
        _state.update { it.copy(meal = v, mealError = false) }
        // Auto-refresh can't run on "all meals" — stop it if the user switches to that.
        val page = _state.value.page
        if (_state.value.autoRefresh && page != null && page.isAllMeals(v)) {
            stopAutoRefresh()
            _state.update { it.copy(mealError = true) }
        }
    }

    private fun StufoodRepository.BuyFoodPage.isAllMeals(mealValue: String): Boolean {
        if (mealValue == ALL_MEALS_VALUE) return true
        val label = mealOptions.firstOrNull { it.second == mealValue }?.first.orEmpty()
        val normalized = label.replace(" ", "").replace("\u200c", "")
        return normalized.contains("همهوعده")
    }

    // ------------------------------------------------------------------
    // Search (manual + auto share one code path)
    // ------------------------------------------------------------------

    fun search() {
        if (_state.value.busy) return
        viewModelScope.launch { performSearch(manual = true) }
    }

    private suspend fun performSearch(manual: Boolean) {
        val s = _state.value
        val page = s.page ?: return
        if (s.busy) return

        // The site errors out when "all meals" is searched, so mirror that locally.
        if (page.isAllMeals(s.meal)) {
            stopAutoRefresh()
            _state.update { it.copy(mealError = true) }
            return
        }

        val date = s.date.map { if (it in '\u06F0'..'\u06F9') '0' + (it - '\u06F0') else it }
            .joinToString("").trim()
        if (JalaliCalendar.parse(date) == null) {
            if (manual) _error.value = ErrorEvent("Date must look like 1405/07/09")
            return
        }

        _state.update { it.copy(busy = true, date = date, mealError = false) }
        try {
            val updated = repo.searchBuyFood(page, date, s.meal)
            _state.update { it.copy(page = updated, busy = false) }
            maybeNotify(previous = page, updated = updated)
        } catch (e: CancellationException) {
            _state.update { it.copy(busy = false) }
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "search failed", t)
            if (manual) _error.value = ErrorEvent(FRIENDLY_ERROR) // auto cycles fail quietly
            _state.update { it.copy(busy = false) }
        }
    }

    /**
     * Rings only when the "available to receive" list gained at least one item compared
     * with what was there before this search — never for food that was already listed.
     */
    private fun maybeNotify(
        previous: StufoodRepository.BuyFoodPage,
        updated: StufoodRepository.BuyFoodPage
    ) {
        if (!_state.value.soundEnabled) return
        fun key(f: StufoodRepository.ExchangeableFood) = "${f.meal}/${f.menu}/${f.food}/${f.self}"
        val before = previous.availableFoods.groupingBy(::key).eachCount()
        val after = updated.availableFoods.groupingBy(::key).eachCount()
        val hasNewItem = after.any { (k, n) -> n > (before[k] ?: 0) }
        if (hasNewItem) _foodAvailable.tryEmit(Unit)
    }

    // ------------------------------------------------------------------
    // Toggles
    // ------------------------------------------------------------------

    /** Turning auto-refresh on/off also turns the sound on/off; sound stays freely toggleable afterwards. */
    fun setAutoRefresh(enabled: Boolean) {
        if (!enabled) {
            stopAutoRefresh()
            return
        }
        val s = _state.value
        val page = s.page ?: return
        if (page.isAllMeals(s.meal)) {
            _state.update { it.copy(mealError = true) }
            return
        }
        _state.update { it.copy(autoRefresh = true, soundEnabled = true, mealError = false) }
        autoJob?.cancel()
        autoJob = viewModelScope.launch {
            // search -> wait for it to finish -> wait out whatever is left of the
            // interval (none if the search itself took longer) -> search again.
            while (isActive) {
                val startedAt = SystemClock.elapsedRealtime()
                _state.update { it.copy(refreshTick = it.refreshTick + 1, cycleStartedAt = startedAt) } // restarts the ring
                performSearch(manual = false)
                val remaining = _state.value.autoIntervalMs - (SystemClock.elapsedRealtime() - startedAt)
                if (remaining > 0) delay(remaining)
            }
        }
    }

    fun setAutoInterval(ms: Long) {
        _state.update {
            it.copy(autoIntervalMs = ms.coerceIn(MIN_AUTO_INTERVAL_MS, MAX_AUTO_INTERVAL_MS))
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        _state.update { it.copy(soundEnabled = enabled) }
    }

    private fun stopAutoRefresh() {
        autoJob?.cancel()
        autoJob = null
        _state.update { it.copy(autoRefresh = false, soundEnabled = false) }
    }

    /** Called on entering/leaving the screen: both toggles always start OFF. */
    fun resetToggles() {
        autoJob?.cancel()
        autoJob = null
        _state.update { it.copy(autoRefresh = false, soundEnabled = false, mealError = false, busy = false) }
    }

    // ------------------------------------------------------------------
    // Receive
    // ------------------------------------------------------------------

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