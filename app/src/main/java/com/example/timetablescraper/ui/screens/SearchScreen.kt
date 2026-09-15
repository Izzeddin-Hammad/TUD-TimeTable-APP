package com.example.timetablescraper.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.timetablescraper.R
import com.example.timetablescraper.TimetableApplication
import com.example.timetablescraper.api.GroupMatcher
import com.example.timetablescraper.api.SearchResult
import com.example.timetablescraper.api.TimetableApiService
import com.example.timetablescraper.api.cache.SearchHistoryEntity
import com.example.timetablescraper.ui.theme.IosTheme
import com.example.timetablescraper.ui.theme.IosType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SearchResult>,
    isLoading: Boolean,
    errorMessage: String?,
    hasSearched: Boolean,
    onStateChange: (query: String, results: List<SearchResult>, isLoading: Boolean, errorMessage: String?, hasSearched: Boolean) -> Unit,
    onCourseSelected: (SearchResult, group: String?) -> Unit,
    onSettingsClick: () -> Unit,
    hasStarredCourse: Boolean = false,
    onHomeClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as TimetableApplication
    val repository = app.repository
    val coroutineScope = rememberCoroutineScope()

    var showHistoryDialog by remember { mutableStateOf(false) }
    var historyEntries by remember { mutableStateOf<List<SearchHistoryEntity>>(emptyList()) }
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the search field and show keyboard when the search page opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // UI refresh key — every tap increments it so the screen recomposes
    var uiTapKey by remember { mutableStateOf(0) }
    val uiRefresh = { uiTapKey++ }

    // Debounced search — uses the hoisted query from parent
    LaunchedEffect(query) {
        if (query.isBlank()) {
            onStateChange(query, emptyList(), false, null, false)
            return@LaunchedEffect
        }

        val capturedQuery = query
        delay(300)
        if (query != capturedQuery || query.isBlank()) return@LaunchedEffect

        onStateChange(query, emptyList(), true, null, true)

        try {
            val response = TimetableApiService.DEFAULT.searchCourses(query.trim())
            if (query == capturedQuery) {
                onStateChange(query, response.results, false, null, true)
                coroutineScope.launch { repository.recordSearch(query) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (query == capturedQuery) {
                onStateChange(query, emptyList(), false, "Connection error: ${e.message ?: "Search failed"}", true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(R.drawable.ic_tud_logo),
                        contentDescription = "TU Dublin Timetable",
                        modifier = Modifier.height(40.dp)
                    )
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            historyEntries = repository.getRecentSearches()
                            showHistoryDialog = true
                        }
                    }) {
                        Icon(Icons.Default.History, contentDescription = "Search history")
                    }
                    if (hasStarredCourse) {
                        IconButton(onClick = onHomeClick) {
                            Icon(Icons.Default.Home, contentDescription = "Go to pinned course")
                        }
                    }
                    IconButton(onClick = {
                        onSettingsClick()
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
                // ── Search bar ──────────────────────────────────────────────
                OutlinedTextField(
                    value = query,
                    onValueChange = { onQueryChange(it); uiRefresh() },
                    placeholder = { Text("Type a course code, e.g. TU859") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                onStateChange("", emptyList(), false, null, false)
                                uiRefresh()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── State: Loading ──────────────────────────────────────────
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                // ── State: Error ────────────────────────────────────────────
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = IosTheme.colors.redWash
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "An unexpected error occurred",
                            modifier = Modifier.padding(16.dp),
                            color = IosTheme.colors.red
                        )
                    }
                }

                // ── State: No results ───────────────────────────────────────
                if (hasSearched && !isLoading && results.isEmpty() && errorMessage == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No courses found for \"$query\"",
                            style = IosType.body,
                            color = IosTheme.colors.secondaryLabel
                        )
                    }
                }

                // ── State: Empty / idle ─────────────────────────────────────
                if (!hasSearched && query.isBlank()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = IosTheme.colors.secondaryLabel.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Type a course code or name to search",
                                style = IosType.body,
                                color = IosTheme.colors.secondaryLabel.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // ── Results list ────────────────────────────────────────────
                if (results.isNotEmpty() && !isLoading) {
                    Text(
                        "${results.size} result(s) found",
                        style = IosType.subhead,
                        color = IosTheme.colors.secondaryLabel,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val expandedIdentities = remember { mutableStateOf(setOf<String>()) }

                    // A blank or duplicated `identity` from upstream would otherwise throw
    // "Key … was already used" and take the entire results list down. De-duplicating only where
    // needed keeps a unique identity untouched (and therefore stable across recompositions).
    val resultKeys = remember(results) {
        val seen = mutableSetOf<String>()
        results.map { result ->
            val base = result.identity.ifBlank { result.name }
            if (seen.add(base)) base else "$base#${seen.size}"
        }
    }

    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(results, key = { index, _ -> resultKeys[index] }) { index, result ->
                            val isExpanded = result.identity in expandedIdentities.value
                            var fetchedGroups by remember { mutableStateOf<List<String>>(emptyList()) }
                            var fetchingGroups by remember { mutableStateOf(false) }

                            Column {
                                CourseResultCard(
                                    result = result,
                                    onClick = {
                                        onCourseSelected(result, null)
                                        uiRefresh()
                                    }
                                )

                                // ── Expand / collapse subgroups ──────────────────
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!isExpanded) {
                                                expandedIdentities.value = expandedIdentities.value + result.identity
                                                fetchingGroups = true
                                                coroutineScope.launch {
                                                    try {
                                                        val now = com.example.timetablescraper.api.TimetableUtils.currentDublinDate()
                                                        var monday = now.with(
                                                            java.time.temporal.TemporalAdjusters.previousOrSame(
                                                                java.time.DayOfWeek.MONDAY))
                                                        var response = TimetableApiService.DEFAULT.fetchTimetable(
                                                            categoryTypeId = result.timetable_type_id,
                                                            identity = result.identity,
                                                            mondayDate = monday
                                                        )
                                                        if (response.events.isEmpty()) {
                                                            val academicYear = if (now.monthValue >= 9) now.year else now.year - 1
                                                            monday = java.time.LocalDate.of(academicYear, 10, 1)
                                                                .with(java.time.temporal.TemporalAdjusters.previousOrSame(
                                                                    java.time.DayOfWeek.MONDAY))
                                                            response = TimetableApiService.DEFAULT.fetchTimetable(
                                                                categoryTypeId = result.timetable_type_id,
                                                                identity = result.identity,
                                                                mondayDate = monday
                                                            )
                                                        }
                                                        fetchedGroups = GroupMatcher.availableGroups(
                                                            response.events.map { it.groupLabel.ifBlank { it.group } }
                                                        )
                                                    } catch (_: Exception) {
                                                        fetchedGroups = emptyList()
                                                    } finally {
                                                        fetchingGroups = false
                                                    }
                                                }
                                            } else {
                                                expandedIdentities.value = expandedIdentities.value - result.identity
                                                fetchedGroups = emptyList()
                                            }
                                            uiRefresh()
                                        }
                                ) {
                                    if (fetchingGroups) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ArrowDropDown
                                                           else Icons.Filled.ArrowRight,
                                            contentDescription = if (isExpanded) "Collapse sub-groups"
                                                                  else "Expand sub-groups",
                                            modifier = Modifier.size(32.dp),
                                            tint = IosTheme.colors.secondaryLabel
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (fetchedGroups.isNotEmpty()) {
                                            if (isExpanded) "Hide sub-groups"
                                            else "${fetchedGroups.size} sub-group(s) — tap to view"
                                        } else if (isExpanded && !fetchingGroups) {
                                            "No sub-groups found"
                                        } else {
                                            "Tap to reveal course sub-groups"
                                        },
                                        style = IosType.subhead,
                                        color = IosTheme.colors.secondaryLabel
                                    )
                                }

                                // ── Group chips (full visible names) ─────────────
                                if (isExpanded && fetchedGroups.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                                    ) {
                                        items(fetchedGroups.size) { i ->
                                            val grp = fetchedGroups[i]
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    onCourseSelected(result, grp)
                                                    uiRefresh()
                                                },
                                                label = { Text(grp, style = IosType.caption1) }
                                            )
                                        }
                                    }
                                } else if (isExpanded && !fetchingGroups) {
                                    Text(
                                        "No groups found for this course.",
                                        style = IosType.footnote,
                                        color = IosTheme.colors.secondaryLabel,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    // ── Search history dialog ───────────────────────────────────────────────
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Recent Searches") },
            text = {
                if (historyEntries.isEmpty()) {
                    Text("No recent searches yet.", color = IosTheme.colors.secondaryLabel)
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())
                    ) {
                        historyEntries.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHistoryDialog = false
                                        onQueryChange(entry.query)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.History, null, Modifier.size(18.dp), tint = IosTheme.colors.secondaryLabel)
                                    Spacer(Modifier.width(12.dp))
                                    Text(entry.query, style = IosType.body)
                                }
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        repository.deleteSearchEntry(entry.query)
                                        historyEntries = repository.getRecentSearches()
                                    }
                                }) {
                                    Icon(Icons.Default.Close, "Remove", Modifier.size(18.dp), tint = IosTheme.colors.secondaryLabel.copy(alpha = 0.5f))
                                }
                            }
                        }
                        // ── Delete All button ──
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        repository.clearSearchHistory()
                                        historyEntries = repository.getRecentSearches()
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = IosTheme.colors.red)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Delete All",
                                style = IosType.body,
                                color = IosTheme.colors.red
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistoryDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun CourseResultCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = result.name,
                style = IosType.subhead,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(result.programme_code, style = IosType.caption1) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(result.type, style = IosType.caption1) }
                )
            }
        }
    }
}
