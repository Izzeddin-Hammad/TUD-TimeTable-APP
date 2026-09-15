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
 * Corner radii on Apple's scale.
 *
 * Apple's rounded rectangles are not circular arcs: the corner eases in continuously, which is why
 * iOS surfaces look softer than the same radius on Android. A superellipse shape approximated that,
 * but nothing used it and it was dropped in v2.0 — these are plain rounded corners, which is what
 * every surface in the app now draws.
 */
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