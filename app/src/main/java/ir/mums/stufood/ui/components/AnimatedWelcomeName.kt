package ir.mums.stufood.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mums.stufood.R
import kotlinx.coroutines.delay

private const val HGHT_TALL = 750f   // starting state: tallest
private const val HGHT_SHORT = 500f  // ending state: shortest
private const val KSHD_NARROW = 100f // starting state: no elongation
private const val KSHD_WIDE = 200f   // ending state: max elongation

// New Font Sizing Constants
private const val MIN_FONT_SIZE = 40f // Increased from 34f (safe for average Persian names)
private const val REFERENCE_FONT_SIZE = 52f // Increased from 48f, used as a reference point for averaging

// Original styling constants for the "خوش آمدید" label
private const val LABEL_HGHT = 500f
private const val LABEL_KSHD = 100f
private const val LABEL_SIZE_SP = 24f

@OptIn(ExperimentalTextApi::class)
internal fun alefFamily(hght: Float, kshd: Float): FontFamily = FontFamily(
    Font(
        resId = R.font.alef_vf,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("hght", hght),
            FontVariation.Setting("kshd", kshd),
            FontVariation.Setting("dots", 0f)
        )
    )
)

/**
 * The static "خوش آمدید" label, styled exactly with the custom Alef font 
 * as it was before, now exposed to be used in the TopAppBar.
 */
@Composable
fun WelcomeLabel(modifier: Modifier = Modifier) {
    Text(
        text = "خوش آمدید",
        style = TextStyle(
            fontFamily = alefFamily(LABEL_HGHT, LABEL_KSHD),
            fontSize = LABEL_SIZE_SP.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Right
        ),
        modifier = modifier
    )
}

/**
 * Just the animated name, right-aligned. 
 */
@Composable
fun WelcomeBanner(
    studentName: String?,
    animationType: String,
    bounciness: String = "medium", // NEW
    modifier: Modifier = Modifier
) {
    if (studentName.isNullOrBlank()) return
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        AnimatedAlefName(name = studentName, animationType = animationType, bounciness = bounciness)
    }
}

