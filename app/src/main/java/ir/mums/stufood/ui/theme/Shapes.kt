package ir.mums.stufood.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class SquircleShape(private val radius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radiusPx = with(density) { radius.toPx() }
        val rect = Rect(0f, 0f, size.width, size.height)
        
        val rx = radiusPx.coerceAtMost(size.width / 2f)
        val ry = radiusPx.coerceAtMost(size.height / 2f)

        if (rx <= 0f || ry <= 0f) {
            return Outline.Generic(Path().apply { addRect(rect) })
        }

        // FIX: Removed 'const' from this local variable
        val smoothingFactor = 0.9f
        val cx = rx * smoothingFactor
        val cy = ry * smoothingFactor

        val left = rect.left
        val top = rect.top
        val right = rect.right
        val bottom = rect.bottom

        val path = Path().apply {
            moveTo(left + rx, top)
            lineTo(right - rx, top)

            cubicTo(right - rx + cx, top, right, top + ry - cy, right, top + ry)
            lineTo(right, bottom - ry)

            cubicTo(right, bottom - ry + cy, right - rx + cx, bottom, right - rx, bottom)
            lineTo(left + rx, bottom)

            cubicTo(left + rx - cx, bottom, left, bottom - ry + cy, left, bottom - ry)
            lineTo(left, top + ry)

            cubicTo(left, top + ry - cy, left + rx - cx, top, left + rx, top)
            close()
        }

        return Outline.Generic(path)
    }
}

/**
 * Helper object to easily access standard squircle sizes across the app.
 */
object SquircleDefaults {
    val extraSmall: Shape = SquircleShape(12.dp)
    val small: Shape = SquircleShape(16)
    val medium: Shape = SquircleShape(24.dp)
    val large: Shape = SquircleShape(32.dp)
    val extraLarge: Shape = SquircleShape(48.dp)
}

/**
 * StadiumBorder (Pill Shape) equivalent.
 */
val StadiumShape = RoundedCornerShape(percent = 50)