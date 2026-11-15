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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.mums.stufood.R

/** Obtainium's purple, used for accent buttons (e.g. the update popup). */
val ObtainiumAccent = Color(0xFF6B3FD4)

private val GlowWidth = 96.dp

/**
 * GitHub card.
 *  - Dark mode:  black card, white glow fading in from the LEFT behind a black logo.
 *  - Light mode: the reverse; white card, black glow on the RIGHT behind a white logo.
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
        logoAtStart = dark,
        hapticEnabled = hapticEnabled,
        onClick = onClick
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_github),
            contentDescription = "GitHub",
            tint = if (dark) Color.Black else Color.White,
            modifier = Modifier.size(34.dp)
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
        logoAtStart = dark,
        hapticEnabled = hapticEnabled,
        onClick = onClick
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_obtainium),
            contentDescription = "Obtainium",
            // Dark: the real purple-gradient logo on the light glow. Light: white logo on the purple glow.
            tint = if (dark) Color.Unspecified else Color.White,
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
    logoAtStart: Boolean,
    hapticEnabled: Boolean,
    onClick: () -> Unit,
    logo: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind { drawBrandBackground(base, glow, logoAtStart, GlowWidth) }
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
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (logoAtStart) LogoSlot(logo)
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
            if (!logoAtStart) LogoSlot(logo)
        }
    }
}

@Composable
private fun LogoSlot(logo: @Composable () -> Unit) {
    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) { logo() }
}

/**
 * Solid [base] with a [glow] that is fully opaque behind the logo and fades out over
 * [glowWidth]. The fade is steep so text next to the logo stays readable.
 * The glow sits at the start (dark mode) or the end (light mode) of the card.
 */
private fun DrawScope.drawBrandBackground(base: Color, glow: Color, glowAtStart: Boolean, glowWidth: Dp) {
    drawRect(base)
    val gw = glowWidth.toPx()
    val stops = arrayOf(
        0.0f to glow,
        0.5f to glow,
        0.7f to glow.copy(alpha = 0.25f),
        1.0f to glow.copy(alpha = 0f)
    )
    val brush = if (glowAtStart) {
        Brush.horizontalGradient(*stops, startX = 0f, endX = gw)
    } else {
        Brush.horizontalGradient(
            *stops.map { (1f - it.first) to it.second }.reversed().toTypedArray(),
            startX = size.width - gw,
            endX = size.width
        )
    }
    drawRect(brush)
}
