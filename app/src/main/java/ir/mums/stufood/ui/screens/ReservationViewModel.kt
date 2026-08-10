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
 * Two different kinds of postback are handled differently:
 *
 * - Page-wide actions (meal change, week navigation, "today", pull-to-refresh) can
 *   legitimately change most or all days — a full dimmed [ReservationUiState.Working]
 *   refresh is correct and expected here.
 * - Per-day actions (picking a cafeteria/diet, cancelling) only ever intend to touch
 *   one day. Those go through [withDay] instead: the page stays in [ReservationUiState.Ready]
 *   the whole time (nothing else on screen moves), only [busyDayIndex] changes so the UI
 *   can show a small spinner on that one card, and the response is merged back in a way
 *   that preserves every *other* day's object identity — see [mergeDay].
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

    private val _pendingCancel = MutableStateFlow<PendingCancel?>(null)
    val pendingCancel: StateFlow<PendingCancel?> = _pendingCancel

    /**
     * Index of the single day currently mid-postback (cafeteria/diet/cancel pick).
     * Only this day shows a busy spinner or animates when the response lands — every
     * other card on the page stays completely still.
     */
    private val _busyDayIndex = MutableStateFlow<Int?>(null)
    val busyDayIndex: StateFlow<Int?> = _busyDayIndex

    // Remembers the last meal the user actually picked (never the "0" placeholder) so
    // a plain page reload (pull-to-refresh, or the very first load) can silently
    // restore it. The site itself resets the meal dropdown to the placeholder on a
    // fresh GET — this isn't something we can fix server-side, only paper over here.
    private var lastSelectedMeal: String? = null

    fun load() {
        val existing = (_uiState.value as? ReservationUiState.Ready)?.page
        _statusText.value = "\u062f\u0631 \u062d\u0627\u0644 \u0628\u0627\u0631\u06af\u0630\u0627\u0631\u06cc \u0635\u0641\u062d\u0647\u2026" // Loading reservation page…
        _uiState.value = if (existing != null) ReservationUiState.Working(existing) else ReservationUiState.Loading
        viewModelScope.launch {
            try {
                var page = repo.fetchReservationPage()
                val remembered = lastSelectedMeal
                if (page.selectedMeal == "0" && !remembered.isNullOrBlank() && remembered != "0") {
                    // Server forgot our meal choice on this fresh GET — reselect it
                    // silently so the user doesn't have to redo it after every refresh.
                    page = repo.selectMeal(page, remembered)
                } else if (page.selectedMeal != "0") {
                    lastSelectedMeal = page.selectedMeal
                }
                _uiState.value = ReservationUiState.Ready(page)
                _statusText.value = null
            } catch (t: Throwable) {
                _errorMessage.value = "Failed to load page: ${t.message}"
                _statusText.value = null
                _uiState.value = existing?.let { ReservationUiState.Ready(it) } ?: ReservationUiState.Idle
            }
        }
    }

    // ---------------------------------------------------------------------
    // Page-wide actions
    // ---------------------------------------------------------------------

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
        lastSelectedMeal = mealValue
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
        _statusText.value = "\u0631\u0641\u062a\u0646 \u0628\u0647 \u0647\u0641\u062a\u0647 \u0628\u0639\u062f\u2026"
        repo.clickNextWeek(page).also { _statusText.value = null }
    }

    fun lastWeek() = withPage { page ->
        if (!page.lastWeek.isUsable) return@withPage page
        _statusText.value = "\u0631\u0641\u062a\u0646 \u0628\u0647 \u0647\u0641\u062a\u0647 \u0642\u0628\u0644\u2026"
        repo.clickLastWeek(page).also { _statusText.value = null }
    }

    // ---------------------------------------------------------------------
    // Per-day actions — only [dayIndex] gets a busy flag / animates
    // ---------------------------------------------------------------------

    private inline fun withDay(dayIndex: Int, crossinline block: suspend (StufoodRepository.ReservationPage) -> StufoodRepository.ReservationPage?) {
        val page = (_uiState.value as? ReservationUiState.Ready)?.page ?: return
        _busyDayIndex.value = dayIndex
        viewModelScope.launch {
            try {
                val updated = block(page)
                _uiState.value = ReservationUiState.Ready(
                    if (updated != null) mergeDay(previous = page, updated = updated, dayIndex = dayIndex) else page
                )
            } catch (t: Throwable) {
                _errorMessage.value = "Network error: ${t.message}"
            } finally {
                _busyDayIndex.value = null
            }
        }
    }

    /**
     * Keeps every day *except* [dayIndex] pointing at the exact same [StufoodRepository.DayInfo]
     * instance it had before this postback. The server re-renders the whole page on every
     * request, so a fresh parse hands back brand-new (if usually value-equal) objects for
     * every day; without this, incidental differences between two parses of the same HTML
     * would make unrelated day cards replay their change animation for no visible reason.
     */
    private fun mergeDay(
        previous: StufoodRepository.ReservationPage,
        updated: StufoodRepository.ReservationPage,
        dayIndex: Int
    ): StufoodRepository.ReservationPage {
        val mergedDays = updated.days.map { newDay ->
            if (newDay.index == dayIndex) newDay
            else previous.days.firstOrNull { it.index == newDay.index } ?: newDay
        }
        return updated.copy(days = mergedDays)
    }

    fun selectCafeteria(day: StufoodRepository.DayInfo, value: String) {
        if (value == "0") return
        withDay(day.index) { page -> repo.selectCafeteria(page, day, value) }
    }

    fun selectDiet(day: StufoodRepository.DayInfo, option: StufoodRepository.DietOption) {
        withDay(day.index) { page -> repo.selectDiet(page, option) }
    }

    /** Called when the user taps the cancel (minus) icon — shows the confirm dialog. */
    fun requestCancel(day: StufoodRepository.DayInfo, option: StufoodRepository.DietOption) {
        if (option.cancelFieldName == null) return
        _pendingCancel.value = PendingCancel(day, option)
    }

    fun dismissCancelRequest() {
        _pendingCancel.value = null
    }

    /** Called after the user confirms "\u0622\u06cc\u0627 \u0627\u0632 \u06a9\u0646\u0633\u0644 \u06a9\u0631\u062f\u0646 \u0642\u0630\u0627 \u0627\u0637\u0645\u06cc\u0646\u0627\u0646 \u062f\u0627\u0631\u06cc\u062f\u061f". */
    fun confirmCancel() {
        val pending = _pendingCancel.value ?: return
        _pendingCancel.value = null
        withDay(pending.day.index) { page -> repo.cancelDiet(page, pending.option) }
    }

    data class PendingCancel(
        val day: StufoodRepository.DayInfo,
        val option: StufoodRepository.DietOption
    )
}

sealed class ReservationUiState {
    object Idle : ReservationUiState()
    object Loading : ReservationUiState()
    data class Ready(val page: StufoodRepository.ReservationPage) : ReservationUiState()
    /** Keeps the previous page visible (dimmed) while a page-wide postback is in flight. */
    data class Working(val previousPage: StufoodRepository.ReservationPage) : ReservationUiState()
}