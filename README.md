# TimeTable

> **⚠️ TU Dublin only.** This app supports a single institution, **TU Dublin**, and connects to its Scientia Publish API. It is an unofficial student project, not affiliated with TU Dublin.

A native Android timetable app that fetches your TU Dublin university schedule directly from the Scientia Publish API — no manual entry needed.

## Features

### Timetable
- **Direct API Integration** — Connects to the TU Dublin Scientia timetable API with Anonymous auth
- **Course Search** — Search by course code or name with real-time debounced results; stale queries are discarded mid-flight. **Keyboard auto-focuses** when the search page opens
- **Full-Year Week Classifier** — Single API request classifies all 30 academic weeks as active or empty instantly (replaces the old 60-second serial scanner)
- **Hide Empty Weeks** — Toggle in Settings to remove weeks with no classes from the dropdown (default: on). When enabled, a warning notes that semester auto-detection will not work
- **Smart Semester Detection** — Auto-detects Semester 1 & 2 boundaries by finding a ≥21-day gap between active weeks after November
- **Week Dropdown** — Numbered weeks (W1, W2, …) with empty weeks optionally hidden
- **Semester Tabs** — An iOS segmented control for instant Semester 1 / Semester 2 switching; switching always lands in the selected semester, even when it has not been published yet
- **Subgroup Filtering** — Pick an individual cohort (`TU859/Y3/MLAI/G2`, `G1`, …) from the dropdown, with ⭐ default-group pinning. A class shared by several cohorts still appears under each of them, and the picker never offers merged `A + B` entries; expand unlimited search results at once to compare groups across courses
- **Pull to Refresh** — Swipe down on the timetable to force a fresh network fetch (limited to once per 24 hours)
- **Persistent View State** — Remembers your semester, week, day tab, and group per course across app restarts
- **Offline Cache** — Room database caches timetables per week; view your schedule even without internet
- **Stale-While-Revalidate** — Cache renders instantly (~5ms), background refresh updates silently

### Timetable Change Detection
- **Real-Time Diff** — When the timetable refreshes from the network, the app compares old vs new data
- **Change Banner** — Finds are announced by a compact, dismissible banner ("3 timetable changes — tap to review") instead of a modal that opens itself; the timetable stays in view
- **Compact, Expandable List** — Each class is one scannable row (name + day/time + a short summary such as "Room changed" or "Cancelled"); tap a row to reveal only the fields that changed, field by field ("Room  A214 → B102")
- **Times match the grid** — Change times are shown in the same Irish local time as the class cards

### Pinning & Bookmarks
- **Pin to Home** — Star a course to make it your home screen; opens instantly on launch. Only one course can be pinned at a time
- **Star = Auto-Save** — Starring a course automatically bookmarks it. Unstarring does NOT unsave
- **Full Course Names** — Saved/bookmarked courses include the year and subgroup (e.g. "TU859/Computer Science (Y3/MLAI/G2)")
- **Bookmark Courses** — Save courses for quick access from Settings. Bookmarking auto-stars (with replacement confirmation)
- **Home Button in Search** — A home icon appears in the search bar when a course is pinned, for one-tap navigation back. Button order: History → Home → Settings

### Sync & Cache
- **Configurable Sync Strategy** — Three-mode pattern:
  - **Daily** — 24-hour cache TTL
  - **Weekly** — 7-day cache TTL
  - **Custom (days)** — User-defined day interval
- **Background Sync** — WorkManager periodically refreshes cached timetables with strategy-aware scheduling
- **Reactive Background Sync** — The screen observes the Room cache for the visible week, so a WorkManager sync repaints the timetable the moment those rows change (no polling)
- **Granular Cache Management** — Delete individual course caches from Settings without wiping everything. Cached courses display **full names** including year and subgroup
- **Sync Notification System** — Background sync completions post notifications with success/fail status and timestamp
- **Client-Side Rate Limiting** — Token Bucket OkHttp interceptor (5 req/10s); returns synthetic 429 to trigger fail-safe fallback

### Privacy
- **Nothing leaves the device** except the timetable request to TU Dublin and the update check to GitHub — both HTTPS, neither carrying an account or a device identifier. Android backup is off
- **Erase all app data** (Settings → Privacy) deletes everything the app has stored, in one tap. You can also switch off the automatic update check there
- **A downloaded update is verified against the app's signing key before it is installed**
- Full detail, including every stored item and every permission: [PRIVACY.md](PRIVACY.md)

### Resilience & Safety
- **Global Crash Handler** — Uncaught exceptions are persisted and recovered on next launch via a dedicated Fatal Error recovery screen
- **Fatal Error Screen** — Shows "Something went wrong" with "Clear Cache & Restart" and "Try Again" buttons. It clears the timetable cache only — your saved courses and pinned course are kept — *unless the database itself is what is broken, in which case it has to delete the database files to escape a crash loop, and those bookmarks go with it
- **Coroutine Exception Handler** — Unhandled coroutine crashes are caught at the root scope and persisted for next-launch recovery
- **Fail-Safe Fallback** — HTTP 429/500 and network errors fall back to stale cache with an "⚠️ Offline / Cached Mode" banner
- **Request Minimization** — Singleton request debouncer deduplicates concurrent API calls to the same URL
- **Defensive JSON Parsing** — All API response parsers wrapped in try/catch; malformed HTML/XML responses emit clean `TimetableApiException(502)`

