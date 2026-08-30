package ir.mums.stufood.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 1. RoundedSuperellipseBorder (Squircle) equivalent for Jetpack Compose.
 * Uses the cubic Bézier smoothing multiplier (0.55) for continuous superellipse curvature,
 * exactly matching the provided Flutter implementation.
 */
class SquircleShape(private val radius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radiusPx = with(density) { radius.toPx() }
        val rect = Rect(0f, 0f, size.width, size.height)
        
        // Clamp radius to half the smallest dimension to prevent overlapping corners
        val rx = radiusPx.coerceAtMost(size.width / 2f)
        val ry = radiusPx.coerceAtMost(size.height / 2f)

        if (rx <= 0f || ry <= 0f) {
            return Outline.Generic(Path().apply { addRect(rect) })
        }

        // Cubic Bézier smoothing multiplier for continuous superellipse curvature
        const val smoothingFactor = 0.55f
        val cx = rx * smoothingFactor
        val cy = ry * smoothingFactor

        val left = rect.left
        val top = rect.top
        val right = rect.right
        val bottom = rect.bottom

        val path = Path().apply {
            moveTo(left + rx, top)
            lineTo(right - rx, top)

            // Top-Right Corner
            cubicTo(
                right - rx + cx, top,
                right, top + ry - cy,
                right, top + ry
            )

            lineTo(right, bottom - ry)

            // Bottom-Right Corner
            cubicTo(
                right, bottom - ry + cy,
                right - rx + cx, bottom,
                right - rx, bottom
            )

            lineTo(left + rx, bottom)

            // Bottom-Left Corner
            cubicTo(
                left + rx - cx, bottom,
                left, bottom - ry + cy,
                left, bottom - ry
            )

            lineTo(left, top + ry)

            // Top-Left Corner
            cubicTo(
                left, top + ry - cy,
                left + rx - cx, top,
                left + rx, top
            )

            close()
        }

        return Outline.Generic(path)
    }
}

/**
 * 2. StadiumBorder (Pill Shape) equivalent.
 * In Compose, a fully rounded pill is simply a RoundedCornerShape with 50% radius.
 */
val StadiumShape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)