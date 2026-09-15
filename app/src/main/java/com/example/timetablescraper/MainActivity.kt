package com.example.timetablescraper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.platform.LocalContext
import com.example.timetablescraper.ui.screens.FatalErrorScreen
import com.example.timetablescraper.ui.screens.SearchScreen
import com.example.timetablescraper.ui.screens.SettingsScreen
import com.example.timetablescraper.ui.screens.TimetableScreen
import com.example.timetablescraper.ui.theme.AppTheme
import com.example.timetablescraper.ui.theme.Motion
import com.example.timetablescraper.ui.theme.TimetableScraperTheme
import com.example.timetablescraper.api.SearchResult
import com.example.timetablescraper.api.TimetableRepository
import com.example.timetablescraper.api.TimetableUtils
import com.example.timetablescraper.update.UpdateChecker
import com.example.timetablescraper.update.UpdateManager
import com.example.timetablescraper.update.UpdateReceiver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── Check for crash from previous session ───────────────
        val previousCrash = CrashHandler.getCrashInfo(this)
        if (previousCrash != null) {
            // Clear the crash flag immediately so "Try Again" works
            CrashHandler.clearCrashFlag(this)
        }

        // This state drives the FatalErrorScreen — it lives here
        // (outside setContent) so it survives composition restarts
        // during try-catch recovery within the same session.
        val crashState = mutableStateOf<CrashHandler.CrashInfo?>(previousCrash)

        setContent {
            // The picked theme lives above the theme itself, so choosing one in Settings repaints
            // the whole app. The read is defensive: a hand-edited preference must not break
            // composition of the root.
            var themeId by remember {
                mutableStateOf(
                    runCatching { SyncPreferences.getThemeId(this@MainActivity) }.getOrNull()
                )
            }
            var customHue by remember {
                mutableStateOf(
                    runCatching {
                        SyncPreferences.getCustomHue(this@MainActivity, AppTheme.DEFAULT_CUSTOM_HUE)
                    }.getOrDefault(AppTheme.DEFAULT_CUSTOM_HUE)
                )
            }
            var customSaturation by remember {
                mutableStateOf(
                    runCatching {
                        SyncPreferences.getCustomSaturation(
                            this@MainActivity, AppTheme.DEFAULT_CUSTOM_SATURATION
                        )
                    }.getOrDefault(AppTheme.DEFAULT_CUSTOM_SATURATION)
                )
            }

            TimetableScraperTheme(
                theme = AppTheme.fromId(themeId),
                customHue = customHue,
                customSaturation = customSaturation,
            ) {
                val currentCrash = crashState.value

                if (currentCrash != null) {
                    // ── Fatal recovery screen (from previous crash) ─
                    FatalErrorScreen(
                        crashInfo = currentCrash,
                        onClearAndRestart = {
                            val intent = packageManager
                                .getLaunchIntentForPackage(packageName)
                            if (intent != null) {
                                intent.addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                                )
                                finishAffinity()
                                startActivity(intent)
                                Runtime.getRuntime().exit(0)
                            }
                        },
                        onTryAgain = {
                            // Clear the crash flags as well as the in-memory state: otherwise a
                            // marker that survived an earlier clear re-shows this screen after a
                            // rotation, which reads as "Try Again did nothing".
                            runCatching { CrashHandler.clearCrashFlag(this@MainActivity) }
                            crashState.value = null
                        }
                    )
                } else {
                    // ── Normal application UI ────────────────────
                    MainApp(
                        selectedThemeId = themeId ?: AppTheme.DEFAULT.id,
                        customHue = customHue,
                        customSaturation = customSaturation,
                        onThemeSelected = { id ->
                            themeId = id
                            runCatching { SyncPreferences.setThemeId(this@MainActivity, id) }
                        },
                        onCustomColorChanged = { hue, saturation ->
                            customHue = hue
                            customSaturation = saturation
                            runCatching {
                                SyncPreferences.setCustomHue(this@MainActivity, hue)
                                SyncPreferences.setCustomSaturation(this@MainActivity, saturation)
                            }
                        },
                    )
                }
            }
        }
    }
}

