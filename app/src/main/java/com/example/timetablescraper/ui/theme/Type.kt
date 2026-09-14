package com.example.timetablescraper.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * An SF-like type scale built on the platform font.
 *
 * SF Pro cannot be redistributed, so the platform font is used and the scale is tuned to SF's
 * proportions instead. The two things that carry most of the resemblance:
 *
 *  - **sizes and weights**, taken from Apple's HIG: body text is 17sp (Material's default is
 *    14–16sp), headlines are 17sp semibold, and there is a 34sp large title;
 *  - **letter spacing that varies with size**: SF tightens as type gets larger (largeTitle and
 *    titles run slightly negative here) and loosens for small text. Material applies a single
 *    positive tracking everywhere, which is the clearest "this is not iOS" tell.
 *
 * Line heights are deliberately generous (~1.2x) because Compose centres text within its line
 * box, which otherwise makes rows look tighter than they do on iOS.
 */
private val tuning = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun ios(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = tuning,
)

// Apple's scale, in points/sp.
private val LargeTitle = ios(34, 41, FontWeight.Bold, 0.37)
private val Title1 = ios(28, 34, FontWeight.Bold, 0.36)
private val Title2 = ios(22, 28, FontWeight.Bold, -0.26)
private val Title3 = ios(20, 25, FontWeight.SemiBold, -0.45)
private val Headline = ios(17, 22, FontWeight.SemiBold, -0.41)
private val Body = ios(17, 22, FontWeight.Normal, -0.41)
private val Callout = ios(16, 21, FontWeight.Normal, -0.32)
private val Subhead = ios(15, 20, FontWeight.Normal, -0.24)
private val Footnote = ios(13, 18, FontWeight.Normal, -0.08)
private val Caption1 = ios(12, 16, FontWeight.Normal, 0.0)
private val Caption2 = ios(11, 13, FontWeight.Normal, 0.07)

/**
 * Material 3 slots mapped onto the scale above, so existing components inherit the look without
 * being rewritten. Slots Apple has no equivalent for reuse the nearest size.
 */
val Typography = Typography(
    displayLarge = LargeTitle,
    displayMedium = Title1,
    displaySmall = Title2,
    headlineLarge = Title1,
    headlineMedium = Title2,
    headlineSmall = Title3,
    titleLarge = Title2,
    titleMedium = Headline,
    titleSmall = Subhead,
    bodyLarge = Body,
    bodyMedium = Callout,
    bodySmall = Footnote,
    labelLarge = Headline,
    labelMedium = Subhead,
    labelSmall = Caption1,
)

/** Named styles for the parts of Apple's scale that Material has no slot for. */
object IosType {
    val largeTitle = LargeTitle
    val title1 = Title1
    val title2 = Title2
    val title3 = Title3
    val headline = Headline
    val body = Body
    val callout = Callout
    val subhead = Subhead
    val footnote = Footnote
    val caption1 = Caption1
    val caption2 = Caption2

    /** Uppercase section headers in grouped lists (Settings). */
    val sectionHeader = Footnote.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        fontSize = 13.sp,
    )
}
