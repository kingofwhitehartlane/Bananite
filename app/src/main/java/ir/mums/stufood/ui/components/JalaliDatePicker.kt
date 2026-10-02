package ir.mums.stufood.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.mums.stufood.util.JalaliCalendar
import ir.mums.stufood.util.JalaliDate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Year wheel range, always relative to today's Jalali year (never hardcoded). */
private const val YEARS_BACK = 2
private const val YEARS_AHEAD = 5

/**
 * Read-only field that shows a Jalali date ("1405/07/09") and opens the 3-wheel
 * picker dialog when tapped. [value] / [onValueChange] use the site's string format.
 */
@Composable
fun JalaliDateField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Date",
    enabled: Boolean = true,
    haptic: (HapticType) -> Unit = {}
) {
    var show by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        // Transparent overlay so the whole field (not just the icon) opens the picker.
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = enabled) {
                    haptic(HapticType.CLICK)
                    show = true
                }
        )
    }

    if (show) {
        JalaliDatePickerDialog(
            initial = JalaliCalendar.parse(value) ?: JalaliCalendar.today(),
            onConfirm = {
                onValueChange(it.format())
                show = false
            },
            onDismiss = { show = false },
            haptic = haptic
        )
    }
}

@Composable
private fun Text(text: String) = androidx.compose.material3.Text(text)

@Composable
fun JalaliDatePickerDialog(
    initial: JalaliDate,
    onConfirm: (JalaliDate) -> Unit,
    onDismiss: () -> Unit,
    haptic: (HapticType) -> Unit = {}
) {
    val today = remember { JalaliCalendar.today() }
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.month) }
    var day by remember { mutableIntStateOf(initial.day) }
    var resetKey by remember { mutableIntStateOf(0) } // bumped by "Today" to re-seed the wheels

    val years = remember {
        val lo = minOf(initial.year, today.year - YEARS_BACK)
        val hi = maxOf(initial.year, today.year + YEARS_AHEAD)
        (lo..hi).toList()
    }
    val daysInMonth = JalaliCalendar.daysInMonth(year, month)
    val safeDay = day.coerceAtMost(daysInMonth)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select date") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    key(resetKey) {
                        WheelPicker(
                            items = years.map { it.toString() },
                            selectedIndex = years.indexOf(year).coerceAtLeast(0),
                            onSelectedIndexChange = { year = years[it] },
                            modifier = Modifier.weight(1.1f),
                            haptic = haptic
                        )
                        WheelPicker(
                            items = JalaliCalendar.monthNames,
                            selectedIndex = month - 1,
                            onSelectedIndexChange = { month = it + 1 },
                            modifier = Modifier.weight(1.5f),
                            haptic = haptic
                        )
                    }
                    // Re-created whenever the month length changes so the wheel never
                    // offers (or sits on) a day that doesn't exist.
                    key(resetKey, daysInMonth) {
                        WheelPicker(
                            items = (1..daysInMonth).map { it.toString() },
                            selectedIndex = safeDay - 1,
                            onSelectedIndexChange = { day = it + 1 },
                            modifier = Modifier.weight(0.8f),
                            haptic = haptic
                        )
                    }
                }
                TextButton(
                    onClick = {
                        haptic(HapticType.CLICK)
                        year = today.year; month = today.month; day = today.day
                        resetKey++
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Today") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                haptic(HapticType.SUCCESS)
                onConfirm(JalaliDate(year, month, safeDay))
            }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Vertical "roller". Snaps to the nearest item; whichever item sits in the highlighted
 * middle row is the selection (reported once the scroll settles).
 * Implemented with blank spacer items above/below instead of contentPadding so
 * `firstVisibleItemIndex` maps 1:1 to the centered item.
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 44.dp,
    visibleCount: Int = 5,
    haptic: (HapticType) -> Unit = {}
) {
    val pad = visibleCount / 2
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, items.lastIndex)
    )
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val scope = rememberCoroutineScope()

    // With `pad` spacers on top, list index (i) == real item (i - pad) sitting in the middle.
    val centered by remember {
        derivedStateOf {
            val raw = listState.firstVisibleItemIndex +
                if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2f) 1 else 0
            raw.coerceIn(0, items.lastIndex)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect { onSelectedIndexChange(centered) }
    }
    LaunchedEffect(listState) {
        snapshotFlow { centered }.collect {
            if (listState.isScrollInProgress) haptic(HapticType.TICK)
        }
    }

    Box(modifier = modifier.height(itemHeight * visibleCount)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
        )
        LazyColumn(state = listState, flingBehavior = flingBehavior) {
            items(pad) { Spacer(Modifier.height(itemHeight)) }
            itemsIndexed(items) { index, text ->
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .clickable {
                            // Tap any visible option to roll it into the middle.
                            scope.launch { listState.animateScrollToItem(index) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MultiScriptText(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (index == centered) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            items(pad) { Spacer(Modifier.height(itemHeight)) }
        }
    }
}