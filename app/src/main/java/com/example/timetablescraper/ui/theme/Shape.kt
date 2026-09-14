package com.example.timetablescraper.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * Corner radii on Apple's scale, plus a continuous ("squircle") corner.
 *
 * Apple's rounded rectangles are not circular arcs: the corner eases in continuously, which is
 * why iOS surfaces look softer than the same radius on Android. [SquircleShape] approximates that
 * with a superellipse, and is used for the surfaces where it reads — cards, sheets, buttons and
 * the segmented control. Hairline-level elements keep plain rounded corners, where the difference
 * is invisible and the path is cheaper to clip.
 */
private const val superellipseExponent = 5.0f

val IosShapes = Shapes(
    // Material's Shapes slots accept CornerBasedShape only, so the squircle is used explicitly
    // by the components that want it instead of being injected here.
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

/** Corner sizes for direct use, mirroring Apple's 8 / 10 / 14 / 22 point scale. */
object IosRadius {
    val small = 8.dp
    val medium = 10.dp
    val card = 14.dp
    val sheet = 22.dp
}

/**
 * A continuous corner: `|x/r|^n + |y/r|^n = 1` with n ≈ 5, sampled into a path.
 *
 * Sampled rather than solved because Compose paths are polygon-based anyway, and 24 steps per
 * corner is smooth well beyond the resolution of any display.
 */
class SquircleShape(private val cornerSize: androidx.compose.ui.unit.Dp) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = with(density) { cornerSize.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        if (radius <= 0f) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
        }
        val path = Path()
        val n = superellipseExponent
        val steps = 24

        fun superellipsePoint(cx: Float, cy: Float, sx: Float, sy: Float, t: Double): Pair<Float, Float> {
            // Quarter arc of |x|^n + |y|^n = 1, parameterised by angle for even sampling.
            val cosT = kotlin.math.cos(t).toFloat()
            val sinT = kotlin.math.sin(t).toFloat()
            val x = kotlin.math.sign(cosT) * kotlin.math.abs(cosT).pow(2f / n) * sx
            val y = kotlin.math.sign(sinT) * kotlin.math.abs(sinT).pow(2f / n) * sy
            return cx + x to cy + y
        }

        val w = size.width
        val h = size.height
        val r = radius

        path.moveTo(r, 0f)
        path.lineTo(w - r, 0f)
        // top-right: from angle 0 to 90 degrees around (w - r, r)
        for (i in 1..steps) {
            val t = (i.toDouble() / steps) * (Math.PI / 2)
            val (x, y) = superellipsePoint(w - r, r, r, r, -t)
            path.lineTo(x, y)
        }
        path.lineTo(w, h - r)
        for (i in 1..steps) {
            val t = (i.toDouble() / steps) * (Math.PI / 2)
            val (x, y) = superellipsePoint(w - r, h - r, r, r, Math.PI + t)
            path.lineTo(x, y)
        }
        path.lineTo(r, h)
        for (i in 1..steps) {
            val t = (i.toDouble() / steps) * (Math.PI / 2)
            val (x, y) = superellipsePoint(r, h - r, r, r, Math.PI / 2 + t)
            path.lineTo(x, y)
        }
        path.lineTo(0f, r)
        for (i in 1..steps) {
            val t = (i.toDouble() / steps) * (Math.PI / 2)
            val (x, y) = superellipsePoint(r, r, r, r, Math.PI - t)
            path.lineTo(x, y)
        }
        path.close()

        return Outline.Generic(path)
    }
}
