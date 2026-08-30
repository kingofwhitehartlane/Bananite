package ir.mums.stufood.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
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

private const val HGHT_TALL = 900f   // starting state: tallest
private const val HGHT_SHORT = 500f  // ending state: shortest
private const val KSHD_NARROW = 100f // starting state: no elongation
private const val KSHD_WIDE = 200f   // ending state: max elongation

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
// Update WelcomeBanner signature to accept animationType:
@Composable
fun WelcomeBanner(studentName: String?, animationType: String, modifier: Modifier = Modifier) {
    if (studentName.isNullOrBlank()) return
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd // Pushes name to the far right
    ) {
        AnimatedAlefName(name = studentName, animationType = animationType)
    }
}

// Update AnimatedAlefName to use the animationType:
@Composable
private fun AnimatedAlefName(name: String, animationType: String) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val hght = remember { Animatable(HGHT_TALL) }
    val kshd = remember { Animatable(KSHD_NARROW) }
    var fontSizeSp by remember { mutableFloatStateOf(48f) }
    var ready by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val marginPx = with(density) { 12.dp.toPx() } // how much narrower than the screen the final state should be
        val boundPx = maxWidthPx - marginPx

        LaunchedEffect(name, maxWidthPx) {
            fun widthAt(sizeSp: Float, kshdVal: Float, hghtVal: Float): Float {
                val style = TextStyle(fontFamily = alefFamily(hghtVal, kshdVal), fontSize = sizeSp.sp)
                return textMeasurer.measure(name, style).size.width.toFloat()
            }

            // 1) biggest font size that still fits at the narrowest kashida.
            var size = 64f
            while (size > 12f && widthAt(size, KSHD_NARROW, HGHT_SHORT) > boundPx) {
                size -= 2f
            }
            fontSizeSp = size

            // 2) biggest kashida (100..200) at that size that still fits.
            var lo = KSHD_NARROW
            var hi = KSHD_WIDE
            var best = KSHD_NARROW
            repeat(8) {
                val mid = (lo + hi) / 2f
                if (widthAt(size, mid, HGHT_SHORT) <= boundPx) {
                    best = mid
                    lo = mid
                } else {
                    hi = mid
                }
            }

            ready = true
            delay(100) // let the tall & narrow starting state register before it moves
            
            // TOGGLE LOGIC HERE:
            if (animationType == "smooth") {
                kshd.animateTo(
                    best,
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                )
            } else {
                kshd.animateTo(
                    best,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
        }

        LaunchedEffect(ready) {
            if (ready) {
                delay(150)
                // TOGGLE LOGIC HERE:
                if (animationType == "smooth") {
                    hght.animateTo(
                        HGHT_SHORT,
                        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                    )
                } else {
                    hght.animateTo(
                        HGHT_SHORT,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
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
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}