package com.example.timetablescraper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.example.timetablescraper.ui.theme.IosRadius
import com.example.timetablescraper.ui.theme.IosTheme
import com.example.timetablescraper.ui.theme.IosType
import com.example.timetablescraper.ui.theme.Motion
import com.example.timetablescraper.ui.theme.iosPressable

/**
 * A small kit of iOS-style building blocks.
 *
 * Everything here follows the same three rules, which are what make an interface feel like iOS
 * rather than Material:
 *
 *  1. **Separation comes from hairlines and grouping, not from elevation.** iOS has almost no
 *     drop shadows inside the content area; instead related rows share a rounded container and a
 *     0.5dp separator. Material's `Card` with elevation is the visual opposite of that.
 *  2. **Press feedback is on the element, not under the finger.** A control scales down slightly
 *     while held, instead of a ripple spreading from the touch point.
 *  3. **Motion is sprung.** See [Motion].
 */

/** A hairline, as iOS draws separators: thinner than a density-independent pixel. */
val IosHairline: Dp = Dp.Hairline

/**
 * A pressable surface with iOS feedback: the element scales slightly while held, with no ink
 * ripple.
 *
 * The interaction source is created here and passed to **both** [iosPressable] and `clickable`.
 * That sharing is the whole point: a private source inside `iosPressable` would never observe the
 * tap, so the scale would silently never animate.
 */
@Composable
private fun Modifier.iosTappable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    haptic: Boolean = false,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .iosPressable(pressedScale = pressedScale, haptic = haptic, interactionSource = source)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** The standard iOS grouped-background surface, for a whole screen. */
@Composable
fun IosScreenBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(IosTheme.colors.groupedBackground),
    ) { content() }
}

/** A separator, inset from the leading edge as iOS insets it in lists. */
@Composable
fun IosDivider(
    startIndent: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(IosHairline)
            .background(IosTheme.colors.separator),
    )
}

/**
 * A grouped list section: a rounded container holding rows, with optional header and footer text.
 *
 * The header/footer styling (uppercase, secondary label, generous side margins) is one of the
 * strongest signals of an iOS interface — it is how Settings keeps its structure readable without
 * a single box or divider around the sections themselves.
 */
@Composable
fun IosListSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                text = header.uppercase(),
                style = IosType.sectionHeader,
                color = IosTheme.colors.secondaryLabel,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 6.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(IosRadius.card))
                .background(IosTheme.colors.secondarySystemBackground),
            content = content,
        )
        if (footer != null) {
            Text(
                text = footer,
                style = IosType.footnote,
                color = IosTheme.colors.secondaryLabel,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp),
            )
        }
    }
}

/**
 * A row inside an [IosListSection].
 *
 * @param icon optional leading glyph, tinted with the accent colour as iOS does in Settings.
 * @param trailing composable for accessories (a value, a toggle, a chevron).
 * @param chevron draws the disclosure chevron, which iOS uses for rows that push a screen.
 */
@Composable
fun IosRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    showDivider: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(clickable)
                .iosPressable(pressedScale = 0.985f, interactionSource = interactionSource)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = IosType.body,
                    color = if (destructive) IosTheme.colors.red else IosTheme.colors.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = IosType.footnote,
                        color = IosTheme.colors.secondaryLabel,
                    )
                }
            }
            trailing?.invoke(this)
        }
        // Inset to align with the title, matching iOS: the separator starts where the text does.
        if (showDivider) IosDivider(startIndent = if (icon != null) 52.dp else 16.dp)
    }
}

/** The disclosure chevron iOS puts on rows that navigate. */
@Composable
fun RowScope.IosChevron() {
    Icon(
        imageVector = Icons.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = IosTheme.colors.tertiaryLabel,
        modifier = Modifier.size(20.dp),
    )
}