// ── Star + Save helper ────────────────────────────────────────────
// Starring a course automatically saves it (bookmarks it) with a
// display name that includes the subgroup when known.
// Unstarring does NOT unsave — the course remains in saved/bookmarked.
private fun applyStarAndSave(
    context: Context,
    course: SearchResult,
    scope: CoroutineScope,
    repo: TimetableRepository,
    group: String? = null
) {
    val displayName = TimetableUtils.savedCourseName(course.name, group)
    // Star with the normalised name: the star is what the home screen titles itself with, and
    // storing the raw upstream string is what carried "… (MLAI/G2) (MLAI/G2)" into the UI.
    SyncPreferences.setStarredCourse(context, course.identity, displayName, course.timetable_type_id)
    scope.launch {
        try {
            if (!repo.isCourseSaved(course.identity)) {
                val courseToSave = course.copy(name = displayName)
                repo.saveCourse(courseToSave, group)
            }
        } catch (_: Exception) {
            // Save failure must not block the starring operation
        }
    }
}

@Composable
private fun MainApp(
    selectedThemeId: String,
    customHue: Float,
    customSaturation: Float,
    onThemeSelected: (String) -> Unit,
    onCustomColorChanged: (hue: Float, saturation: Float) -> Unit,
) {
    val context = LocalContext.current

    // Protected coroutine scope with a fatal-error handler.
    // Catches unhandled exceptions from launched coroutines and persists
    // them so the next launch shows FatalErrorScreen.
    val coroutineScope = remember {
        CoroutineScope(
            Dispatchers.Main + SupervisorJob() +
            CoroutineExceptionHandler { _, throwable ->
                Log.e("MainApp", "Unhandled coroutine crash", throwable)
                // Persist crash info so the next launch shows FatalErrorScreen.
                context.getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("crash_occurred", true)
                    .putString("crash_message", throwable.message ?: "Coroutine crash")
                    .putString("crash_stacktrace", throwable.stackTraceToString())
                    .putLong("crash_timestamp", System.currentTimeMillis())
                    .apply()
            }
        )
    }
    DisposableEffect(Unit) {
        onDispose { coroutineScope.cancel() }
    }

    val repository = TimetableApplication.instance.repository

    // Navigation and search state is *saveable*, not just remembered: it used to be plain
    // `remember`, so rotating the device — or any other recreation — dropped the student back to
    // the start screen with an empty query. SearchResult/UpdateResult are Serializable so a Bundle
    // can hold them.
    var starred by rememberSaveable {
        // Defensive: a corrupt custom attribute or a hand-edited preferences file must never be
        // able to take down composition of the root screen. (Reads also go through SafePrefs now,
        // so this is a second line of defence rather than the only one.)
        mutableStateOf(runCatching { SyncPreferences.getStarredCourse(context) }.getOrNull())
    }
    val initialScreen = if (starred != null) "TIMETABLE" else "SEARCH"

    var currentScreen by rememberSaveable { mutableStateOf(initialScreen) }
    // Which way the next screen transition travels: a push enters from the trailing edge, a pop
    // returns from the leading edge. This used to be fixed, so every "back" animated as a push.
    var navForward by rememberSaveable { mutableStateOf(true) }
    var selectedCourse by rememberSaveable {
        mutableStateOf(
            if (starred != null) SearchResult(
                name = starred!!.second, programme_code = "",
                identity = starred!!.first, type = "Programme",
                selection_id = "", timetable_type_id = starred!!.third
            ) else null
        )
    }
    var preselectedGroup by rememberSaveable { mutableStateOf<String?>(null) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchResults by rememberSaveable { mutableStateOf<List<SearchResult>>(emptyList()) }
    // Deliberately not saveable: restoring `true` after a recreation would show a spinner with no
    // coroutine behind it. A rotation mid-search just resets the indicator.
    var searchIsLoading by remember { mutableStateOf(false) }
    var searchError by rememberSaveable { mutableStateOf<String?>(null) }
    var searchHasSearched by rememberSaveable { mutableStateOf(false) }

    // ── Self-updating state ──────────────────────────────────────────────
    // Saveable so a rotation does not re-run the network check and lose the offered update.
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var updateResult by rememberSaveable { mutableStateOf<UpdateChecker.UpdateResult?>(null) }
    var updateCheckDone by rememberSaveable { mutableStateOf(false) }

    // Check for updates once on launch — unless the student has switched it off. That toggle is the
    // only control they have over the app contacting a third party (GitHub) on its own, which
    // discloses their IP address and when they opened the app.
    LaunchedEffect(Unit) {
        if (updateCheckDone) return@LaunchedEffect
        updateCheckDone = true
        if (!runCatching { SyncPreferences.isAutoUpdateCheckEnabled(context) }.getOrDefault(true)) {
            return@LaunchedEffect
        }
        try {
            val result = UpdateChecker.checkForUpdate()
            updateResult = result
            if (result.updateAvailable && result.downloadUrl != null) {
                showUpdateDialog = true
            }
        } catch (_: Exception) {
            // Update check failure is non-fatal — silently ignore
        }
    }

    // Register the download-complete receiver for the lifetime of this composable
    val updateReceiver = remember { UpdateReceiver() }
    DisposableEffect(Unit) {
        UpdateManager.registerReceiver(context, updateReceiver)
        onDispose {
            try { context.unregisterReceiver(updateReceiver) } catch (_: Exception) {}
        }
    }

    // ── Update available dialog ──────────────────────────────────────────
    if (showUpdateDialog && updateResult != null) {
        val remoteVersion = updateResult!!.remoteVersion ?: "latest"
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Update Available") },
            text = {
                Text(
                    "A new version of TimeTable ($remoteVersion) is available. " +
                    "Would you like to download and install it now?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdateDialog = false
                        val downloadUrl = updateResult?.downloadUrl
                        if (downloadUrl != null) {
                            coroutineScope.launch {
                                UpdateManager.startDownload(context, downloadUrl)
                            }
                        }
                    }
                ) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpdateDialog = false }
                ) {
                    Text("Later")
                }
            }
        )
    }

    // ── Back navigation logic ───────────────────────────────────────
    fun goToStarredOrExit(forward: Boolean = false) {
        val s = starred
        if (s != null) {
            selectedCourse = SearchResult(
                name = s.second, programme_code = "",
                identity = s.first, type = "Programme",
                selection_id = "", timetable_type_id = s.third
            )
            preselectedGroup = null
            navForward = forward
            currentScreen = "TIMETABLE"
        } else {
            (context as? android.app.Activity)?.finish()
        }
    }

    fun goBackFromTimetable(isViewingStarred: Boolean) {
        if (isViewingStarred) {
            (context as? android.app.Activity)?.finish()
        } else if (starred != null) {
            goToStarredOrExit()
        } else {
            // No starred course — go to search, don't exit
            selectedCourse = null
            preselectedGroup = null
            navForward = false
            currentScreen = "SEARCH"
        }
    }

    // ── System back handler ─────────────────────────────────────────
    BackHandler {
        when (currentScreen) {
            "SEARCH" -> goToStarredOrExit()
            "TIMETABLE" -> goBackFromTimetable(starred?.first == selectedCourse?.identity)
            "SETTINGS" -> goToStarredOrExit()
        }
    }

    // iOS push/pop between screens: the incoming screen slides in from the trailing edge while
    // the outgoing one parallaxes a third of the way out and fades. An instant `when` swap is the
    // single biggest reason an Android app feels less fluid than an iOS one.
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            // iOS push/pop. Nothing cross-fades: two translucent full-screen layers overlapping is
            // what made this look muddy, and the direction used to be fixed, so every "back"
            // animated as though it were a fresh push.
            val push = navForward
            (
                slideInHorizontally(animationSpec = Motion.screen()) { fullWidth ->
                    if (push) fullWidth else -fullWidth
                } togetherWith
                    slideOutHorizontally(animationSpec = Motion.screen()) { fullWidth ->
                        val parallax = (fullWidth * Motion.screenParallax).toInt()
                        if (push) -parallax else parallax
                    }
                ).using(SizeTransform(clip = false))
        },
        label = "screen"
    ) { screen ->
    when (screen) {
        "SEARCH" -> {
            SearchScreen(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                results = searchResults,
                isLoading = searchIsLoading,
                errorMessage = searchError,
                hasSearched = searchHasSearched,
                onStateChange = { q, r, l, e, h ->
                    searchQuery = q; searchResults = r
                    searchIsLoading = l; searchError = e; searchHasSearched = h
                },
                onCourseSelected = { course, group ->
                    selectedCourse = course
                    preselectedGroup = group
                    navForward = true
                    currentScreen = "TIMETABLE"
                },
                onSettingsClick = {
                    navForward = true
                    currentScreen = "SETTINGS"
                },
                hasStarredCourse = starred != null,
                onHomeClick = { goToStarredOrExit(forward = true) }
            )
        }

        "TIMETABLE" -> {
            selectedCourse?.let { course ->
                val viewingStarred = starred?.first == course.identity
                var showReplaceStarDialog by remember { mutableStateOf(false) }
                var pendingStarCourse by remember { mutableStateOf<SearchResult?>(null) }

                // ── Replace-star confirmation dialog ─────────────────────────
                if (showReplaceStarDialog && pendingStarCourse != null) {
                    val currentStarredName = starred?.second ?: ""
                    val newName = pendingStarCourse!!.name
                    AlertDialog(
                        onDismissRequest = {
                            showReplaceStarDialog = false
                            pendingStarCourse = null
                        },
                        title = { Text("Replace Starred Course?") },
                        text = {
                            Text(
                                "\"$currentStarredName\" is currently your pinned course. " +
                                "Do you want to replace it with \"$newName\"?"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val pc = pendingStarCourse!!
                                // Remove star from old course
                                SyncPreferences.setStarredCourse(context, null, null, null)
                                starred = null
                                // Remove old saved state before replacing
                                showReplaceStarDialog = false
                                pendingStarCourse = null
                                // Apply star + save for the new course
                                applyStarAndSave(context, pc, coroutineScope, repository)
                                starred = Triple(pc.identity, pc.name, pc.timetable_type_id)
                            }) {
                                Text("Replace")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showReplaceStarDialog = false
                                pendingStarCourse = null
                            }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                TimetableScreen(
                    selectedCourse = course,
                    preselectedGroup = preselectedGroup,
                    isStarred = viewingStarred,
                    onStarToggle = { star, group ->
                        if (star) {
                            // If another course is already starred, ask for confirmation
                            val existingStar = starred
                            if (existingStar != null && existingStar.first != course.identity) {
                                pendingStarCourse = course
                                showReplaceStarDialog = true
                            } else {
                                applyStarAndSave(context, course, coroutineScope, repository, group)
                                starred = Triple(course.identity, course.name, course.timetable_type_id)
                            }
                        } else {
                            // Unstar — remove star ONLY (keep saved/bookmark unchanged)
                            SyncPreferences.setStarredCourse(context, null, null, null)
                            starred = null
                        }
                    },
                    onSavedChanged = { isNowSaved, group ->
                        val currentStarredId = starred?.first
                        if (isNowSaved) {
                            // Saving a course → auto-star it (pin to home).
                            // If another course is already starred, ask for confirmation.
                            if (currentStarredId != null && currentStarredId != course.identity) {
                                pendingStarCourse = course
                                showReplaceStarDialog = true
                            } else if (currentStarredId == null) {
                                // No existing star → star this course directly
                                applyStarAndSave(context, course, coroutineScope, repository, group)
                                starred = Triple(course.identity, course.name, course.timetable_type_id)
                            }
                            // If currentStarredId == course.identity → already starred, nothing to do
                        } else {
                            // Unsaving a course → unstar it if it was the pinned one
                            if (currentStarredId == course.identity) {
                                SyncPreferences.setStarredCourse(context, null, null, null)
                                starred = null
                            }
                        }
                    },
                    onSearchClick = {
                        navForward = false
                        currentScreen = "SEARCH"
                        selectedCourse = null
                        preselectedGroup = null
                    },
                    onSettingsClick = {
                        navForward = true
                        currentScreen = "SETTINGS"
                    },
                    onBack = { goBackFromTimetable(viewingStarred) },
                    showBackArrow = !viewingStarred
                )
            }
        }

        "SETTINGS" -> {
            SettingsScreen(
                onBack = { goToStarredOrExit() },
                onSavedCourseSelected = { course, group ->
                    selectedCourse = course
                    preselectedGroup = group
                    navForward = true
                    currentScreen = "TIMETABLE"
                },
                selectedThemeId = selectedThemeId,
                customHue = customHue,
                customSaturation = customSaturation,
                onThemeSelected = onThemeSelected,
                onCustomColorChanged = onCustomColorChanged,
            )
        }
    }
    }
}
