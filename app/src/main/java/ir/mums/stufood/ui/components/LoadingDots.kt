package ir.mums.stufood.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small "expressive"-style loading indicator: three dots pulsing out of phase with
 * each other.
 *
 * Material3's new Expressive `LoadingIndicator` composable isn't publicly usable yet
 * in the Material3 version this project resolves (the API surface — and the
 * `ExperimentalMaterial3ExpressiveApi` annotation gating it — is still internal), so
 * this is a small hand-rolled stand-in with a similar playful, bouncy feel. It only
 * depends on stable `androidx.compose.animation` APIs, so it won't break the build.
 * If a future Material3 release exposes `LoadingIndicator` publicly, you can swap
 * calls to this back to that.
 */
@Composable
fun LoadingDots(
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "loadingDots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSize / 2)
    ) {
        repeat(3) { index ->
            val scale by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 420, delayMillis = index * 140, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotScale$index"
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}