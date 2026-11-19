package ir.mums.stufood.ui.screens

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.mums.stufood.BananiteApp
import ir.mums.stufood.data.StufoodRepository
import ir.mums.stufood.data.UserPrefs
import ir.mums.stufood.service.AutoSearchBridge
import ir.mums.stufood.service.AutoSearchService
import ir.mums.stufood.service.InProcessNotifier
import ir.mums.stufood.service.LiveSupport
import ir.mums.stufood.util.JalaliCalendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import ir.mums.stufood.util.FoodMatcher

private const val TAG = "BuyFoodViewModel"
private const val FRIENDLY_ERROR = "Something went wrong. Please try again in a moment."

/** The site's "همه وعده ها" option (also detected by label, see [isAllMeals]). */
private const val ALL_MEALS_VALUE = "-1"

/** Identity of a food row. Positions shift between refreshes, so never compare by index. */
private fun foodKey(f: StufoodRepository.ExchangeableFood) = "${f.meal}/${f.menu}/${f.food}/${f.self}"

private fun normalizeDate(s: String) = s.map { if (it in '\u06F0'..'\u06F9') '0' + (it - '\u06F0') else it }
    .joinToString("").trim()

/**
 * The site answers a purchase with an inline alert('...'). On success it reads
 * "خرید غذا موفقیت آمیز بود، ..." ("purchase was successful ...").
 */
private fun isSuccessMessage(msg: String): Boolean {
    val n = msg.replace(" ", "").replace("\u200c", "")
    return n.contains("موفقیت آمیز") && !n.contains("ناموفق")
}

/** Pulls a price like "160000 ریال" / "16,000 تومان" out of the site's alert and shows it in Toman. */
private fun extractPriceToman(msg: String): String? {
    val m = Regex("([0-9\u06F0-\u06F9,\u066C]+)\\s*(ریال|تومان)").find(msg) ?: return null
    val digits = m.groupValues[1]
        .map { if (it in '\u06F0'..'\u06F9') '0' + (it - '\u06F0') else it }
        .filter { it.isDigit() }
        .joinToString("")
    val amount = digits.toLongOrNull() ?: return null
    val toman = if (m.groupValues[2] == "ریال") amount / 10 else amount
    return String.format(java.util.Locale.US, "%,d", toman) + "T"
}

