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

### Resilience & Safety
- **Global Crash Handler** — Uncaught exceptions are persisted and recovered on next launch via a dedicated Fatal Error recovery screen
- **Fatal Error Screen** — Shows "Something went wrong" with "Clear Cache & Restart" (clears the timetable cache only — your saved courses and pinned course are kept) and "Try Again" buttons
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

[**Download latest APK (v2.0)**](https://github.com/Izzeddin-Hammad/TUD-TimeTable-APP/raw/main/releases/TimeTable-v2.0-release.apk)

> Requires Android 8.0+ (API 26). Tap the APK to install — the system will prompt you once per app.
>
> v2.0 onward are **signed release builds** (R8-minified, ~1.8 MB) rather than debug APKs.
>
> **Upgrading from v1.29 or earlier? Uninstall first.** v2.0 is signed with a new key, and Android
> refuses to install a differently-signed package over an existing one. Uninstalling clears the
> local data (pinned course, bookmarks, settings) — re-pin your course once and you are set. Every
> update *after* this one installs in place.

### What's new in v2.0

The first stable build: a full-app audit, then the blockers, correctness fixes and polish below. Not an exhaustive list — see [`releases/TimeTable-v2.0.md`](releases/TimeTable-v2.0.md).

- **Signed release builds exist at all.** There was no signing config, so a release APK could not be installed and the updater could not recognise one. Both are fixed; releases are R8-minified and ~25 MB smaller
- **A broken database is no longer an unbreakable crash loop** — the recovery button can delete the database file instead of going through the database it cannot open
- **No more duplicate classes after a refresh, no half-written cache, no corrupt-preference crash**, and rotation no longer throws away your screen or your search
- **Nothing is uploaded to Google backup any more**, and your course is no longer written to logcat

### What's new in v1.29

Pick a theme. Notes: [`releases/TimeTable-v1.29.md`](releases/TimeTable-v1.29.md).

- **Six themes, chosen in Settings → Appearance.** Classic (the original iOS palette, and the default) plus five cozy ones — Latte, Sage, Dusk, Peach and Mist — each with a live preview swatch. Picking one re-tints the whole app at once and is remembered across restarts
- **Every theme has a light and a dark variant**, so the app still follows the system's light/dark setting; only the hue is yours
- **A cozy palette is warm and low-contrast** — off-white (or warm deep grey in dark) rather than pure white/black, with a muted accent

### What's new in v1.28

Fixes three timetable bugs and rebuilds the change notification. Notes: [`releases/TimeTable-v1.28.md`](releases/TimeTable-v1.28.md).

- **Classes are no longer an hour early.** The API sends every session in UTC and the app rendered that clock verbatim, so every class read an hour early through Irish Summer Time (and could land on the wrong day near midnight). Times are now projected into `Europe/Dublin`, and they self-correct after the clocks change
- **"Semester 2" always switches.** For a course whose Semester 2 is not published yet, every week in that semester is "empty", and the *Hide empty weeks* option emptied the list — so tapping Semester 2 silently left you on the other semester's week. It now lands in the chosen semester and says "No classes this week"
- **The group picker offers cohorts, not combinations.** A shared lecture belongs to several cohorts at once, and the picker offered the whole list as one option — reading as "TU859/Y1/G1 + TU859/Y1/G2". Each cohort is now its own option, while the shared class still appears under each of them
- **"Timetable changes" is readable.** The modal that opened itself and printed a semicolon-joined sentence per change is gone: a compact, dismissible banner offers a review, and each class is one row that expands to the fields that changed, in local time

### What's new in v1.27

Fixes a launch crash that affected every build from v1.24 to v1.26. Notes: [`releases/TimeTable-v1.27.md`](releases/TimeTable-v1.27.md).

- **The app opens again.** The database's destructive-fallback version list (added in v1.24) included version 6, which is also the start of the registered `6 → 7` migration. Room rejects that combination while *creating* the database, so the app crashed on every launch — and the recovery screen's restart put it straight back there, which looked like buttons that did nothing
- **This cannot ship again.** Room's own validator is now exercised by a unit test against the shipped plan, so an inconsistent migration list fails the build instead of a user's phone
- The broken v1.24–v1.26 APKs were removed from `releases/` so they cannot be installed by mistake

### What's new in v1.26

A diagnostic release for the "Something went wrong" screen: it does not fix the underlying crash yet — it makes it visible and makes recovery reliable. Notes: [`releases/TimeTable-v1.26.md`](releases/TimeTable-v1.26.md).

- **The crash details are no longer lost or hidden.** The crash record was written asynchronously and could be discarded when the process died, and the details panel was collapsed — so the screen appeared blank with nothing to act on. The record is now written synchronously, recoverable from the on-disk marker, and shown by default
- **An undeletable crash marker can no longer trap you on the recovery screen.** Clearing now timestamps the clear, so a stale marker is ignored — previously the screen returned on every launch and both buttons appeared to do nothing
- **"Clear Cache & Restart" can no longer hang**, and no longer deletes your saved courses, pinned course or settings

### What's new in v1.25

One behaviour change — the rest is the v1.24 work below. Notes: [`releases/TimeTable-v1.25.md`](releases/TimeTable-v1.25.md).

- **The app now reports its real version to the university API.** The request `User-Agent` was hardcoded as `TimeTableApp/1.1` and stayed there while the app reached 1.22, so campus network logs would misread current traffic as an ancient client. It now follows `versionName`, so it cannot go stale again
- **Its contact link pointed at the wrong repository** (`…/TimeTable-APP`, which does not exist); it now points at `…/TUD-TimeTable-APP`
- The value lives in one place instead of being duplicated across two configuration classes

### What's new in v1.24

A student-experience pass: 19 defects fixed, most of them about the timetable telling the truth. Full notes: [`releases/TimeTable-v1.24.md`](releases/TimeTable-v1.24.md).

- **Your subgroup filter no longer hides all-cohort lectures** — classes with no specific group apply to everyone, and now stay visible when you pick G1/G2/…
- **Fewer false change alerts, and no missed ones** — sessions are compared one-to-one, so a room swap is reported once and an unchanged week reports nothing
- **Cancelled classes no longer come back** — a week that comes back empty is cached as empty instead of resurrecting the old rows
- **No more crash loop from corrupt settings** — a bad stored value falls back to its default instead of crashing on launch
- **"Clear Cache & Restart" keeps your data** — it clears the timetable cache only; saved/bookmarked courses, the pinned course and your group choices survive
- **The current week is always reachable** — including May and August, outside the Sep–Apr teaching window
- **Month names are always English**, not the device language
- **Less background battery use** — the timetable refreshes when the cached week actually changes, instead of every 2 minutes
- **Cleartext HTTP is no longer permitted** (every endpoint was already HTTPS)

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