@Composable
private fun AnimatedAlefName(name: String, animationType: String, bounciness: String) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val hght = remember { Animatable(HGHT_TALL) }
    val kshd = remember { Animatable(KSHD_NARROW) }
    var fontSizeSp by remember { mutableFloatStateOf(MIN_FONT_SIZE) }
    var ready by remember { mutableStateOf(false) }

    // The real spring used for both hght and kshd. "low" bounciness genuinely
    // reduces the physical overshoot (not just the safety-margin estimate below) —
    // that's what makes the two stay in sync and stops the mid-animation overflow.
    val nameDampingRatio = if (bounciness == "low") Spring.DampingRatioLowBouncy
                           else Spring.DampingRatioMediumBouncy

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val marginPx = with(density) { 12.dp.toPx() } // how much narrower than the screen the final state should be
        val boundPx = maxWidthPx - marginPx

        LaunchedEffect(name, maxWidthPx, animationType, bounciness) {
            // This must always be >= the real overshoot the spring below actually
            // produces. "low" uses DampingRatioLowBouncy (overshoots less), so its
            // ratio can shrink too — but it's kept conservative on purpose (fixed
            // margin rather than a precise physics calc) so a slight mismatch
            // errs toward "font a bit smaller," never toward "wraps to 2 lines."
            val bounceOvershootRatio = when {
                animationType == "smooth" -> 0f
                bounciness == "low" -> 0.08f
                else -> 0.15f
            }

            val peakKshd: (Float) -> Float = { kshdVal ->
                if (bounceOvershootRatio > 0f) {
                    kshdVal + (kshdVal - KSHD_NARROW) * bounceOvershootRatio
                } else {
                    kshdVal
                }
            }

            // Derived from the ratio instead of a hardcoded 440f, so it automatically
            // stays consistent with whatever bounceOvershootRatio is above — this is
            // what was silently wrong before (440f only matched the 0.15 case).
            val peakHght = HGHT_SHORT - (HGHT_TALL - HGHT_SHORT) * bounceOvershootRatio

            fun widthAt(sizeSp: Float, kshdVal: Float, hghtVal: Float): Float {
                val style = TextStyle(fontFamily = alefFamily(hghtVal, kshdVal), fontSize = sizeSp.sp)
                return textMeasurer.measure(name, style).size.width.toFloat()
            }

            // Helper to find the maximum font size that fits for a given kashida (checking against peak bounce values)
            fun findMaxFontSizeForKshd(kshdVal: Float): Float {
                var lo = MIN_FONT_SIZE
                var hi = 150f // Allow font size to grow significantly if it fits
                var best = MIN_FONT_SIZE
                val checkKshd = peakKshd(kshdVal)
                repeat(12) { // 12 iterations give excellent precision (~0.08f)
                    val mid = (lo + hi) / 2f
                    if (widthAt(mid, checkKshd, peakHght) <= boundPx) {
                        best = mid
                        lo = mid
                    } else {
                        hi = mid
                    }
                }
                return best
            }

            // Helper to find the maximum kashida (100..200) that fits for a given font size (checking against peak bounce values)
            fun findMaxKshd(fontSize: Float): Float {
                var lo = KSHD_NARROW
                var hi = KSHD_WIDE
                var best = KSHD_NARROW
                repeat(8) {
                    val mid = (lo + hi) / 2f
                    if (widthAt(fontSize, peakKshd(mid), peakHght) <= boundPx) {
                        best = mid
                        lo = mid
                    } else {
                        hi = mid
                    }
                }
                return best
            }

            // 1) Check if MIN_FONT_SIZE with max expected kashida (including bounce offshoot) fits
            val widthAtMinSizeAndPeakKshd = widthAt(MIN_FONT_SIZE, peakKshd(KSHD_WIDE), peakHght)

            var finalFontSize = MIN_FONT_SIZE
            var finalMaxKshd = KSHD_NARROW

            if (widthAtMinSizeAndPeakKshd <= boundPx) {
                // It FITS! Push the font size to the max that still fits perfectly with KSHD_WIDE.
                val maxFittingSize = findMaxFontSizeForKshd(KSHD_WIDE)
                
                if (maxFittingSize in MIN_FONT_SIZE..REFERENCE_FONT_SIZE) {
                    // It's between min and reference size. Do one step of averaging with the reference size 
                    // to get closer to the desired size, which will naturally reduce the max kshd a little 
                    // for a balanced, subtle animation.
                    val targetFontSize = (maxFittingSize + REFERENCE_FONT_SIZE) / 2f
                    finalFontSize = targetFontSize
                    finalMaxKshd = findMaxKshd(targetFontSize)
                } else {
                    // It's > REFERENCE_FONT_SIZE. We shouldn't cap it, so we just use the max fitting size and full kashida.
                    finalFontSize = maxFittingSize
                    finalMaxKshd = KSHD_WIDE
                }
            } else {
                // It OVERFLOWS even at MIN_FONT_SIZE and max expected kashida.
                // We stick to MIN_FONT_SIZE and reduce kashida until it fits (accounting for bounce).
                finalFontSize = MIN_FONT_SIZE
                finalMaxKshd = findMaxKshd(MIN_FONT_SIZE)
            }

            fontSizeSp = finalFontSize
            ready = true
            delay(175) // let the tall & narrow starting state register before it moves
            
            // TOGGLE LOGIC
            if (animationType == "smooth") {
                kshd.animateTo(finalMaxKshd, animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing))
            } else {
                kshd.animateTo(finalMaxKshd, animationSpec = spring(dampingRatio = nameDampingRatio, stiffness = Spring.StiffnessLow))
            }
        }

        LaunchedEffect(ready) {
            if (ready) {
                delay(150)
                // TOGGLE LOGIC
                if (animationType == "smooth") {
                    hght.animateTo(HGHT_SHORT, animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing))
                } else {
                    hght.animateTo(HGHT_SHORT, animationSpec = spring(dampingRatio = nameDampingRatio, stiffness = Spring.StiffnessLow))
                }
            }
        }

        if (ready) {
            Text(
                text = name,
                style = TextStyle(
                    fontFamily = alefFamily(hght.value, kshd.value),
                    fontSize = fontSizeSp.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Right
                ),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,              // NEW — hard backstop so even a future mismatch clips instead of wrapping
                softWrap = false           // NEW
            )
        }
    }
}