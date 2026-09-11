package com.example.timetablescraper.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timetablescraper.CrashHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
/**
 * Full-screen fatal error recovery composable.
 *
 * Displayed by [MainActivity] when a crash from a previous session is
 * detected via [CrashHandler.hasCrashOccurred], OR in the current session
 * when a recoverable fatal error is caught by the root error boundary.
 *
 * ## Recovery actions
 * - **Try Again** — clears the crash flag and resumes normal UI.
 * - **Clear Cache & Restart** — clears the timetable cache and the cache-derived preferences
 *   (saved courses, the pinned course and your settings are kept), then restarts the activity.
 *   It used to call `clearAllTables()` on Room plus a full preferences wipe, which silently
 *   deleted the student's bookmarked courses and their entire setup.
 */
@Composable
fun FatalErrorScreen(
    crashInfo: CrashHandler.CrashInfo? = null,
    onClearAndRestart: () -> Unit = {},
    onTryAgain: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isClearing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )

                Text(
                    "Something went wrong",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    "The app encountered an unexpected error and needs to recover. " +
                            "Your cached timetables may need to be refreshed.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Expandable error details
                if (crashInfo != null) {
                    // Expanded by default. Collapsed, this panel made the recovery screen look
                    // blank — nothing visible under the heading — which hid the one piece of
                    // information needed to diagnose the crash.
                    var showDetails by remember { mutableStateOf(true) }
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(if (showDetails) "Hide details" else "Show error details")
                    }
                    if (showDetails) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
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
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(12.dp),
                                maxLines = 30
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Try Again — non-destructive
                OutlinedButton(
                    onClick = {
                        CrashHandler.clearCrashFlag(context)
                        onTryAgain()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isClearing
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Try Again")
                }

                // Clear Cache & Restart — recovery that never blocks the user
                Button(
                    onClick = {
                        isClearing = true
                        scope.launch {
                            // Recovery must not be able to strand the user on this screen: whatever
                            // happens below, the button is re-enabled and the restart runs.
                            try {
                                withContext(Dispatchers.IO) {
                                    // Cache-only reset.
                                    //
                                    // This previously called `db.clearAllTables()`, which also
                                    // deleted `saved_courses` (the student's bookmarked courses) and
                                    // `search_history`, and then wiped *every* sync preference —
                                    // starred course, semester/week/day view state, chosen groups.
                                    // A button labelled "Clear Cache" must not silently destroy
                                    // user-created data, so only the timetable cache and the
                                    // cache-derived preferences are cleared here.
                                    com.example.timetablescraper.TimetableApplication.instance
                                        .repository.clearAll()
                                    com.example.timetablescraper.SyncPreferences
                                        .clearCacheState(context)
                                    CrashHandler.clearCrashFlag(context)
                                }
                            } catch (t: Throwable) {
                                // Even a failure to clear must not trap the user: drop the crash
                                // flag (best effort) and restart anyway.
                                runCatching { CrashHandler.clearCrashFlag(context) }
                            } finally {
                                isClearing = false
                            }
                            onClearAndRestart()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = !isClearing
                ) {
                    if (isClearing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Clear Cache & Restart")
                }
            }
        }
    }
}
