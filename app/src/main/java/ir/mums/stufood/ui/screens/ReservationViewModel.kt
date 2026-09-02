package ir.mums.stufood.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.BananiteApp
import ir.mums.stufood.data.StufoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine

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
    private val repo: StufoodRepository = BananiteApp.instance.repository
) : ViewModel() {
    
    val bounciness: StateFlow<String> = BananiteApp.instance.userPrefs.bounciness.stateIn(viewModelScope, SharingStarted.Lazily, "medium")
    val creditTransitionType: StateFlow<String> = BananiteApp.instance.userPrefs.creditTransitionType.stateIn(viewModelScope, SharingStarted.Lazily, "fade")
    
    // NEW: Global Disable Override
    val disableAllAnimations: StateFlow<Boolean> = BananiteApp.instance.userPrefs.disableAllAnimations.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val effectiveBounciness: StateFlow<String> = combine(bounciness, disableAllAnimations) { b, d -> if (d) "none" else b }
        .stateIn(viewModelScope, SharingStarted.Lazily, "medium")

    val effectiveCreditTransitionType: StateFlow<String> = combine(creditTransitionType, disableAllAnimations) { c, d -> if (d) "fade" else c }
        .stateIn(viewModelScope, SharingStarted.Lazily, "fade")

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

    val hapticFeedbackEnabled: StateFlow<Boolean> = BananiteApp.instance.userPrefs.hapticFeedbackEnabled.stateIn(viewModelScope, SharingStarted.Lazily, true)

    // Remembers the last meal the user actually picked (never the "0" placeholder) so
    // a plain page reload (pull-to-refresh, or the very first load) can silently
    // restore it. The site itself resets the meal dropdown to the placeholder on a
    // fresh GET — this isn't something we can fix server-side, only paper over here.
    private var lastSelectedMeal: String? = null

    fun load() {
        val existing = (_uiState.value as? ReservationUiState.Ready)?.page
        _statusText.value = "در حال بارگذاری صفحه…"
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
        _statusText.value = "در حال تعویض وعده…"
        repo.selectMeal(page, mealValue).also { _statusText.value = null }
    }

    fun today() = withPage { page ->
        if (!page.today.isUsable) return@withPage page
        _statusText.value = "رفتن به امروز…" // Jumping to today…
        repo.clickToday(page).also { _statusText.value = null }
    }

    fun nextWeek() = withPage { page ->
        if (!page.nextWeek.isUsable) return@withPage page
        _statusText.value = "رفتن به هفته بعد…"
        repo.clickNextWeek(page).also { _statusText.value = null }
    }

    fun lastWeek() = withPage { page ->
        if (!page.lastWeek.isUsable) return@withPage page
        _statusText.value = "رفتن به هفته قبل…"
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

    /** Called after the user confirms "آیا از کنسل کردن غذا اطمینان دارید؟". */
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
                Log.e(TAG, "exchangeFieldName is null (day=${day.index}, field=${option.fieldName})")
                postError("مشکلی در باز کردن دیالوگ تبادل پیش آمد (اطلاعات نامعتبر)")
                return
            }

            val page = (_uiState.value as? ReservationUiState.Ready)?.page
            if (page == null) {
                Log.e(TAG, "uiState not Ready, was ${_uiState.value::class.simpleName}")
                postError("مشکلی در باز کردن دیالوگ تبادل پیش آمد")
                return
            }

            val foodId = option.exchangeFoodId.orEmpty()
            val baseDialog = page.exchangeDialog ?: StufoodRepository.ExchangeDialogData(
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
            
            // Give a clean dialog on every open: the page still carries the previous
            // search's destStudentLabel in its parsed modal — drop it, and reset the
            // selected type + visibility so they always agree.
            val dialogData = baseDialog.copy(
                selectedExchangeType   = baseDialog.exchangeTypes.firstOrNull()?.second ?: "1",
                showChangeFoodFields   = false,
                showStudentSearchFields = false,
                studentNumber          = null,
                destStudentLabel       = null
            )
            
            _exchangeDialog.value = ExchangeDialogUiState(
                day       = day,
                option    = option,
                dialog    = dialogData,
                foodId    = foodId,
                mealValue = page.selectedMeal
            )

        } catch (t: Throwable) {
            Log.e(TAG, "CRASH in openExchangeDialog: ${t::class.simpleName}: ${t.message}", t)
            postError("خطای غیرمنتظره‌ای در باز کردن دیالوگ تبادل رخ داد")
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
                    // Type "2": fetch cafeteria options via AJAX PageMethod
                    val fetchedSelfOptions = repo.fetchExchangeSelfOptions(current.mealValue, current.foodId)
                    // Prepend the placeholder to prevent "0" from showing as default
                    val selfOptions = listOf("انتخاب کنید" to "0") + fetchedSelfOptions
                    
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
                // Fetch food options via AJAX PageMethod
                val fetchedFoodOptions = repo.fetchExchangeFoodOptions(current.mealValue, current.foodId, value)
                // Prepend the placeholder to prevent "0" from showing as default
                val foodOptions = listOf("انتخاب غذا" to "0") + fetchedFoodOptions
                
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
                // Always surface the latest "destination student" result. copy() only
                // touches destStudentLabel, so showStudentSearchFields stays true.
                val newDestLabel = updated.exchangeDialog?.destStudentLabel
                _exchangeDialog.value = current.copy(
                    dialog = current.dialog.copy(destStudentLabel = newDestLabel),
                    studentNumber = studentNumber,
                    busy = false
                )
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