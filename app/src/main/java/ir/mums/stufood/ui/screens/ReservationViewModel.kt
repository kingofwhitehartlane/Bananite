package ir.mums.stufood.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.StufoodApp
import ir.mums.stufood.data.StufoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "ReservationViewModel"

/** Generic, user-facing message — we never show raw exception/server text in the UI. */
private const val FRIENDLY_ERROR = "Something went wrong. Please try again in a moment."
private const val FRIENDLY_LOAD_ERROR = "Couldn't load the reservation page. Pull down to try again."

/**
 * Reservation screen state.
 *
 * Two different kinds of postback are handled differently:
 *
 * - Page-wide actions (meal change, week navigation, "today", pull-to-refresh) can
 *   legitimately change most or all days — a full dimmed [ReservationUiState.Working]
 *   refresh is correct and expected here.
 * - Per-day actions (picking a cafeteria/diet, cancelling, opening/confirming the
 *   exchange flow) only ever intend to touch one day. Those go through [withDay]
 *   instead: the page stays in [ReservationUiState.Ready] the whole time (nothing
 *   else on screen moves), only [busyDayIndex] changes so the UI can show a small
 *   spinner on that one card, and the response is merged back in a way that
 *   preserves every *other* day's object identity — see [mergeDay].
 *
 * The food-exchange ("تبادل غذا") flow lives in its own small state machine
 * ([exchangeDialog]) layered on top of the same page state: opening the dialog is a
 * per-day action (goes through [withDay]) that also stashes the parsed dialog fields;
 * every subsequent step inside the dialog (switching type, picking a cafeteria/food,
 * searching a student, confirming) goes through [withExchangeDialog], which keeps
 * re-merging the *day* that owns the dialog the same way [withDay] does.
 */
