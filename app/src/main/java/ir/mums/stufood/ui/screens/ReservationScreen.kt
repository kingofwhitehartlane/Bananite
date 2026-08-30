package ir.mums.stufood.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.mums.stufood.data.StufoodRepository
import ir.mums.stufood.ui.components.LoadingDots
import ir.mums.stufood.data.StufoodRepository.DayInfo
import ir.mums.stufood.data.StufoodRepository.DayStatus
import ir.mums.stufood.data.StufoodRepository.DietOption
import ir.mums.stufood.data.StufoodRepository.ExchangeDialogData
import ir.mums.stufood.data.StufoodRepository.ReservationPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Reservation screen.
 *
 * Days, their state, and the week-nav buttons are all read fresh from whatever the
 * server just sent — none of it is assumed to be a fixed shape, since the site
 * adds/removes days and locks/unlocks them (cutoffs, already-reserved, admin
 * changes, published/unpublished menus, etc.) on its own schedule.
 *
 * Animation notes:
 * - The credit header lives OUTSIDE the scrollable list (a sibling above it, not the
 *   list's first item). A [NestedScrollConnection] collapses it in `onPreScroll`
 *   (before the list itself moves) and only expands it back in `onPostScroll` — i.e.
 *   only once the list has been scrolled all the way back to its own top and has
 *   nothing left to give back. That's what keeps the top-bar pill from fading on
 *   every little upward wobble deep in the list: it can only react right at the edge
 *   where the big card would become visible again. While actively dragging it can sit
 *   at any partial collapse; the moment the drag/scroll ends — whether or not that
 *   happens to be a fling — a `LaunchedEffect` watching `scrollState.isScrollInProgress`
 *   snaps it the rest of the way to whichever end is closer, so it can never come to
 *   rest half-collapsed. `onPreFling` still gives it a head start on that snap the
 *   instant a fling begins (so it doesn't wait for the fling to fully finish), but the
 *   `isScrollInProgress` effect is what guarantees it always actually lands on a hard
 *   edge, fling or not.
 * - [ReservationScreen] always renders the day list through the *same* call to
 *   [ReservationContent], with `page`/`dimmed` as plain parameters that change value
 *   — never two different branches of a `when`. Two different branches are two
 *   different composition slots, so switching between them tears down and rebuilds
 *   the whole subtree (losing every day card's animation state and replaying its
 *   entrance transition). Keeping one call site means a loading transition just
 *   updates parameters in place; nothing remounts, so nothing flashes.
 * - Each day card's inner content is wrapped in an AnimatedContent keyed on the whole
 *   [DayInfo]. Its transitionSpec checks whether it's still the *same* day (same
 *   date/index — a per-day action just updated its content, so the nice fade+scale+
 *   resize plays) or a *different* day now occupying this slot (page reload, week
 *   nav, meal change bringing in a fresh set of days — the outer entrance stagger
 *   already animates that arrival, so this skips its own transition entirely rather
 *   than stacking a second animation on top of it).
 *
 * Food exchange ("تبادل غذا"): a checked-but-locked diet option can offer
 * `exchangeFieldName` (show "درخواست تبادل با دانشجویان") or, once a request has
 * already been placed, `cancelExchangeFieldName` (show "انصراف از تبادل غذا") — see
 * [DietList]. Opening the dialog shows [ExchangeDialogSheet]; withdrawing a pending
 * request goes through a confirm [AlertDialog] first, same pattern as cancelling a
 * plain reservation.
 */
private val CreditCollapseRange = 76.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationScreen(
    onBack: () -> Unit,
    vm: ReservationViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val status by vm.statusText.collectAsState()
    // FIX: vm.errorMessage now emits an ErrorEvent(message, id) instead of a raw
    // String — each call to postError() carries a unique id, so this LaunchedEffect
    // re-fires for every error, even when the message text is identical to the
    // previous one. Previously, a plain String StateFlow silently deduped repeat
    // errors (StateFlow skips emission when the new value equals the old one), so
    // only the very first error of a given kind was ever shown — every later one
    // (e.g. exchange/cancel-exchange failures after any earlier hiccup) vanished
    // with no snackbar at all.
    val error by vm.errorMessage.collectAsState()
    val pendingCancel by vm.pendingCancel.collectAsState()
    val pendingCancelExchange by vm.pendingCancelExchange.collectAsState()
    val exchangeDialog by vm.exchangeDialog.collectAsState()
    val busyDayIndex by vm.busyDayIndex.collectAsState()

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbarHost.showSnackbar(it.message) } }
    LaunchedEffect(Unit) { vm.load() }

    // A single source of truth for "what page to show" and "are we mid page-wide
    // refresh" — this is what lets ReservationContent be called from exactly one
    // place below, instead of one call per `when` branch.
    val displayPage: ReservationPage? = when (val s = state) {
        is ReservationUiState.Ready -> s.page
        is ReservationUiState.Working -> s.previousPage
        else -> null
    }
    val dimmed = state is ReservationUiState.Working

    val scrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val collapseRangePx = with(density) { CreditCollapseRange.toPx() }

    var collapsedPx by remember { mutableStateOf(0f) }
    val collapseFraction by remember { derivedStateOf { (collapsedPx / collapseRangePx).coerceIn(0f, 1f) } }
    val settleScope = rememberCoroutineScope()
    var settleJob: Job? by remember { mutableStateOf(null) }

    fun settleToNearestEdge() {
        if (collapsedPx > 0f && collapsedPx < collapseRangePx) {
            val target = if (collapsedPx > collapseRangePx / 2f) collapseRangePx else 0f
            settleJob = settleScope.launch {
                animate(
                    initialValue = collapsedPx,
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { value, _ -> collapsedPx = value }
            }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            // Collapsing: intercepted BEFORE the list moves, so the list stays at its
            // own offset 0 for as long as there's still header left to collapse.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                settleJob?.cancel() // a new drag always takes over from any in-progress settle
                val delta = available.y
                if (delta >= 0f) return Offset.Zero // upward drag: let the list have first go (see onPostScroll)
                if (collapsedPx >= collapseRangePx) return Offset.Zero
                val target = (collapsedPx - delta).coerceIn(0f, collapseRangePx)
                val consumed = target - collapsedPx
                collapsedPx = target
                return Offset(0f, -consumed)
            }

            // Expanding: only the LEFTOVER after the list already consumed everything
            // it could — i.e. only once the list is already at its own top.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                settleJob?.cancel() // a new drag always takes over from any in-progress settle
                val leftover = available.y
                if (leftover <= 0f) return Offset.Zero
                if (collapsedPx <= 0f) return Offset.Zero
                val target = (collapsedPx - leftover).coerceIn(0f, collapseRangePx)
                val consumed2 = target - collapsedPx
                collapsedPx = target
                return Offset(0f, -consumed2)
            }

            // Gives the snap a head start the instant a fling begins, rather than
            // waiting for the fling to fully settle. This doesn't consume any of the
            // fling velocity, so the list's own momentum scroll is unaffected. The
            // `isScrollInProgress` effect below is the one that actually *guarantees*
            // the header always ends up on a hard edge — this is just an optimization
            // so it doesn't look laggy on a fast fling.
            override suspend fun onPreFling(available: Velocity): Velocity {
                settleToNearestEdge()
                return Velocity.Zero
            }
        }
    }

    // Belt-and-suspenders for "no middle ground": whenever the list's own scroll
    // gesture ends — drag released with no fling, fling finished, programmatic
    // scroll, anything — and the header was left mid-collapse, snap it the rest of
    // the way. onPreFling only fires for flings with enough velocity; a slow,
    // deliberate drag-and-release can end without ever triggering it, which is
    // exactly the "stuck half-faded" case this closes.
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress) {
            settleToNearestEdge()
        }
    }

    pendingCancel?.let { pending ->
        AlertDialog(
            onDismissRequest = vm::dismissCancelRequest,
            title = { MultiScriptText("\u06a9\u0646\u0633\u0644 \u0631\u0632\u0631\u0648") }, // "کنسل رزرو"
            text = { MultiScriptText("\u0622\u06cc\u0627 \u0627\u0632 \u06a9\u0646\u0633\u0644 \u06a9\u0631\u062f\u0646 \u0642\u0630\u0627 \u0627\u0637\u0645\u06cc\u0646\u0627\u0646 \u062f\u0627\u0631\u06cc\u062f\u061f") },
            confirmButton = {
                TextButton(onClick = vm::confirmCancel) { MultiScriptText("\u0628\u0644\u0647") } // بله
            },
            dismissButton = {
                TextButton(onClick = vm::dismissCancelRequest) { MultiScriptText("\u062e\u06cc\u0631") } // خیر
            }
        )
    }

    pendingCancelExchange?.let { pending ->
        AlertDialog(
            onDismissRequest = vm::dismissCancelExchangeRequest,
            title = { Text("\u0644\u0641\u0648 \u062f\u0631\u062e\u0648\u0627\u0633\u062a \u062a\u0628\u0627\u062f\u0644") }, // "لغو درخواست تبادل"
            text = { Text("\u0622\u06cc\u0627 \u0627\u0632 \u0627\u0646\u0635\u0631\u0627\u0641 \u0627\u0632 \u062a\u0628\u0627\u062f\u0644 \u0627\u06cc\u0646 \u0648\u0639\u062f\u0647 \u0627\u0637\u0645\u06cc\u0646\u0627\u0646 \u062f\u0627\u0631\u06cc\u062f\u061f") },
            confirmButton = {
                TextButton(onClick = vm::confirmCancelExchange) { Text("\u0628\u0644\u0647") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissCancelExchangeRequest) { Text("\u062e\u06cc\u0631") }
            }
        )
    }

    exchangeDialog?.let { dialogState ->
        ExchangeDialogSheet(state = dialogState, vm = vm)
    }

    val currentCredit = displayPage?.creditToman

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reserve Food") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    currentCredit?.let { credit ->
                        Surface(
                            modifier = Modifier
                                .alpha(collapseFraction)
                                .padding(end = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = credit,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = dimmed,
            onRefresh = { vm.load() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (displayPage == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoadingDots()
                        Spacer(Modifier.width(12.dp))
                        Text("Loading…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                ) {
                    // ---- Collapsing credit header: a sibling ABOVE the scrollable
                    // list, not part of its content — this is what lets the nested
                    // scroll connection gate expand/collapse on the list's own top,
                    // instead of the header's height fighting with the list's own
                    // scroll offset. ----
                    displayPage.creditToman?.let { credit ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = if (collapseFraction < 1f) 8.dp else 0.dp)
                                .height(CreditCollapseRange * (1f - collapseFraction))
                                .clipToBounds()
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(unbounded = true, align = Alignment.Top)
                                    .alpha(1f - collapseFraction),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Credit Balance",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    AnimatedContent(
                                        targetState = credit,
                                        transitionSpec = {
                                            (slideInVertically(tween(220)) { h -> h / 2 } + fadeIn())
                                                .togetherWith(slideOutVertically(tween(220)) { h -> -h / 2 } + fadeOut())
                                        },
                                        label = "creditRollCard"
                                    ) { c ->
                                        Text(
                                            c,
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ---- Scrollable body: meal/week card + day cards. One single
                    // call site regardless of dimmed/busy state — see the doc comment
                    // at the top of this file for why that matters. ----
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                    ) {
                        ReservationContent(
                            page = displayPage,
                            dimmed = dimmed,
                            busyDayIndex = if (dimmed) null else busyDayIndex,
                            status = status,
                            vm = vm
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationContent(
    page: ReservationPage,
    dimmed: Boolean,
    busyDayIndex: Int?,
    status: String?,
    vm: ReservationViewModel
) {
    // Any postback in flight — page-wide or single-day — disables the day-level
    // controls so two postbacks can never race each other, even though only the
    // specific busy day shows a visible spinner.
    val controlsEnabled = !dimmed && busyDayIndex == null

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (dimmed && status != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LoadingDots(dotSize = 5.dp)
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- Meal + week navigation ----
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
            Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
                Text("Meal & week", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))

                if (page.mealOptions.isNotEmpty()) {
                    DropdownField(
                        label = "Meal",
                        options = page.mealOptions,
                        selectedValue = page.selectedMeal,
                        enabled = controlsEnabled,
                        onSelected = { vm.selectMeal(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { vm.lastWeek() },
                        enabled = controlsEnabled && page.lastWeek.isUsable,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(text = "Previous week", maxLines = 1, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = { vm.nextWeek() },
                        enabled = controlsEnabled && page.nextWeek.isUsable,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(text = "Next week", maxLines = 1, softWrap = false)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.today() },
                    enabled = controlsEnabled && page.today.isUsable,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Jump to today")
                }
            }
        }

        // ---- Per-day cards ----
        val mealSelected = page.selectedMeal != "0"
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            page.days.forEachIndexed { index, day ->
                val entryState = remember(day.index, day.dateLabel) {
                    MutableTransitionState(false).apply { targetState = true }
                }
                AnimatedVisibility(
                    visibleState = entryState,
                    enter = fadeIn(tween(320, delayMillis = index * 50)) +
                        slideInVertically(tween(320, delayMillis = index * 50)) { h -> h / 6 }
                ) {
                    DayCard(
                        day = day,
                        enabled = controlsEnabled,
                        isBusy = busyDayIndex == day.index,
                        mealSelected = mealSelected,
                        vm = vm
                    )
                }
            }
            if (page.days.isEmpty()) {
                Text(
                    "No days found on this page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DayCard(day: DayInfo, enabled: Boolean, isBusy: Boolean, mealSelected: Boolean, vm: ReservationViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---- Header: date (+ lock icon) on the left, status badge (or a busy
            // spinner while this specific day is mid-postback) on the right ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(day.dateLabel, style = MaterialTheme.typography.titleMedium)
                    if (day.isReadOnly || day.status == DayStatus.NOT_ALLOWED || day.dietLocked) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.height(16.dp)
                        )
                    }
                }
                if (isBusy) {
                    LoadingDots(dotSize = 5.dp)
                } else {
                    day.statusBadge?.let { badge ->
                        StatusBadgeChip(text = badge)
                    }
                }
            }

            // Reacts to whatever comes back from the server: when this day's data
            // changes (new status, new options, etc.) the old content fades+shrinks
            // out and the new content springs in, resizing the card as it goes — but
            // only when it's genuinely the same day being updated. If a different day
            // now occupies this slot (page reload / week nav / meal change), the outer
            // entrance stagger already animates its arrival, so this snaps instantly
            // instead of piling a second animation on top of it.
            AnimatedContent(
                targetState = day,
                transitionSpec = {
                    val sameDay = initialState.index == targetState.index &&
                        initialState.dateLabel == targetState.dateLabel

                    if (sameDay) {
                        // Reusable soft spring spec for both scale and size change
                        val softSpring = spring<Float>(
                            dampingRatio = Spring.DampingRatioLowBouncy, // Subtle spring overshoot
                            stiffness = Spring.StiffnessMediumLow        // Settles faster, prevents floating
                        )

                        (fadeIn(tween(220, delayMillis = 90)) + 
                        scaleIn(
                            animationSpec = softSpring,
                            initialScale = 0.95f // Reduced zoom displacement (5% vs 8%)
                        ))
                            .togetherWith(fadeOut(tween(90)))
                            .using(
                                SizeTransform(clip = false) { _, _ ->
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                }
                            )
                    } else {
                        EnterTransition.None
                            .togetherWith(ExitTransition.None)
                            .using(SizeTransform(clip = false) { _, _ -> snap() })
                    }
                },
                label = "dayCardContent"
            ) { animatedDay ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (animatedDay.status) {
                        DayStatus.NO_FOOD_DEFINED -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "\u063a\u0630\u0627\u06cc\u06cc \u0628\u0631\u0627\u06cc \u0627\u06cc\u0646 \u0631\u0648\u0632 \u062a\u0639\u0631\u06cc\u0641 \u0646\u0634\u062f\u0647 \u0627\u0633\u062a",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        DayStatus.NOT_ALLOWED, DayStatus.NOT_RESERVED -> {
                            // Only the "روز فروش" (day-sale) badge type carries this extra menu text.
                            if (animatedDay.statusBadge == "\u0631\u0648\u0632 \u0641\u0631\u0648\u0634" && // روز فروش
                                !animatedDay.daySealText.isNullOrBlank()
                            ) {
                                Text(
                                    text = animatedDay.daySealText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        // adding the exchange things here fixed the not reacting button bug
                        DayStatus.RECEIVED, DayStatus.NOT_RECEIVED -> {
                            DietList(
                                options = animatedDay.dietOptions,
                                selectable = false,
                                onSelect = {},
                                onCancel = {},
                                onRequestExchange = { vm.openExchangeDialog(animatedDay, it) },
                                onCancelExchange = { vm.requestCancelExchange(animatedDay, it) }
                            )
                        }

                        DayStatus.SELECT_CAFETERIA -> {
                            if (mealSelected) {
                                val onlyPlaceholder = animatedDay.cafeteriaOptions.size <= 1
                                if (onlyPlaceholder) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.height(16.dp)
                                        )
                                        Text(
                                            "\u0627\u0646\u062a\u062e\u0627\u0628 \u0633\u0644\u0641",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    DropdownField(
                                        label = "\u0627\u0646\u062a\u062e\u0627\u0628 \u0633\u0644\u0641",
                                        options = animatedDay.cafeteriaOptions,
                                        selectedValue = animatedDay.selectedCafeteria ?: "0",
                                        enabled = enabled,
                                        onSelected = { vm.selectCafeteria(animatedDay, it) }
                                    )
                                }
                            }
                        }

                        DayStatus.SELECT_DIET, DayStatus.RESERVED -> {
                            if (mealSelected) {
                                if (
                                    !animatedDay.cafeteriaFieldName.isNullOrBlank() &&
                                    animatedDay.cafeteriaOptions.isNotEmpty()
                                ) {
                                    DropdownField(
                                        label = "\u0633\u0644\u0641 \u0627\u0646\u062a\u062e\u0627\u0628 \u0634\u062f\u0647",
                                        options = animatedDay.cafeteriaOptions,
                                        selectedValue = animatedDay.selectedCafeteria ?: "0",
                                        enabled = enabled,
                                        onSelected = { vm.selectCafeteria(animatedDay, it) }
                                    )
                                    Spacer(Modifier.height(4.dp))
                                } else if (!animatedDay.selfLabel.isNullOrBlank()) {
                                    Text(
                                        text = animatedDay.selfLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }

                                DietList(
                                    options = animatedDay.dietOptions,
                                    selectable = enabled,
                                    onSelect = { vm.selectDiet(animatedDay, it) },
                                    onCancel = { vm.requestCancel(animatedDay, it) },
                                    onRequestExchange = { vm.openExchangeDialog(animatedDay, it) },
                                    onCancelExchange = { vm.requestCancelExchange(animatedDay, it) }
                                )
                            }
                        }

                        DayStatus.UNKNOWN -> {
                            Text(
                                "Couldn't determine this day's state — check for site changes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadgeChip(text: String, modifier: Modifier = Modifier) {
    val notReceivedLabel = "\u062f\u0631\u06cc\u0627\u0641\u062a \u0646\u06a9\u0631\u062f\u0647"
    val receivedLabel = "\u062f\u0631\u06cc\u0627\u0641\u062a \u06a9\u0631\u062f\u0647"

    val (targetContainer, targetContent) = when (text) {
        notReceivedLabel -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        receivedLabel -> Color(0xFFC8E6C9) to Color(0xFF1B5E20)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val container by animateColorAsState(targetContainer, label = "badgeContainer")
    val content by animateColorAsState(targetContent, label = "badgeContent")

    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier
    ) {
        AnimatedContent(targetState = text, label = "badgeText") { t ->
            Text(
                text = t,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun DietList(
    options: List<DietOption>,
    selectable: Boolean,
    onSelect: (DietOption) -> Unit,
    onCancel: (DietOption) -> Unit,
    onRequestExchange: ((DietOption) -> Unit)? = null,
    onCancelExchange: ((DietOption) -> Unit)? = null
) {
    if (options.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (selectable && !option.disabled) {
                            it.selectable(selected = option.checked, onClick = { onSelect(option) })
                        } else it
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = option.checked,
                    onClick = if (selectable && !option.disabled) { { onSelect(option) } } else null,
                    enabled = selectable && !option.disabled
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    option.priceToman?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // ---- Cancel reservation outright (only when the site allows it) ----
                if (option.checked && option.cancelFieldName != null) {
                    IconButton(onClick = { onCancel(option) }) {
                        Icon(
                            Icons.Default.RemoveCircle,
                            contentDescription = "\u06a9\u0646\u0633\u0644 \u0631\u0632\u0631\u0648", // کنسل رزرو
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // ---- Food exchange: offer it ("درخواست تبادل با دانشجویان"), or
                // withdraw an already-placed offer ("انصراف از تبادل غذا") ----
                if (option.checked && option.exchangeFieldName != null && !option.exchangePending && onRequestExchange != null) {
                    IconButton(onClick = {
                        onRequestExchange?.invoke(option)
                    }) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "\u062f\u0631\u062e\u0648\u0627\u0633\u062a \u062a\u0628\u0627\u062f\u0644 \u0628\u0627 \u062f\u0627\u0646\u0634\u062c\u0648\u06cc\u0627\u0646", // درخواست تبادل با دانشجویان
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (option.checked && option.exchangePending && onRequestExchange != null) {
                    IconButton(onClick = { onCancelExchange?.invoke(option) }) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = "\u0627\u0646\u0635\u0631\u0627\u0641 \u0627\u0632 \u062a\u0628\u0627\u062f\u0644 \u0642\u0630\u0627", // انصراف از تبادل غذا
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

/**
 * The "تبادل غذا" (food exchange) dialog. Mirrors the real site's modal: a radio
 * group picking the exchange kind, then either a pair of dropdowns (specific food)
 * or a student-number search (specific student), then a confirm button.
 *
 * The dropdown options and the show/hide state of the two extra sections all come
 * straight from [ExchangeDialogData] as parsed off the server's response to each
 * step — nothing here is hardcoded, so if the site adds/removes an exchange type or
 * changes which fields go with which type, this follows along automatically.
 */
@Composable
private fun ExchangeDialogSheet(state: ReservationViewModel.ExchangeDialogUiState, vm: ReservationViewModel) {
    val dialog = state.dialog

    AlertDialog(
        onDismissRequest = { if (!state.busy) vm.dismissExchangeDialog() },
        title = { Text("\u062a\u0628\u0627\u062f\u0644 \u0642\u0630\u0627") }, // تبادل غذا
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ---- Exchange type ----
                dialog.exchangeTypes.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = dialog.selectedExchangeType == value,
                                enabled = !state.busy,
                                onClick = { vm.selectExchangeType(value) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = dialog.selectedExchangeType == value,
                            onClick = { vm.selectExchangeType(value) },
                            enabled = !state.busy
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Text(
                    // آیا از درخواست تبادل/تعویض اطمینان دارید؟ فقط در صورت لغو
                    // تبادل/تعویض، امکان دریافت غذای خود را دارید. در صورت عدم
                    // تبادل/تعویض و عدم دریافت غذا، طبق قوانین جریمه خواهید شد.
                    "\u0622\u06cc\u0627 \u0627\u0632 \u062f\u0631\u062e\u0648\u0627\u0633\u062a \u062a\u0628\u0627\u062f\u0644/\u062a\u0639\u0648\u06cc\u0636 \u0627\u0637\u0645\u06cc\u0646\u0627\u0646 \u062f\u0627\u0631\u06cc\u062f\u061f " +
                        "\u0641\u0642\u0637 \u062f\u0631 \u0635\u0648\u0631\u062a \u0644\u0642\u0648 \u062a\u0628\u0627\u062f\u0644/\u062a\u0639\u0648\u06cc\u0636\u060c \u0627\u0645\u06a9\u0627\u0646 \u062f\u0631\u06cc\u0627\u0641\u062a \u0642\u0630\u0627\u06cc \u062e\u0648\u062f \u0631\u0627 \u062f\u0627\u0631\u06cc\u062f. " +
                        "\u062f\u0631 \u0635\u0648\u0631\u062a \u0639\u062f\u0645 \u062a\u0628\u0627\u062f\u0644/\u062a\u0639\u0648\u06cc\u0636 \u0648 \u0639\u062f\u0645 \u062f\u0631\u06cc\u0627\u0641\u062a \u0642\u0630\u0627\u060c \u0637\u0628\u0642 \u0642\u0648\u0627\u0646\u06cc\u0646 \u062c\u0631\u06cc\u0645\u0647 \u062e\u0648\u0627\u0647\u06cc\u062f \u0634\u062f.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ---- "تعویض غذا": swap for a specific cafeteria + food ----
                if (dialog.showChangeFoodFields) {
                    DropdownField(
                        label = "\u0633\u0644\u0641", // سلف
                        options = dialog.selfOptions,
                        selectedValue = dialog.selectedSelf ?: "0",
                        enabled = !state.busy,
                        onSelected = { vm.selectExchangeSelf(it) }
                    )
                    DropdownField(
                        label = "\u0627\u0646\u062a\u062e\u0627\u0628 \u0641\u0648\u062f \u0628\u0631\u0627\u06cc \u0645\u0639\u0627\u0648\u0636\u0647", // انتخاب فود برای معاوضه
                        options = dialog.foodOptions,
                        selectedValue = dialog.selectedFood ?: "0",
                        enabled = !state.busy,
                        onSelected = { vm.selectExchangeFood(it) }
                    )
                }

                // ---- "تعویض غذا با سایرین": swap with a specific student ----
                if (dialog.showStudentSearchFields) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.studentNumber,
                            onValueChange = { if (it.length <= 14) vm.updateExchangeStudentNumber(it) },
                            label = { Text("\u0634\u0645\u0627\u0631\u0647 \u062f\u0627\u0646\u0634\u062c\u0648\u06cc \u0645\u0642\u0635\u062f") }, // شماره دانشجوی مقصد
                            singleLine = true,
                            enabled = !state.busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { vm.searchDestinationStudent(state.studentNumber) },
                            enabled = !state.busy && state.studentNumber.isNotBlank()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "\u062c\u0633\u062a\u062c\u0648") // جستجو
                        }
                    }
                    dialog.destStudentLabel?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (state.busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoadingDots(dotSize = 5.dp)
                        Text("\u062f\u0631 \u062d\u0627\u0644 \u0627\u0631\u0633\u0627\u0644\u2026", style = MaterialTheme.typography.bodySmall) // در حال ارسال…
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.confirmExchange() }, enabled = !state.busy) {
                Text("\u062a\u0627\u06cc\u06cc\u062f \u0648 \u062b\u0628\u062a \u062f\u0631\u062e\u0648\u0627\u0633\u062a") // تایید و ثبت درخواست
            }
        },
        dismissButton = {
            TextButton(onClick = vm::dismissExchangeDialog, enabled = !state.busy) {
                Text("\u0627\u0646\u0635\u0631\u0627\u0641") // انصراف
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    enabled: Boolean,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.second == selectedValue }?.first ?: selectedValue

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (optLabel, optValue) ->
                DropdownMenuItem(
                    text = { Text(optLabel) },
                    onClick = {
                        onSelected(optValue)
                        expanded = false
                    }
                )
            }
        }
    }
}