### Performance
- **Instant Week Classification Cache** — Full-year classification is cached in SharedPreferences per course; subsequent launches are instant
- **Compose Stability** — `@Immutable` annotations on `TimetableEvent`, `SearchResult`, `ApiEvent`, `CacheResult` let Compose skip unchanged-item recomposition checks
- **Room Performance** — Composite index on `[courseIdentity, weekStart]` + `fetchedAt` index for cache pruning; DB v7 schema
- **Resource Efficiency** — `DateTimeFormatter` instances cached globally; date parsing offloaded to `Dispatchers.Default`

### UI/UX
- **Themes** — Pick from six colour themes (Classic plus five cozy ones: Latte, Sage, Dusk, Peach, Mist) in Settings → Appearance, each shown with a live preview swatch. The choice applies app-wide and is remembered across restarts
- **iOS-style design system** — Jetpack Compose screens built on a custom iOS-style theme (colours, type and components) with light/dark support and smooth crossfade animations
- **In-App Self-Updating** — Scans the `releases/` directory on GitHub (via the Contents API) for new APK files; prompts with an update dialog when a newer version is detected. It matches both release and legacy debug APK names
- **Search History** — Quick re-access to recent searches with single-entry delete and "Delete All" button
- **Auto-Focus Keyboard** — The search field gains focus and opens the keyboard automatically when the search page opens
- **Subgroup UI** — 32dp expand arrow with "Tap to reveal course sub-groups" label; full subgroup path displayed in filter chips
- **Auto-Dismiss Cache Status** — "🌐 Updated from server" banner auto-dismisses after 4 seconds

## Privacy

**Zero data collection.** See [PRIVACY.md](PRIVACY.md) for full details.

## How It Works

### Architecture
```
┌─ Presentation Layer ─────────────────────────────────────────┐
│  Jetpack Compose UI (single-Activity, all state hoisted)     │
│  ┌─ SearchScreen ─┐  ┌─ TimetableScreen ─┐  ┌─ Settings ─┐  │
│  │ (course search) │  │ (week view, pull  │  │ (sync,     │  │
│  │ auto-focus kbd  │  │  star, bookmark,  │  │  cache,    │  │
│  │                 │  │  semester tabs)   │  │  updates)  │  │
│  └────────────────┘  └───────────────────┘  └────────────┘  │
│         ▲                    ▲                     ▲         │
│         │  onCourseSelected  │  onStarToggle       │         │
│         ▼                    ▼                     ▼         │
│  ┌─ MainActivity (MainApp) ────────────────────────────┐     │
│  │  State machine: SEARCH / TIMETABLE / SETTINGS       │     │
│  │  CoroutineScope + CoroutineExceptionHandler         │     │
│  └─────────────────────────────────────────────────────┘     │
├─ Safety Layer ───────────────────────────────────────────────┤
│  CrashHandler (Thread.setDefaultUncaughtExceptionHandler)     │
│    → persists crash → next launch shows FatalErrorScreen     │
├─ Data Layer ─────────────────────────────────────────────────┤
│  TimetableRepository (strategy-aware TTL, request debouncer) │
│    ├─ Room Database (per-week indexed key-value cache)       │
│    │     └─ Change detection: diff old vs new on refresh     │
│    ├─ TimetableApiService → OkHttp → Scientia Publish API   │
│    │     ├─ RequestDebouncer (URL-keyed deduplication)       │
│    │     └─ RateLimitInterceptor (token bucket; 5 req/10s)  │
│    └─ WorkManager (strategy-aware periodic sync)             │
│         └─ SyncNotificationManager                           │
├─ Update Layer ───────────────────────────────────────────────┤
│  UpdateChecker → GitHub Contents API (releases/ directory)  │
│  UpdateManager → DownloadManager → FileProvider → Installer  │
└──────────────────────────────────────────────────────────────┘
```

### Data Flow
1. User searches for a course → API returns matching programmes (debounced, stale-guarded). **Keyboard auto-focuses on the search field**
2. Selecting a course loads the timetable via two-phase stale-while-revalidate:
   - **Phase 1 (instant)**: Room cache read + `toUiEvent` parsing on `Dispatchers.Default` → UI renders in ~5ms
   - **Phase 2 (background)**: `TimetableRepository.loadTimetable()` — fresh cache short-circuits; stale cache triggers network fetch silently
3. Events are parsed (pre-compiled regexes), deduplicated (O(n log n) single pass), and cached
4. A single full-year API request classifies all 30 academic weeks as active or empty instantly
5. Week classification is cached in SharedPreferences per course — subsequent launches are instant
6. Semester boundaries auto-detected from a ≥21-day gap between active weeks after November
7. The week dropdown shows only non-empty weeks when the "Hide empty weeks" toggle is on
8. View state (semester, week, day, group) is persisted per course across app restarts
9. WorkManager refreshes cached data per the user's chosen SyncStrategy
10. The screen observes the Room cache, so a WorkManager sync refreshes the UI the moment the cached week actually changes — no polling
11. Pull-to-refresh fetches fresh data but is rate-limited to once every 24 hours
12. **When network data arrives, the app diffs it against the old cache and shows a change notification popup** with day, time, module, and what changed