class ReservationViewModel(
    private val repo: StufoodRepository = StufoodApp.instance.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReservationUiState>(ReservationUiState.Idle)
    val uiState: StateFlow<ReservationUiState> = _uiState

    private val _statusText = MutableStateFlow<String?>(null)
    val statusText: StateFlow<String?> = _statusText

    // FIX: errorMessage used to be a plain MutableStateFlow<String?> that was never
    // reset to null. StateFlow conflates equal values, so the *second* (and every
    // subsequent) identical error string was silently swallowed — the snackbar's
    // LaunchedEffect(error) never re-fired because, as far as the StateFlow was
    // concerned, nothing had changed. Wrapping each error in a small event object
    // with its own nonce/id guarantees every call to postError() is a distinct value,
    // so the snackbar shows every time, even for back-to-back identical failures.
    data class ErrorEvent(val message: String, val id: Long = System.nanoTime())

    private val _errorMessage = MutableStateFlow<ErrorEvent?>(null)
    val errorMessage: StateFlow<ErrorEvent?> = _errorMessage

    private fun postError(message: String) {
        _errorMessage.value = ErrorEvent(message)
    }

    private val _pendingCancel = MutableStateFlow<PendingCancel?>(null)
    val pendingCancel: StateFlow<PendingCancel?> = _pendingCancel

    private val _pendingCancelExchange = MutableStateFlow<PendingCancelExchange?>(null)
    val pendingCancelExchange: StateFlow<PendingCancelExchange?> = _pendingCancelExchange

    private val _exchangeDialog = MutableStateFlow<ExchangeDialogUiState?>(null)
    val exchangeDialog: StateFlow<ExchangeDialogUiState?> = _exchangeDialog

    /**
     * Index of the single day currently mid-postback (cafeteria/diet/cancel/exchange
     * pick). Only this day shows a busy spinner or animates when the response lands —
     * every other card on the page stays completely still.
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
                Log.e(TAG, "Failed to load reservation page", t)
                postError(FRIENDLY_LOAD_ERROR)
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
                Log.e(TAG, "Page-wide action failed", t)
                postError(FRIENDLY_ERROR)
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
                Log.e(TAG, "Per-day action failed (day=$dayIndex)", t)
                postError(FRIENDLY_ERROR)
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

    // ---------------------------------------------------------------------
    // Food exchange ("تبادل غذا")
    // ---------------------------------------------------------------------

    /** Taps "درخواست تبادل با دانشجویان" — opens the exchange dialog for [option]. */
    fun openExchangeDialog(day: StufoodRepository.DayInfo, option: StufoodRepository.DietOption) {
        try {
            if (option.exchangeFieldName == null) {
                postError("DEBUG: exchangeFieldName is null (day=${day.index}, field=${option.fieldName})")
                return
            }

            val page = (_uiState.value as? ReservationUiState.Ready)?.page
            if (page == null) {
                postError("DEBUG: uiState not Ready, was ${_uiState.value::class.simpleName}")
                return
            }

            val foodId = option.exchangeFoodId.orEmpty()

            val dialogData = page.exchangeDialog ?: StufoodRepository.ExchangeDialogData(
                exchangeTypes = listOf(
                    "تبادل غذا" to "1",
                    "تعویض غذا" to "2",
                    "تعویض غذا با سایرین" to "3"
                ),
                selectedExchangeType  = "1",
                selfOptions           = emptyList(),
                selectedSelf          = null,
                foodOptions           = emptyList(),
                selectedFood          = null,
                showChangeFoodFields  = false,
                showStudentSearchFields = false,
                studentNumber         = null,
                destStudentLabel      = null
            )

            _exchangeDialog.value = ExchangeDialogUiState(
                day       = day,
                option    = option,
                dialog    = dialogData,
                foodId    = foodId,
                mealValue = page.selectedMeal
            )

        } catch (t: Throwable) {
            postError("DEBUG CRASH: ${t::class.simpleName}: ${t.message}")
        }
    }

    fun dismissExchangeDialog() {
        _exchangeDialog.value = null
    }

    fun selectExchangeType(value: String) {
        val current = _exchangeDialog.value ?: return
        _exchangeDialog.value = current.copy(busy = true)

        viewModelScope.launch {
            try {
                val showChangeFood = value == "2"
                val showStudentSearch = value == "3"

                var updatedDialog = current.dialog.copy(
                    selectedExchangeType = value,
                    showChangeFoodFields = showChangeFood,
                    showStudentSearchFields = showStudentSearch
                )

                if (showChangeFood) {
                    // Type "2": fetch cafeteria options via AJAX PageMethod,
                    // exactly like the site's getSeachSelfData() JS function.
                    val selfOptions = repo.fetchExchangeSelfOptions(current.mealValue, current.foodId)
                    updatedDialog = updatedDialog.copy(
                        selfOptions = selfOptions,
                        selectedSelf = "0",
                        foodOptions = emptyList(),
                        selectedFood = "0"
                    )
                } else {
                    // Type "1" or "3": no AJAX needed, just hide the dropdown sections.
                    updatedDialog = updatedDialog.copy(
                        foodOptions = emptyList(),
                        selectedSelf = null,
                        selectedFood = null
                    )
                }

                _exchangeDialog.value = current.copy(dialog = updatedDialog, busy = false)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to select exchange type", t)
                postError(FRIENDLY_ERROR)
                _exchangeDialog.value = current.copy(busy = false)
            }
        }
    }

    fun selectExchangeSelf(value: String) {
        val current = _exchangeDialog.value ?: return
        if (value == "0") return
        _exchangeDialog.value = current.copy(busy = true)

        viewModelScope.launch {
            try {
                // Fetch food options via AJAX PageMethod, exactly like the site's
                // dpSelectSelf.change JS handler.
                val foodOptions = repo.fetchExchangeFoodOptions(current.mealValue, current.foodId, value)
                val updatedDialog = current.dialog.copy(
                    selectedSelf = value,
                    foodOptions = foodOptions,
                    selectedFood = "0"
                )
                _exchangeDialog.value = current.copy(dialog = updatedDialog, busy = false)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to fetch exchange food options", t)
                postError(FRIENDLY_ERROR)
                _exchangeDialog.value = current.copy(busy = false)
            }
        }
    }

    fun selectExchangeFood(value: String) {
        val current = _exchangeDialog.value ?: return
        // No postback — on the site, picking a food from the dropdown is purely
        // client-side. The value is read by btnExchangeFood() JS at confirm time.
        val updatedDialog = current.dialog.copy(selectedFood = value)
        _exchangeDialog.value = current.copy(dialog = updatedDialog)
    }

    /** Updates the locally-tracked student number as the user types. */
    fun updateExchangeStudentNumber(value: String) {
        val current = _exchangeDialog.value ?: return
        _exchangeDialog.value = current.copy(studentNumber = value)
    }

    fun searchDestinationStudent(studentNumber: String) {
        val current = _exchangeDialog.value ?: return
        val page = (_uiState.value as? ReservationUiState.Ready)?.page ?: return

        val request = StufoodRepository.ExchangeRequest(
            foodId = current.foodId,
            exchangeType = current.dialog.selectedExchangeType,
            studentNumber = studentNumber
        )

        _exchangeDialog.value = current.copy(busy = true, studentNumber = studentNumber)

        viewModelScope.launch {
            try {
                val updated = repo.searchDestinationStudent(page, request)
                _uiState.value = ReservationUiState.Ready(mergeDay(page, updated, current.day.index))

                // The server re-renders the whole page, so the parsed dialog will have
                // reset show/hide flags and selected type back to their initial HTML
                // state. We keep the user's current dialog state and only pull the
                // destination student label from the response.
                val dialogData = updated.exchangeDialog
                if (dialogData != null) {
                    _exchangeDialog.value = current.copy(
                        dialog = current.dialog.copy(
                            destStudentLabel = dialogData.destStudentLabel
                        ),
                        studentNumber = studentNumber,
                        busy = false
                    )
                } else {
                    _exchangeDialog.value = current.copy(busy = false)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to search destination student", t)
                postError(FRIENDLY_ERROR)
                _exchangeDialog.value = current.copy(busy = false)
            }
        }
    }

    /** Taps "تایید و ثبت درخواست" inside the exchange dialog. */
    fun confirmExchange() {
        val current = _exchangeDialog.value ?: return
        val page = (_uiState.value as? ReservationUiState.Ready)?.page ?: return

        val request = StufoodRepository.ExchangeRequest(
            foodId = current.foodId,
            exchangeType = current.dialog.selectedExchangeType,
            selectedSelf = current.dialog.selectedSelf ?: "0",
            selectedFood = current.dialog.selectedFood ?: "0",
            studentNumber = current.studentNumber
        )

        _exchangeDialog.value = current.copy(busy = true)

        viewModelScope.launch {
            try {
                val updated = repo.confirmExchange(page, request)
                _uiState.value = ReservationUiState.Ready(mergeDay(page, updated, current.day.index))
                // Close the dialog — the page now reflects the new exchange state.
                _exchangeDialog.value = null
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to confirm exchange", t)
                postError(FRIENDLY_ERROR)
                _exchangeDialog.value = current.copy(busy = false)
            }
        }
    }

    /** Taps the "انصراف از تبادل غذا" icon — shows the confirm dialog. */
    fun requestCancelExchange(day: StufoodRepository.DayInfo, option: StufoodRepository.DietOption) {
        // FIX: this used to `return` with zero feedback when cancelExchangeFieldName
        // was null — a pure no-op tap. The icon that triggers this is only shown
        // when exchangePending (== cancelExchangeFieldName != null) is true, so this
        // branch should be rare, but if it's ever hit (stale option reference, a
        // parsing gap, etc.) the user deserves to see *something* instead of the
        // tap silently doing nothing.
        if (option.cancelExchangeFieldName == null) {
            postError(FRIENDLY_ERROR)
            return
        }
        _pendingCancelExchange.value = PendingCancelExchange(day, option)
    }

    fun dismissCancelExchangeRequest() {
        _pendingCancelExchange.value = null
    }

    fun confirmCancelExchange() {
        val pending = _pendingCancelExchange.value ?: return
        _pendingCancelExchange.value = null
        withDay(pending.day.index) { page -> repo.cancelExchange(page, pending.option) }
    }

    data class PendingCancel(
        val day: StufoodRepository.DayInfo,
        val option: StufoodRepository.DietOption
    )

    data class PendingCancelExchange(
        val day: StufoodRepository.DayInfo,
        val option: StufoodRepository.DietOption
    )

    data class ExchangeDialogUiState(
        val day: StufoodRepository.DayInfo,
        val option: StufoodRepository.DietOption,
        val dialog: StufoodRepository.ExchangeDialogData,
        val busy: Boolean = false,
        /** The "attre" value from btnSellFood — needed for AJAX calls and the confirm postback. */
        val foodId: String = "",
        /** Current meal value — needed as a parameter for the AJAX PageMethods. */
        val mealValue: String = "",
        /** Student number typed by the user for type "3" — tracked locally since there's
         *  no postback until the search button is tapped. */
        val studentNumber: String = ""
    )
}

sealed class ReservationUiState {
    object Idle : ReservationUiState()
    object Loading : ReservationUiState()
    data class Ready(val page: StufoodRepository.ReservationPage) : ReservationUiState()
    /** Keeps the previous page visible (dimmed) while a page-wide postback is in flight. */
    data class Working(val previousPage: StufoodRepository.ReservationPage) : ReservationUiState()
}