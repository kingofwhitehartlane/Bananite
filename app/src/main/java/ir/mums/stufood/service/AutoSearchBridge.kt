package ir.mums.stufood.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-process hub between BuyFoodViewModel (runs the search loop) and AutoSearchService
 * (keeps the process alive and draws the live notification).
 * The ViewModel writes; the service only reads and reacts.
 */
object AutoSearchBridge {

    enum class Phase {
        IDLE, SEARCHING, WAITING, BUYING, FOUND, SUCCESS, FAILED;

        /** Session finished: the service posts a final result notification and stops. */
        val isTerminal: Boolean get() = this == FOUND || this == SUCCESS || this == FAILED
        val isOngoing: Boolean get() = this == SEARCHING || this == WAITING || this == BUYING
    }

    data class Snapshot(
        val phase: Phase = Phase.IDLE,
        val startedAtWallMs: Long = 0L,
        val searches: Int = 0,
        val intervalMs: Long = 0L,
        /** SystemClock.elapsedRealtime() when the current search cycle started. */
        val cycleStartedAt: Long = 0L,
        val dateLabel: String = "",
        val mealLabel: String = "",
        val foodName: String? = null,
        /** Dining hall / self-service the food belongs to (ExchangeableFood.self). */
        val selfName: String? = null,
        val price: String? = null,
        val message: String? = null,
        val tookMs: Long = 0L,
        /** Auto-buy was armed when the session ended / is armed right now. */
        val autoBuy: Boolean = false,
        /** Live Updates style (progress bar, promoted chip). false = plain ongoing notification. */
        val fancy: Boolean = false
    )

    enum class Command { STOP_AND_CLOSE }

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 1)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    fun begin(dateLabel: String, mealLabel: String, intervalMs: Long, fancy: Boolean, autoBuy: Boolean = false) {
        _snapshot.value = Snapshot(
            phase = Phase.SEARCHING,
            startedAtWallMs = System.currentTimeMillis(),
            intervalMs = intervalMs,
            dateLabel = dateLabel,
            mealLabel = mealLabel,
            autoBuy = autoBuy,
            fancy = fancy
        )
    }

    /** Session over (user stopped, left the screen, ...). The service removes everything. */
    fun end() { _snapshot.value = Snapshot() }

    fun setInterval(ms: Long) = updateActive { it.copy(intervalMs = ms) }

    /** The Auto-buy switch was flipped while a session is running. */
    fun setAutoBuy(enabled: Boolean) = updateActive { it.copy(autoBuy = enabled) }

    fun onSearchStart(cycleStartedAt: Long) = updateActive {
        if (it.phase.isTerminal || it.phase == Phase.BUYING) it
        else it.copy(phase = Phase.SEARCHING, searches = it.searches + 1, cycleStartedAt = cycleStartedAt)
    }

    fun onSearchEnd() = updateActive {
        if (it.phase == Phase.SEARCHING) it.copy(phase = Phase.WAITING) else it
    }

    fun onFound(foodName: String?, selfName: String? = null) = updateActive {
        it.copy(
            phase = Phase.FOUND,
            foodName = foodName,
            selfName = selfName ?: it.selfName,
            tookMs = System.currentTimeMillis() - it.startedAtWallMs
        )
    }

    fun onBuying(foodName: String?, selfName: String? = null) = updateActive {
        it.copy(
            phase = Phase.BUYING,
            foodName = foodName ?: it.foodName,
            selfName = selfName ?: it.selfName
        )
    }

    fun onResult(
        success: Boolean,
        foodName: String?,
        price: String?,
        message: String?,
        selfName: String? = null
    ) = updateActive {
        it.copy(
            phase = if (success) Phase.SUCCESS else Phase.FAILED,
            foodName = foodName ?: it.foodName,
            selfName = selfName ?: it.selfName,
            price = price,
            message = message,
            tookMs = System.currentTimeMillis() - it.startedAtWallMs
        )
    }

    /** "Stop & close" pressed in the notification. */
    fun requestStopAndClose() {
        _commands.tryEmit(Command.STOP_AND_CLOSE)
        end()
    }

    private inline fun updateActive(crossinline f: (Snapshot) -> Snapshot) {
        _snapshot.update { if (it.phase == Phase.IDLE) it else f(it) }
    }
}