class BuyFoodViewModel(
    private val repo: StufoodRepository = BananiteApp.instance.repository,
    private val prefs: UserPrefs = BananiteApp.instance.userPrefs
) : ViewModel() {

    companion object {
        const val DEFAULT_AUTO_INTERVAL_MS = 3_000L
        const val MIN_AUTO_INTERVAL_MS = 0L
        const val MAX_AUTO_INTERVAL_MS = 30_000L
        /** Preferred self when reading the day's menu (matched fuzzily; falls back to the first self). */
        const val TARGET_SELF = "سلف پردیس"
    }

    /** One food of the day. List order IS the buying priority (top = first choice). */
    data class FoodChoice(
        val name: String,
        val priceRial: Long?,
        val priceToman: String?,
        val half: Boolean,
        val enabled: Boolean = true
    )

    data class FoodPickerState(
        val loading: Boolean = false,
        val foods: List<FoodChoice> = emptyList(),
        /** Shown when the lookup found nothing or failed. */
        val message: String? = null,
        /** Set when the day is already reserved: name of the reserved food. */
        val reservedFood: String? = null,
        /** The self the list was read from. */
        val selfUsed: String? = null,
        val forDate: String = "",
        val forMeal: String = ""
    )

    data class UiState(
        val page: StufoodRepository.BuyFoodPage? = null,
        val loading: Boolean = true,
        val busy: Boolean = false,
        val date: String = "",
        val meal: String = ALL_MEALS_VALUE,
        /** Shows the red "وعده را انتخاب نمایید" hint under the meal dropdown. */
        val mealError: Boolean = false,
        val autoRefresh: Boolean = false,
        /**
         * When ON, the moment a search turns up a NEW food, the first buyable food in the
         * list is bought immediately (no confirmation). Off by default, never saved.
         */
        val autoBuy: Boolean = false,
        /** Minimum time from the START of one auto search to the START of the next. */
        val autoIntervalMs: Long = BuyFoodViewModel.DEFAULT_AUTO_INTERVAL_MS,
        /** Incremented every time an auto cycle (re)starts — drives the countdown ring. */
        val refreshTick: Int = 0,
        /** SystemClock.elapsedRealtime() when the current auto cycle began — drives the ring. */
        val cycleStartedAt: Long = 0L,
        /** Foods picked for auto-buy. Empty = not configured (auto-buy buys the first food, as before). */
        val foodPicker: FoodPickerState = FoodPickerState()
    )

    data class ErrorEvent(val message: String, val id: Long = System.nanoTime())

    /**
     * Outcome of a "Buy" attempt, shown as a popup.
     * [message] is the site's own alert text (Persian); null means the site said nothing.
     */
    data class PurchaseResult(
        val success: Boolean,
        val message: String?,
        val foodName: String? = null,
        val price: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val _error = MutableStateFlow<ErrorEvent?>(null)
    val error: StateFlow<ErrorEvent?> = _error

    private val _purchaseResult = MutableStateFlow<PurchaseResult?>(null)
    val purchaseResult: StateFlow<PurchaseResult?> = _purchaseResult
    fun dismissPurchaseResult() { _purchaseResult.value = null }

    /** Fires when a search turns up food that can be bought (and sound is on). */
    private val _foodAvailable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val foodAvailable: SharedFlow<Unit> = _foodAvailable.asSharedFlow()

    val hapticFeedbackEnabled: StateFlow<Boolean> =
        BananiteApp.instance.userPrefs.hapticFeedbackEnabled
            .stateIn(viewModelScope, SharingStarted.Lazily, true)

    // Notifications > Buy food settings (both OFF by default, so behaviour is unchanged after an update).
    val runInBackground: StateFlow<Boolean> =
        prefs.runInBackground.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val liveNotifications: StateFlow<Boolean> =
        prefs.liveNotifications.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    /** Settings > Notifications > "Food found sound". Default ON (same as before). Mutes only that sound. */
    val foodFoundSound: StateFlow<Boolean> =
        prefs.foodFoundSound.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    /** true until DataStore says otherwise, so the first-time popup can't flash by mistake. */
    val setupPromptDone: StateFlow<Boolean> =
        prefs.setupPromptDone.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun completeSetupPrompt() { viewModelScope.launch { prefs.saveSetupPromptDone(true) } }
    fun setRunInBackground(on: Boolean) { viewModelScope.launch { prefs.saveRunInBackground(on) } }
    fun setLiveNotifications(on: Boolean) { viewModelScope.launch { prefs.saveLiveNotifications(on) } }

    private var started = false

    init {
        // Interval is a saved setting; the toggles themselves are never saved.
        viewModelScope.launch {
            val saved = prefs.autoSearchIntervalMs.first()
            _state.update {
                it.copy(autoIntervalMs = saved.coerceIn(MIN_AUTO_INTERVAL_MS, MAX_AUTO_INTERVAL_MS))
            }
        }
    }
    private var autoJob: Job? = null
    private var pickerJob: Job? = null

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

    fun setDate(v: String) = _state.update {
        it.copy(date = v, foodPicker = if (normalizeDate(v) == it.foodPicker.forDate) it.foodPicker else FoodPickerState())
    }

    fun setMeal(v: String) {
        _state.update {
            it.copy(
                meal = v, mealError = false,
                foodPicker = if (v == it.foodPicker.forMeal) it.foodPicker else FoodPickerState()
            )
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
            handleNewFood(previous = page, updated = updated)
        } catch (e: CancellationException) {
            _state.update { it.copy(busy = false) }
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "search failed", t)
            if (manual) _error.value = ErrorEvent(FRIENDLY_ERROR) // auto cycles fail quietly
            _state.update { it.copy(busy = false) }
        }
    }

    /** True when the "available to purchase" list gained at least one item vs. before this search. */
    private fun hasNewFood(
        previous: StufoodRepository.BuyFoodPage,
        updated: StufoodRepository.BuyFoodPage
    ): Boolean {
        val before = previous.availableFoods.groupingBy(::foodKey).eachCount()
        val after = updated.availableFoods.groupingBy(::foodKey).eachCount()
        return after.any { (k, n) -> n > (before[k] ?: 0) }
    }

    /** Foods in [updated] that were not in [previous] (multiset difference). */
    private fun newFoods(
        previous: StufoodRepository.BuyFoodPage,
        updated: StufoodRepository.BuyFoodPage
    ): List<StufoodRepository.ExchangeableFood> {
        val left = previous.availableFoods.groupingBy(::foodKey).eachCount().toMutableMap()
        return updated.availableFoods.filter { f ->
            val n = left[foodKey(f)] ?: 0
            if (n > 0) { left[foodKey(f)] = n - 1; false } else true
        }
    }

    /**
     * Runs after every search. Only reacts to food that is NEW compared with what was
     * listed before this search — never to food that was already there.
     *
     *  1. Rings the notification sound (if sound is on).
     *  2. Stops auto-search, so the Buy buttons aren't disabled by a running search.
     *  3. If auto-buy is on, buys the first buyable food right away (no confirmation).
     */
    private fun handleNewFood(
        previous: StufoodRepository.BuyFoodPage,
        updated: StufoodRepository.BuyFoodPage
    ) {
        val s = _state.value
        val picks = if (s.autoBuy) s.foodPicker.foods else emptyList()
        val filterOn = picks.isNotEmpty()
        val candidates = picks.map { FoodMatcher.Candidate(it.name, it.priceRial) }

        // Position in the user's list if this row is an ENABLED food, else null. The self column
        // is deliberately ignored. The row is matched against ALL of the day's foods first (so
        // "قیمه" can never land on "قیمه نیم پرس"), and only then is "is it enabled?" checked.
        fun rankOf(row: StufoodRepository.ExchangeableFood): Int? {
            val i = FoodMatcher.bestMatch(row.food, candidates) ?: return null
            return if (picks[i].enabled) i else null
        }

        var fresh = newFoods(previous, updated)
        if (filterOn) fresh = fresh.filter { rankOf(it) != null } // others: no sound, no stop, no buy
        if (fresh.isEmpty()) return

        if (s.autoRefresh && foodFoundSound.value) _foodAvailable.tryEmit(Unit)

        // Keep the live notification session: it ends with a result (found / bought / failed).
        if (s.autoRefresh) stopAutoRefresh(endSession = false)

        val buyable = when {
            !s.autoBuy -> null
            // Several wanted foods can be on sale at once (e.g. at 12:00): take the one
            // highest in the user's list.
            filterOn -> updated.availableFoods
                .filter { it.receive != null && rankOf(it) != null }
                .minByOrNull { rankOf(it)!! }
            else -> updated.availableFoods.firstOrNull { it.receive != null }
        }
        if (buyable != null) {
            AutoSearchBridge.onBuying(buyable.food)
            receive(buyable)
        } else {
            AutoSearchBridge.onFound(fresh.first().food)
        }
    }
    // ------------------------------------------------------------------
    // Toggles
    // ------------------------------------------------------------------

    /** Turning auto-refresh on/off also turns the sound on/off; sound stays freely toggleable afterwards. */
    fun setAutoRefresh(
        enabled: Boolean,
        // Explicit values let "Enable now" start right after saving, before DataStore echoes the change.
        background: Boolean = runInBackground.value,
        live: Boolean = liveNotifications.value
    ) {
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
        _state.update { it.copy(autoRefresh = true, mealError = false) }

        // The normal "food found / purchase result" notification is ALWAYS sent. On top of that:
        //   Run in the background  -> foreground service + wake lock (+ a small ongoing notification)
        //   Live notifications     -> progress/Now Bar style ongoing notification (only where supported)
        // The loop itself always runs below, in this ViewModel.
        val liveOn = live && LiveSupport.status().supported
        val mealLabel = page.mealOptions.firstOrNull { it.second == s.meal }?.first.orEmpty()
        AutoSearchBridge.begin(s.date, mealLabel, s.autoIntervalMs, fancy = liveOn)
        runCatching {
            if (background) AutoSearchService.start(BananiteApp.instance)
            else InProcessNotifier.start(BananiteApp.instance, showOngoing = liveOn)
        }.onFailure { Log.e(TAG, "couldn't start notifications / background service", it) }

        autoJob?.cancel()
        autoJob = viewModelScope.launch {
            // search -> wait for it to finish -> wait out whatever is left of the
            // interval (none if the search itself took longer) -> search again.
            while (isActive) {
                val startedAt = SystemClock.elapsedRealtime()
                _state.update { it.copy(refreshTick = it.refreshTick + 1, cycleStartedAt = startedAt) } // restarts the ring
                AutoSearchBridge.onSearchStart(startedAt)
                performSearch(manual = false)
                AutoSearchBridge.onSearchEnd()
                val remaining = _state.value.autoIntervalMs - (SystemClock.elapsedRealtime() - startedAt)
                if (remaining > 0) delay(remaining)
            }
        }
    }

    fun setAutoInterval(ms: Long) {
        _state.update {
            it.copy(autoIntervalMs = ms.coerceIn(MIN_AUTO_INTERVAL_MS, MAX_AUTO_INTERVAL_MS))
        }
        AutoSearchBridge.setInterval(_state.value.autoIntervalMs)
    }

    /** Persists the current interval (called when the slider is released). */
    fun saveAutoInterval() {
        val ms = _state.value.autoIntervalMs
        viewModelScope.launch { prefs.saveAutoSearchInterval(ms) }
    }

    /** The bell toggle: same switch as Settings > Notifications > Food found sound. */
    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { prefs.saveFoodFoundSound(enabled) }
    }

    /** Auto-buy: instantly buys the first food when a new one appears. Never saved. */
    fun setAutoBuy(enabled: Boolean) {
        _state.update { it.copy(autoBuy = enabled) }
    }

    fun loadAutoBuyFoods(force: Boolean = false) {
        val s = _state.value
        val page = s.page ?: return
        if (s.foodPicker.loading) return

        val date = normalizeDate(s.date)
        if (!force && s.foodPicker.foods.isNotEmpty() &&
            s.foodPicker.forDate == date && s.foodPicker.forMeal == s.meal) return

        val jalali = JalaliCalendar.parse(date)
        if (jalali == null) {
            _state.update { it.copy(foodPicker = FoodPickerState(message = "Date must look like 1405/07/09")) }
            return
        }
        if (page.isAllMeals(s.meal)) {
            _state.update { it.copy(foodPicker = FoodPickerState(message = "Pick a meal first.")) }
            return
        }
        val mealLabel = page.mealOptions.firstOrNull { it.second == s.meal }?.first.orEmpty()

        _state.update { it.copy(foodPicker = FoodPickerState(loading = true, forDate = date, forMeal = s.meal)) }
        pickerJob?.cancel()
        pickerJob = viewModelScope.launch {
            try {
                val r = repo.fetchDayFoods(jalali, mealLabel, TARGET_SELF)
                val half = FoodMatcher.halfFlags(r.foods.map { FoodMatcher.Candidate(it.name, it.priceRial) })
                _state.update {
                    it.copy(
                        foodPicker = FoodPickerState(
                            foods = r.foods.mapIndexed { i, f -> FoodChoice(f.name, f.priceRial, f.priceToman, half[i]) },
                            message = r.problem,
                            reservedFood = r.reservedFood,
                            selfUsed = r.selfUsed,
                            forDate = date,
                            forMeal = s.meal
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "food list failed", t)
                _state.update { it.copy(foodPicker = FoodPickerState(message = FRIENDLY_ERROR)) }
            }
        }
    }

    fun setFoodEnabled(index: Int, enabled: Boolean) = _state.update { st ->
        val list = st.foodPicker.foods.toMutableList()
        if (index in list.indices) list[index] = list[index].copy(enabled = enabled)
        st.copy(foodPicker = st.foodPicker.copy(foods = list))
    }

    /** [delta] = -1 moves a food up (higher priority), +1 moves it down. */
    fun moveFood(index: Int, delta: Int) = _state.update { st ->
        val list = st.foodPicker.foods.toMutableList()
        val to = index + delta
        if (index in list.indices && to in list.indices) {
            val item = list.removeAt(index)
            list.add(to, item)
        }
        st.copy(foodPicker = st.foodPicker.copy(foods = list))
    }

    /** [endSession] = false keeps the live notification alive (a purchase is about to be shown). */
    private fun stopAutoRefresh(endSession: Boolean = true) {
        autoJob?.cancel()
        autoJob = null
        _state.update { it.copy(autoRefresh = false) }
        if (endSession) AutoSearchBridge.end()
    }

    override fun onCleared() {
        // Activity is gone for good: the loop dies with this ViewModel, so remove the notification too.
        autoJob?.cancel()
        AutoSearchBridge.end()
        super.onCleared()
    }

    /** Called on entering/leaving the screen: all toggles always start OFF. */
    fun resetToggles() {
        autoJob?.cancel()
        autoJob = null
        pickerJob?.cancel()
        pickerJob = null
        AutoSearchBridge.end()
        _state.update {
            it.copy(
                autoRefresh = false,
                autoBuy = false,
                mealError = false,
                busy = false,
                foodPicker = FoodPickerState()
            )
        }
    }

    // ------------------------------------------------------------------
    // Buy (called by the Buy button and by auto-buy)
    // ------------------------------------------------------------------

    fun receive(row: StufoodRepository.ExchangeableFood) {
        if (row.receive == null) return
        val running = autoJob
        stopAutoRefresh(endSession = false) // a background search must not race (or shift row indexes under) a purchase
        viewModelScope.launch {
            running?.cancelAndJoin()
            val s = _state.value
            val page = s.page ?: return@launch
            if (s.busy) {
                AutoSearchBridge.onResult(false, row.food, null, "Another request was still running. Try again.")
                return@launch
            }

            // The postback target is positional (cell<N>_4 = "row N right now"), so
            // re-resolve the row against the newest page instead of trusting the tapped,
            // possibly stale, one.
            val fresh = page.availableFoods.firstOrNull { it.receive != null && foodKey(it) == foodKey(row) }
            if (fresh == null) {
                _purchaseResult.value = PurchaseResult(false, "This food is no longer available.")
                AutoSearchBridge.onResult(false, row.food, null, "This food is no longer available.")
                return@launch
            }

            AutoSearchBridge.onBuying(fresh.food)
            _state.update { it.copy(busy = true) }
            try {
                val updated = repo.receiveFood(page, fresh)
                _state.update { it.copy(page = updated ?: page, busy = false) }
                val msg = updated?.message
                Log.d(TAG, "buy -> site alert: $msg")
                val ok = msg != null && isSuccessMessage(msg)
                _purchaseResult.value = PurchaseResult(
                    success = ok,
                    message = msg,
                    foodName = if (ok) fresh.food else null,
                    price = if (ok) msg?.let(::extractPriceToman) else null
                )
                AutoSearchBridge.onResult(ok, fresh.food, if (ok) msg?.let(::extractPriceToman) else null, msg)
            } catch (e: CancellationException) {
                _state.update { it.copy(busy = false) }
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "buy failed", t)
                _state.update { it.copy(busy = false) }
                _purchaseResult.value = PurchaseResult(
                    success = false,
                    message = "Request failed (${t.message ?: "network error"})."
                )
                AutoSearchBridge.onResult(false, fresh.food, null, "Request failed (${t.message ?: "network error"}).")
            }
        }
    }
}
