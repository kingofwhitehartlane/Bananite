package ir.mums.stufood.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mums.stufood.R

/** Obtainium's purple, used for accent buttons (e.g. the update popup). */
val ObtainiumAccent = Color(0xFF6B3FD4)

// Glow geometry (dp, measured from the card's left edge). The glow is solid behind
// the logo, then eases out smoothly so the text next to it stays readable.
private const val GLOW_SOLID_DP = 28f
private const val GLOW_END_DP = 124f

/**
 * GitHub card. The logo is always on the left, in both themes.
 *  - Dark mode:  black card, white glow behind a black logo.
 *  - Light mode: white card, black glow behind a white logo.
 */
@Composable
fun GitHubLinkCard(
    title: String,
    subtitle: String,
    hapticEnabled: Boolean,
    onClick: () -> Unit
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    BrandCard(
        title = title,
        subtitle = subtitle,
        base = if (dark) Color.Black else Color.White,
        glow = if (dark) Color.White else Color.Black,
        textColor = if (dark) Color.White else Color.Black,
        hapticEnabled = hapticEnabled,
        onClick = onClick
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_github),
            contentDescription = "GitHub",
            tint = if (dark) Color.Black else Color.White,
            modifier = Modifier.size(38.dp)
        )
    }
}

/** Same layout as [GitHubLinkCard], with Obtainium purple as the main colour. */
@Composable
fun ObtainiumLinkCard(
    title: String,
    subtitle: String,
    hapticEnabled: Boolean,
    onClick: () -> Unit
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    BrandCard(
        title = title,
        subtitle = subtitle,
        base = if (dark) Color(0xFF1A1033) else Color(0xFFF4EEFF),
        glow = if (dark) Color(0xFFEBE0FF) else Color(0xFF5B2DB8),
        textColor = if (dark) Color.White else Color(0xFF1A1033),
        hapticEnabled = hapticEnabled,
        onClick = onClick
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_obtainium),
            contentDescription = "Obtainium",
            // Dark: the real purple-gradient logo on the light glow. Light: white logo on the purple glow.
            tint = if (dark) Color.Unspecified else Color.White,
            // The drawable is cropped to the artwork, so this is the logo's real on-screen size.
            modifier = Modifier.size(38.dp)
        )
    }
}

@Composable
private fun BrandCard(
    title: String,
    subtitle: String,
    base: Color,
    glow: Color,
    textColor: Color,
    hapticEnabled: Boolean,
    onClick: () -> Unit,
    logo: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind { drawBrandBackground(base, glow) }
            .border(1.dp, textColor.copy(alpha = 0.15f), shape)
            .hapticClickable(
                type = HapticType.CLICK,
                hapticsEnabled = hapticEnabled,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Fixed-size slot so the text lines up identically on every card.
            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) { logo() }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = textColor
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/**
 * Solid [base] with a [glow] on the left edge: fully opaque behind the logo, then a
 * "smootherstep" ease-out to transparent. Many small stops (instead of a couple of
 * straight segments) remove the visible edge where a linear fade changes slope.
 */
private fun DrawScope.drawBrandBackground(base: Color, glow: Color) {
    drawRect(base)
    val solid = GLOW_SOLID_DP / GLOW_END_DP
    val steps = 16
    val stops = Array(steps + 1) { i ->
        val t = i / steps.toFloat()
        val u = ((t - solid) / (1f - solid)).coerceIn(0f, 1f)
        val eased = u * u * u * (u * (u * 6f - 15f) + 10f) // smootherstep
        t to glow.copy(alpha = 1f - eased)
    }
    drawRect(
        Brush.horizontalGradient(
            *stops,
            startX = 0f,
            endX = GLOW_END_DP.dp.toPx()
        )
    )
}