/** A plain trailing value, as used for a row that shows a setting's current value. */
@Composable
fun RowScope.IosValue(text: String) {
    Text(
        text = text,
        style = IosType.body,
        color = IosTheme.colors.secondaryLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Button emphasis levels, mirroring iOS's filled / tinted / plain styles. */
enum class IosButtonStyle { Filled, Tinted, Plain }

@Composable
fun IosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IosButtonStyle = IosButtonStyle.Filled,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = IosTheme.colors
    val tint = when {
        destructive -> colors.red
        else -> colors.accent
    }
    val background = when (style) {
        IosButtonStyle.Filled -> tint
        IosButtonStyle.Tinted -> tint.copy(alpha = 0.15f)
        IosButtonStyle.Plain -> Color.Transparent
    }
    val textColor = when (style) {
        IosButtonStyle.Filled -> colors.onAccent
        else -> tint
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(IosRadius.card))
            .background(if (enabled) background else colors.fill)
            .iosTappable(enabled = enabled, pressedScale = 0.97f, haptic = true, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = IosType.headline,
            color = if (enabled) textColor else colors.tertiaryLabel,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * An iOS switch: a 51x31 track with a 27pt knob that overshoots very slightly.
 *
 * Written rather than restyled from Material's `Switch` because the difference is in the shape
 * and the travel, not the colours — Material's switch is smaller with a wider knob.
 */
@Composable
fun IosToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackWidth = 51.dp
    val trackHeight = 31.dp
    val knobSize = 27.dp
    val padding = 2.dp

    val offset by animateDpAsState(
        targetValue = if (checked) trackWidth - knobSize - padding * 2 else 0.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.7f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "iosSwitchKnob",
    )

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (checked) IosTheme.colors.green else IosTheme.colors.fillStrong)
            .iosTappable(pressedScale = 0.94f, haptic = true) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = padding)
                .offset(x = offset)
                .size(knobSize)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * A segmented control, as iOS uses for two or three mutually exclusive views.
 *
 * The selected segment is a rounded thumb that *slides* between positions on a spring, which is
 * the whole point of the control — a crossfade or a highlight swap loses the physicality.
 */
@Composable
fun IosSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val colors = IosTheme.colors
    val hapticTick = com.example.timetablescraper.ui.theme.rememberHapticTick()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IosRadius.medium))
            .background(colors.fill)
            .padding(2.dp),
    ) {
        val segmentWidth = maxWidth / options.size
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = Motion.bouncy(),
            label = "segmentThumb",
        )

        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .height(32.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(colors.secondarySystemBackground)
                .border(
                    width = IosHairline,
                    color = colors.separator.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(7.dp),
                ),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .height(32.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .iosTappable(pressedScale = 0.96f) {
                            if (index != selectedIndex) hapticTick()
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = IosType.subhead.copy(
                            fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = colors.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * A collapsing large title, as iOS uses at the top of a scrollable screen.
 *
 * [collapsed] is driven directly by the scroll position (0 = expanded, 1 = collapsed) rather than
 * being animated: on iOS this transition *is* the scroll, so adding a spring here would make it
 * lag behind the finger — the one place where motion should not be sprung.
 */
@Composable
fun IosLargeTitleHeader(
    title: String,
    collapsed: Float,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    background: Color = IosTheme.colors.systemBackground,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = IosTheme.colors
    val progress = collapsed.coerceIn(0f, 1f)
    val inlineTitleAlpha by animateFloatAsState(
        targetValue = if (progress > 0.85f) 1f else 0f,
        animationSpec = Motion.contentFade,
        label = "inlineTitle",
    )

    Column(modifier = modifier.fillMaxWidth().background(background)) {
        // Inline bar: becomes the whole header once the large title has scrolled away.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = IosType.headline,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            actions?.invoke(this)
        }

        // Large title, which fades and shrinks as it collapses.
        Text(
            text = title,
            style = IosType.largeTitle.copy(
                fontSize = lerp(34.sp, 20.sp, progress),
            ),
            color = colors.label.copy(alpha = 1f - progress * 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        if (subtitle != null && progress < 0.6f) {
            Text(
                text = subtitle,
                style = IosType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        }
        AnimatedVisibility(visible = inlineTitleAlpha > 0f, enter = fadeIn(), exit = fadeOut()) {
            IosDivider()
        }
    }
}

/**
 * The iOS search field: a grey rounded fill with a magnifier and a clear button.
 *
 * Material's `TextField` has a floating label and an underline; iOS has neither, which is why
 * this is a `BasicTextField` with its own decoration box.
 */
@Composable
fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    leading: (@Composable () -> Unit)? = { SearchGlyph() },
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = IosTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IosRadius.medium))
            .background(colors.fill)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) { it() }
        }
        Box(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = IosType.body,
                    color = colors.tertiaryLabel,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(IosType.body).copy(color = colors.label),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .iosTappable(pressedScale = 0.9f) { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Cancel,
                    contentDescription = "Clear",
                    tint = colors.tertiaryLabel,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            trailing?.invoke()
        }
    }
}

@Composable
private fun SearchGlyph() {
    Icon(
        imageVector = Icons.Filled.Search,
        contentDescription = null,
        tint = IosTheme.colors.tertiaryLabel,
        modifier = Modifier.size(18.dp),
    )
}

/** A card surface, used by timeline entries and the recovery screen. */
@Composable
fun IosCard(
    modifier: Modifier = Modifier,
    background: Color = IosTheme.colors.secondarySystemBackground,
    radius: Dp = IosRadius.card,
    elevation: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation, RoundedCornerShape(radius))
            .clip(RoundedCornerShape(radius))
            .background(background),
        content = content,
    )
}

/** Vertical spacing between grouped sections, as iOS uses in Settings. */
val IosSectionSpacing: Dp = 28.dp

/** Horizontal screen margin used by the iOS large-title layout. */
val IosScreenMargin: Dp = 16.dp

/** A subtle separator used at the top of a scrolled list, where iOS dims the content edge. */
@Composable
fun IosTopEdgeDivider() {
    IosDivider()
}

/** Small helper so callers do not need to import the style object for one-off labels. */
@Composable
fun IosCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = IosType.footnote,
        color = IosTheme.colors.secondaryLabel,
        modifier = modifier,
    )
}

/** Themed spacer, kept here so screens do not hardcode paddings. */
@Composable
fun IosVerticalGap(height: Dp) = Spacer(Modifier.height(height))
