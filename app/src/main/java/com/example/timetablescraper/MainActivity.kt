package com.example.timetablescraper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.timetablescraper.ui.screens.SearchScreen
import com.example.timetablescraper.ui.screens.SettingsScreen
import com.example.timetablescraper.ui.screens.TimetableScreen
import com.example.timetablescraper.ui.theme.TimetableScraperTheme
import com.example.timetablescraper.api.SearchResult
import com.example.timetablescraper.update.UpdateChecker
import com.example.timetablescraper.update.UpdateManager
import com.example.timetablescraper.update.UpdateReceiver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimetableScraperTheme {
                MainApp()
            }
        }
    }
}

@Composable
private fun MainApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var starred by remember { mutableStateOf(SyncPreferences.getStarredCourse(context)) }
    val initialScreen = if (starred != null) "TIMETABLE" else "SEARCH"

    var currentScreen by remember { mutableStateOf(initialScreen) }
    var selectedCourse by remember {
        mutableStateOf(
            if (starred != null) SearchResult(
                name = starred!!.second, programme_code = "",
                identity = starred!!.first, type = "Programme",
                selection_id = "", timetable_type_id = starred!!.third
            ) else null
        )
    }
    var preselectedGroup by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchIsLoading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchHasSearched by remember { mutableStateOf(false) }

    // ── Self-updating state ──────────────────────────────────────────────
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateChecker.UpdateResult?>(null) }
    var updateCheckDone by remember { mutableStateOf(false) }

    // Check for updates once on launch
    LaunchedEffect(Unit) {
        if (updateCheckDone) return@LaunchedEffect
        updateCheckDone = true
        val result = UpdateChecker.checkForUpdate()
        updateResult = result
        if (result.updateAvailable && result.downloadUrl != null) {
            showUpdateDialog = true
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
    fun goToStarredOrExit() {
        val s = starred
        if (s != null) {
            selectedCourse = SearchResult(
                name = s.second, programme_code = "",
                identity = s.first, type = "Programme",
                selection_id = "", timetable_type_id = s.third
            )
            preselectedGroup = null
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

    when (currentScreen) {
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
                    currentScreen = "TIMETABLE"
                },
                onSettingsClick = { currentScreen = "SETTINGS" }
            )
        }

        "TIMETABLE" -> {
            selectedCourse?.let { course ->
                val viewingStarred = starred?.first == course.identity
                TimetableScreen(
                    selectedCourse = course,
                    preselectedGroup = preselectedGroup,
                    isStarred = viewingStarred,
                    onStarToggle = { star ->
                        if (star) {
                            SyncPreferences.setStarredCourse(
                                context, course.identity, course.name, course.timetable_type_id)
                            starred = Triple(course.identity, course.name, course.timetable_type_id)
                        } else {
                            SyncPreferences.setStarredCourse(context, null, null, null)
                            starred = null
                        }
                    },
                    onSearchClick = {
                        currentScreen = "SEARCH"
                        selectedCourse = null
                        preselectedGroup = null
                    },
                    onSettingsClick = { currentScreen = "SETTINGS" },
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
                    currentScreen = "TIMETABLE"
                }
            )
        }
    }
}
