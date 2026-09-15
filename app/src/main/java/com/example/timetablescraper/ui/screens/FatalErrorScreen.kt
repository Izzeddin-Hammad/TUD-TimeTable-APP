package com.example.timetablescraper.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timetablescraper.CrashHandler
import com.example.timetablescraper.api.cache.TimetableDatabase
import com.example.timetablescraper.ui.components.IosButton
import com.example.timetablescraper.ui.components.IosButtonStyle
import com.example.timetablescraper.ui.components.IosCard
import com.example.timetablescraper.ui.components.IosDivider
import com.example.timetablescraper.ui.theme.IosTheme
import com.example.timetablescraper.ui.theme.IosType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen fatal error recovery composable.
 *
 * Displayed by [MainActivity] when a crash from a previous session is detected via
 * [CrashHandler.hasCrashOccurred], OR in the current session when a recoverable fatal error is
 * caught by the root error boundary.
 *
 * ## Recovery actions
 * - **Try Again** — clears the crash flag and resumes normal UI.
 * - **Clear Cache & Restart** — clears the timetable cache and the cache-derived preferences
 *   (saved courses, the pinned course and your settings are kept), then restarts the activity.
 *   It used to call `clearAllTables()` on Room plus a full preferences wipe, which silently
 *   deleted the student's bookmarked courses and their entire setup.
 *
 * Styled like an iOS alert: a rounded card, a tinted circular glyph, and the actions at the
 * bottom, with the destructive action plainly labelled rather than shouting in red.
 */
@Composable
fun FatalErrorScreen(
    crashInfo: CrashHandler.CrashInfo? = null,
    onClearAndRestart: () -> Unit = {},
    onTryAgain: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = IosTheme.colors
    var isClearing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.groupedBackground)
            // This screen is emitted straight from setContent, outside any Scaffold, so nothing
            // else applies its insets — without this the card sits under the system bars.
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        IosCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            background = colors.secondarySystemBackground,
            radius = com.example.timetablescraper.ui.theme.IosRadius.sheet,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tinted circular glyph, as iOS uses for a state icon.
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.red.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = colors.red,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Something went wrong",
                    style = IosType.title3,
                    fontWeight = FontWeight.Bold,
                    color = colors.label,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "The app hit an unexpected error and needs to recover. " +
                        "Your cached timetables may need to be refreshed.",
                    style = IosType.callout,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center
                )

                // Error details are expanded by default: collapsed, this panel made the recovery
                // screen look blank and hid the one piece of information needed to diagnose it.
                if (crashInfo != null) {
                    var showDetails by remember { mutableStateOf(true) }
                    val disclosureSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = disclosureSource,
                                indication = null,
                            ) { showDetails = !showDetails },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (showDetails) "Hide error details" else "Show error details",
                            style = IosType.subhead,
                            color = colors.accent
                        )
                    }

                    if (showDetails) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.tertiarySystemBackground)
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = buildString {
                                    append(crashInfo.message ?: "Unknown error")
                                    if (crashInfo.stacktrace != null) {
                                        appendLine()
                                        appendLine()
                                        append(crashInfo.stacktrace)
                                    }
                                },
                                style = IosType.caption1,
                                color = colors.secondaryLabel
                            )
                        }
                    }
                }

                IosDivider()

                // Primary action first, as iOS orders alert buttons.
                IosButton(
                    text = if (isClearing) "Clearing…" else "Try Again",
                    onClick = {
                        runCatching { CrashHandler.clearCrashFlag(context) }
                        onTryAgain()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = IosButtonStyle.Filled,
                    enabled = !isClearing
                )

                IosButton(
                    text = "Clear Cache & Restart",
                    onClick = {
                        isClearing = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    // Preferred: a cache-only reset through Room, which keeps the
                                    // student's bookmarked courses and search history.
                                    //
                                    // It cannot run at all when the database is the thing that is
                                    // broken — opening it is what fails — so fall back to deleting
                                    // the database files. That works for a corrupt file or an
                                    // invalid schema/migration state, and it is the only action
                                    // that lets the next launch build a fresh database: without it
                                    // this screen is an inescapable loop.
                                    val clearedThroughRoom = runCatching {
                                        com.example.timetablescraper.TimetableApplication.instance
                                            .repository.clearAll()
                                    }.isSuccess
                                    if (!clearedThroughRoom) deleteDatabaseFiles(context)

                                    runCatching {
                                        com.example.timetablescraper.SyncPreferences
                                            .clearCacheState(context)
                                    }
                                    runCatching { CrashHandler.clearCrashFlag(context) }
                                }
                            } catch (_: Throwable) {
                                // Nothing may strand the user here: if even the reset above blew up,
                                // delete the database files outright and restart anyway.
                                runCatching { deleteDatabaseFiles(context) }
                                runCatching { CrashHandler.clearCrashFlag(context) }
                            } finally {
                                isClearing = false
                            }
                            onClearAndRestart()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = IosButtonStyle.Plain,
                    destructive = true,
                    enabled = !isClearing
                )

                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

/**
 * Deletes the cache database's files **without** opening Room.
 *
 * The recovery screen has to be able to clear a database that cannot be *opened* — and opening it
 * is exactly what fails, so going through the repository cannot work. Deleting the files succeeds
 * for a corrupt file or an invalid schema/migration state, and the next launch then builds a fresh
 * database. Without this the screen is an inescapable loop: the reset would fail on the same broken
 * database it is trying to clear.
 */
private fun deleteDatabaseFiles(context: Context) {
    TimetableDatabase.deleteFiles(context)
}