### Sync Strategies
| Mode | TTL | Behavior |
|------|-----|----------|
| **Daily** | 24 hours | Cache valid for 1 day — network calls blocked within window |
| **Weekly** | 7 days | Cache valid for 7 days |
| **Custom** | X days | User-defined interval (e.g., 3 days) |

Network calls are completely blocked if the app is opened while the cache is still fresh.

### Fail-Safe Behavior
| Scenario | Behavior |
|----------|----------|
| Network timeout / DNS failure | Fall back to stale cache, show "⚠️ Offline / Cached Mode" banner |
| HTTP 429 (Rate Limited) | Fall back to stale cache |
| HTTP 500+ (Server Error) | Fall back to stale cache |
| No cache available + network fail | Show error message with "Retry" button |
| Crash (uncaught exception) | Persisted to prefs → next launch shows FatalErrorScreen |
| Coroutine crash | Caught by root CoroutineExceptionHandler → persisted → FatalErrorScreen on next launch |
| Coroutine cancelled (navigation) | `CancellationException` propagated, cache left intact |

### Fault Tolerance (Chaos Engineering)
| Attack Scenario | Defense |
|----------------|---------|
| JSON key renamed by upstream | `optString()` with safe defaults — missing keys return `""`, never crash |
| Null values in API response | `optString` treats JSON null as missing; `.ifBlank { "TBA" }` guards downstream |
| Server returns HTML instead of JSON | `JSONObject(body)` wrapped in try/catch → clean `TimetableApiException(502)` |
| Server hangs (no response) | 15s connect timeout + 30s read timeout; no automatic retries |
| HTTP 429 / 500+ | Repository `catch(Exception)` triggers stale Room cache fallback |
| Firewall returns login page | `optJSONArray("Results") ?: JSONArray()` — graceful empty results, no crash |
| Worker updates Room while user is viewing | The screen collects the Room `Flow` for the visible week, so it repaints when the rows change and idles otherwise (previously a 2-minute polling `LaunchedEffect`) |
| Timezone boundary (midnight) | All `LocalDate.now()` calls use `Europe/Dublin` with safe `ZoneId` fallback; the API returns session times in **UTC**, so every displayed time (grid and change feed) is projected into `Europe/Dublin` first |

## Download

[**Download latest APK (v2.4)**](https://github.com/Izzeddin-Hammad/TUD-TimeTable-APP/raw/main/releases/TimeTable-v2.4-release.apk)

> Requires Android 8.0+ (API 26). Tap the APK to install — the system will prompt you once per app.
>
> v2.0 onward are **signed release builds** (R8-minified, ~1.8 MB) rather than debug APKs.
>
> **Upgrading from v1.29 or earlier? Uninstall first.** v2.0 is signed with a new key, and Android
> refuses to install a differently-signed package over an existing one. Uninstalling clears the
> local data (pinned course, bookmarks, settings) — re-pin your course once and you are set. Every
> update *after* this one installs in place.


## Releases

Each release has its own notes under [`releases/`](releases/); the APK above is always the latest.
Every release from v2.0 is a signed release build.

| Version | What it was |
|---|---|
| [v2.4](releases/TimeTable-v2.4.md) | The Custom theme sliders no longer write to preferences on every drag frame; the README's per-release sections moved here, and pre-v2.0 releases were unpublished |
| [v2.3](releases/TimeTable-v2.3.md) | Twenty real courses swept end to end; two parsing bugs fixed — a room that rendered as `"null"`, and a module/title swap for a second upstream name shape |
| [v2.2](releases/TimeTable-v2.2.md) | Student-facing bug sweep: switching week no longer shows the wrong week's classes, Semester 2 is actually auto-detected, weekend classes are visible |
| [v2.1](releases/TimeTable-v2.1.md) | Privacy hardening: in-app "Erase all app data", a verified update download, an update-check toggle |
| [v2.0](releases/TimeTable-v2.0.md) | First stable build: a signed release pipeline, plus an audit-driven hardening pass |

Releases before v2.0 are no longer published. They were debug-signed APKs, and v2.0 changed the
signing key — so they could not be updated in place anyway, and keeping them only risked someone
installing one by mistake.

## Setup (for developers)

1. Clone the repo
2. Open in Android Studio
3. Sync Gradle
4. Run on device/emulator (min SDK 26)

No API keys needed — the Scientia Publish API uses Anonymous authentication.

## Running Tests

```bash
# JVM unit tests (fast, no emulator)
./gradlew test

# Filter specific tests
./gradlew test -PtestFilter="com.example.timetablescraper.api.*"

# Instrumentation tests (emulator required)
./gradlew connectedAndroidTest
```

## License

MIT
