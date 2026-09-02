package ir.mums.stufood.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView

/**
 * Semantic haptic types.
 */
enum class HapticType {
    TICK,    // Tiny. For: Toggles, Radios, Dropdowns.
    CLICK,   // Standard. For: Buttons, Cards, Navigation.
    HEAVY,   // Pronounced. For: Destructive actions (Reset, Delete).
    SUCCESS  // Positive. For: Data loaded, action confirmed.
}

/**
 * Professional Haptic Feedback Manager.
 * @param enabled Pass your "disable all animations/accessibility" flag here.
 */
@Composable
fun rememberHapticFeedback(enabled: Boolean = true): (HapticType) -> Unit {
    val view = LocalView.current
    
    // FIXED: Removed runBlocking. We now rely on the 'enabled' parameter 
    // passed from the UI state, keeping the main thread completely free.
    return remember(enabled) {
        { type ->
            if (!enabled) return@remember

            val constant = when (type) {
                HapticType.TICK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    HapticFeedbackConstants.CONTEXT_TICK
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
                HapticType.CLICK -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    HapticFeedbackConstants.KEYBOARD_TAP
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
                HapticType.HEAVY -> HapticFeedbackConstants.LONG_PRESS
                HapticType.SUCCESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    HapticFeedbackConstants.KEYBOARD_TAP
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
            }
            view.performHapticFeedback(constant)
        }
    }
}

/**
 * Reusable Modifier for clickable elements.
 */
fun Modifier.hapticClickable(
    type: HapticType = HapticType.CLICK,
    enabled: Boolean = true,
    hapticsEnabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = rememberHapticFeedback(hapticsEnabled)
    this.clickable(enabled = enabled) {
        haptic(type)
        onClick()
    }
}