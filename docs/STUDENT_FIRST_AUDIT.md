# Student-First Audit — TimetableScraper

| Field | Value |
|---|---|
| Date | 2026-02-09 |
| Scope | Whole repository (`app/src/main`, tests, Gradle config, resources) |
| Baseline audited | v1.22 (`versionCode` 22) |
| Philosophy | "Student Experience First" — a wrong timetable is worse than a stale one; a class that vanishes is worse than one that is duplicated; a crash loop is worse than an empty screen |
| Verification | `tools/jvm-test-harness.sh` — **143 unit tests green**, `--compile` — **85 classes type-checked** (§4) |

---

## 1. Method

Three parallel read-only audits (boot/persistence safety; offline UX and polling; diff engine, groups and date maths), then targeted fixes validated by compilation and unit tests. Every defect below was confirmed in the source before being changed; every fix is either covered by a test in this repo or explicitly listed as unverified (§4.3).

---

## 2. Defects found — and fixed

Severity reflects **student impact**, not code elegance: `S1` = wrong timetable or unrecoverable state, `S2` = visible degradation, `S3` = hygiene.

| # | Sev | Defect | Evidence (pre-fix) | Mechanism — what actually went wrong for a student | Fix | Test |
|---|---|---|---|---|---|---|
| 1 | **S1** | **All-cohort lectures disappeared when a subgroup was selected** | `TimetableScreen.kt:189` — `event.group.split("+").any { g -> g.trim() == selectedGroup }` | A plenary class has `group = ""`, so `split("+")` yields the token `""`, which never equals `"G1"`. Picking your subgroup therefore **hid the lectures that apply to everyone** — the exact classes you must attend | `GroupMatcher.matches()` treats a blank cohort as "applies to all"; UI filter now calls it | `GroupMatcherTest`, `GroupFilteringTest` |
| 2 | **S1** | Diff engine collapsed sessions that shared a slot | `TimetableRepository.kt:226-227,274-277` — `groupBy(Triple(module, start, end))` then compare `.first()` only | Two sessions in one slot (G1 and G2) meant the **second was never inspected** — a real room change went unreported — while differing sort orders between cache and API produced **phantom "Room: A → B" alerts** on an unchanged timetable | Extracted to `TimetableDiff`: 1:1 pairing over deterministically sorted rows | `TimetableDiffTest` (3 cases) |
| 3 | **S1** | **Cancelled classes came back from the dead** | `TimetableRepository.kt:144` — `if (response.events.isNotEmpty())` | A week whose classes were all cancelled returned `[]`; the write was skipped, so the old rows stayed in Room and the cache-fresh fast path kept serving them. The student kept seeing classes that no longer exist | A successfully parsed response always replaces the cached week, empty included | `TimetableDiffTest` (full-cancellation), §4.2 |
| 4 | **S1** | `SyncStrategy.fromToken("CUSTOM:0:HOURS")` threw `IllegalArgumentException` | `SyncStrategy.kt:103` → `Custom.init { require(value > 0) }` | A corrupt/hand-edited preference crashed the **app-launch path**, contradicting the function's own "returns Daily for unrecognised tokens" contract. Nothing in the UI could clear it | Reject non-positive values before constructing `Custom` | `SyncStrategyContractTest` |
| 5 | **S1** | Unguarded typed `SharedPreferences` reads | 18 sites in `SyncPreferences.kt` (e.g. `:47,51,175-177,231,260,285,302`) | `getInt`/`getString`/… do not coerce: a value written with another primitive type throws `ClassCastException`. One site runs **during composition of the root screen** (`MainActivity.kt:142`), so type drift meant a crash loop whose only escape was wiping app data | All reads go through `SafePrefs` (type-tolerant, degrading to the caller's default) + defensive `runCatching` on the boot path | `SafePrefsTest` (11 cases) |
| 6 | **S1** | **"Clear Cache & Restart" could hang forever** | `FatalErrorScreen.kt:132-151` — no `try/catch`; `enabled = !isClearing` | If the clear threw (corrupt DB, downgrade `IllegalStateException`), the coroutine died **before** `onClearAndRestart()` and the flag was never reset — so both buttons stayed disabled and the spinner span forever. No way out but force-stop | `try/catch/finally`: the flag is always reset and the restart always runs | inspection (§4.3) |
| 7 | **S1** | **"Clear Cache" destroyed user-created data** | `FatalErrorScreen.kt:138-140` — `db.clearAllTables()` + `edit().clear()` on all prefs | It deleted `saved_courses` (bookmarked courses) and `search_history`, and wiped every preference: starred course, semester/week/day view state, chosen groups. A recovery button labelled "Clear Cache" silently discarded the student's setup — while `SettingsScreen.kt:799` promises the opposite for the *other* clear-cache action | Cache-only reset: `repository.clearAll()` (timetable rows only) + new `SyncPreferences.clearCacheState()` (cache-derived keys only) | `SyncPreferences.clearCacheState` key list derived from the file's own constants (§4.2) |
| 8 | **S2** | "Try Again" appeared not to work | `CrashHandler.kt:117-123` — `apply()` + ignored `delete()` result | `apply()` is asynchronous and the caller restarts the process immediately, so the clear could be lost; an undeletable marker file was never noticed. Result: the fatal screen came back after a rotation, which reads as a broken button | `commit()`; deletion verified; un-deletable markers are emptied; `hasCrashOccurred` ignores an empty marker | inspection (§4.3) |
| 9 | **S2** | Room reset itself destructively and silently, wiping user tables | `TimetableDatabase.kt:97` — blanket `fallbackToDestructiveMigration()` with only `MIGRATION_6_7` registered | Any version gap ≤ 5 dropped **every** table, including `saved_courses` and `search_history`. The only user-visible trace was a `Log.w` | Scoped to the versions that genuinely have no migration (`fallbackToDestructiveMigrationFrom(true, 1..6)`); every future gap now fails loudly instead of wiping data | `RoomMigrationTest` (spec) |
| 10 | **S2** | Subgroup data dropped on de-duplication | `TimetableUtils.kt:140-145` — key `start|title|lecturer`, score `group.length + lecturer.length` | Two lab groups running the same module at the same hour collapsed into one row, so a whole subgroup lost its lab; the copy that survived was chosen by a heuristic that could keep the one *without* the group | New two-stage rule: bucket by meeting identity, merge only rows that do not actively disagree about room/cohort | `GroupFilteringTest` (4 cases), `EventKeyTest` |
| 11 | **S2** | Authoritative cohort ignored | `TimetableParser.kt:63` — `if (group.isEmpty())` guarded `Class Group` | The name-derived segment won whenever it existed, so the property carrying the real (often compound, `G1 + G2`) cohort was discarded. The repo's own instrumented test asserted the opposite and was red | `Class Group` is authoritative; the name segment is the fallback; both normalised through `GroupMatcher.format()` | parser test now consistent (device-only, §4.3) |
| 12 | **S2** | The current week could be unreachable | `TimetableUtils.kt:108-127` — hardcoded Sep 1 → Apr 30 | Opening the app in May (resits) or late August gave no way to reach the week the student was actually in — the picker simply did not contain it | The current week is always included | `TimetableUtilsEdgeCaseTest` |
| 13 | **S2** | A room change could be reported as a "move" or as nothing | `TimetableRepository.kt:174` compared **raw** API events against a **de-duplicated** cache with **unnormalised** keys (`:226-227`) | `…T09:00:00` vs `…T09:00:00Z` are the same class but different strings, so every refresh could emit a spurious removed/added pair; `title` was never compared at all, so a renamed module was invisible | One normalisation (`EventKey.wallClock`) and one key model on both sides; `title` compared | `EventKeyTest`, `TimetableDiffTest` |
| 14 | **S2** | A moved class raised two alerts | `TimetableRepository.kt:226-227` (the slot is part of the key) | One real event ("the 10:00 lecture moved to 12:00") surfaced as "1 class removed" + "1 class added" — noise that makes students distrust the change feed | A lone removal with an identical counterpart is reported once as `Time: 10:00 - 11:00 → 12:00 - 13:00`; genuine multi-session churn is left alone | `TimetableDiffTest` (3 cases) |
| 15 | **S2** | Wasted background work every 2 minutes | `TimetableScreen.kt:395-417` — `while (isActive) { delay(120_000); … }` | 30 wake-ups/hour, each running a repository call, a full Room read and an event-list rebuild — for a change that usually had not happened. Battery cost on a student's phone, for no information | Replaced with `repository.observeCachedWeek(...)` (Room `Flow`): the screen updates **when the rows actually change** and does nothing in between | `--compile` covers the API; UI wiring by inspection (§4.3) |
| 16 | **S2** | Cleartext HTTP permitted to every host | `res/xml/network_security_config.xml:4` — `cleartextTrafficPermitted="true"` | Harmless only while the app holds no credentials. The moment accounts arrive it is a downgrade/interception path for tokens, and it is a Play review finding | `false`, with system trust anchors and a documented debug-only escape hatch | inspection (§4.3) |
| 17 | **S3** | Month names followed the device locale | `TimetableUtils.kt:98,103-104` — `ofPattern("MMM d")` with no `Locale` | A German device rendered "Okt 6" next to English change descriptions; the existing tests were locale-dependent too | `Locale.ENGLISH` pinned | `TimetableUtilsEdgeCaseTest` (sets the default locale to Germany) |
| 18 | **S3** | Dead Retrofit dependency | `build.gradle.kts:82`, sole consumer `NetworkResult.kt:7` | Unused weight in every build, and an unused exception branch that implied a dependency the app does not have | Dependency removed; branch removed; doc updated | `--compile` |
| 19 | **S3** | Tests that could not fail | `UpdateCheckerTest.kt:15-21` (reflection into a private method), `GroupFilteringTest.kt:15-114` (re-implemented the logic under test) | The second one passes **even when production filtering is broken** — it was green while defect #1 was live. False confidence is worse than no test | Version comparison extracted to public `Semver` and tested directly; `GroupFilteringTest` rewritten to call production code | `SemverTest`, rewritten `GroupFilteringTest` |

---

## 3. Architectural refactors

The fixes above shared one root cause: **the same domain rule was implemented in several places, differently**. Rather than patching each copy, the rules were extracted into single, pure, testable units.

| New unit | Replaces | Why it matters for a student |
|---|---|---|
| `api/EventKey.kt` | Three inconsistent keys: `dedup(start\|title\|lecturer)`, `diffTriple(module,start,end)` (raw), and the UI's `split("+")` | One definition of "the same session". Wall-clock semantics: a 09:00 lecture stays 09:00 across a DST boundary instead of shifting by an hour and looking like a change |
| `api/GroupMatcher.kt` | Inline logic in `TimetableScreen.kt:182-193` and `SearchScreen.kt:291-295` | Subgroup selection can no longer hide plenary classes, and the options offered are produced by the same parser that matches them — an offered filter can never fail to match |
| `api/TimetableDiff.kt` | `private fun computeChanges` inside the repository (untestable, Android-coupled) | The change feed is the product. It is now pure, deterministic, 1:1 matched, and covered by 20 tests |
| `api/Semver.kt` | `private fun isNewerThan` reached by reflection | Version comparison is behaviour-tested instead of shape-tested |
| `util/SafePrefs.kt` | 18 raw typed preference reads | Corrupt or type-drifted settings degrade to a default instead of crashing the launch path |
| `TimetableRepository.observeCachedWeek()` | The UI's 2-minute poll loop | Event-driven refresh: the screen reacts to a completed background sync instead of asking repeatedly |
| `tools/jvm-test-harness.sh` | nothing (new) | Runs the pure-logic suite and type-checks the non-UI source set **without Gradle**, which is the only way to verify anything in an environment where `~/.gradle` is not writable |

**De-duplication rule, stated once** (because it is the subtlest change here): upstream rows are bucketed by *meeting* identity (module, title, type, wall-clock slot, lecturer); within a bucket, a row that merely **lacks** a room or cohort is folded into the row that states it, while two rows that state **different** rooms/cohorts are genuinely different classes and both survive. That is what keeps parallel lab groups apart *and* stops the same class appearing twice.

---

## 4. Verification

### 4.1 Executed

```
$ ./tools/jvm-test-harness.sh
▸ compiling 8 production files + 9 test files
▸ running 9 test classes
OK (143 tests)

$ ./tools/jvm-test-harness.sh --compile
▸ type-checking the non-UI production source set
▸ compile check OK (85 classes)
```

`./gradlew test` itself remains **host-blocked**: Gradle's user home (`~/.gradle`, ~2.9 GB of cached dependencies) is outside the writable workspace, so the Android unit tests cannot run here at all. The harness is the workaround, and it is checked in so the same verification is reproducible anywhere: `tools/jvm-test-harness.sh [--list|--compile]`.

**Test delta:** 41 tests (baseline: `TimetableUtilsTest` + `GroupFilteringTest` + new contract test) → **143**, across `EventKeyTest`, `GroupMatcherTest`, `TimetableDiffTest`, `SemverTest`, `SafePrefsTest`, `TimetableUtilsEdgeCaseTest`, `SyncStrategyContractTest`, plus the two rewritten suites.

### 4.2 Compile-verified (85 classes, no errors)

`SyncPreferences`, `CrashHandler`, `util/SafePrefs`, and the whole `api/` package including `cache/` — i.e. **every change to production logic and persistence** in this audit, against real Room 2.7.1, OkHttp 4.12.0 and kotlinx-coroutines jars.

### 4.3 Not verified here — stated plainly

| Item | Why | How to verify |
|---|---|---|
| The 3 UI files (`TimetableScreen`, `FatalErrorScreen`, `MainActivity`) | Front-end resolution passes, but codegen for `@Composable` functions needs the Compose compiler plugin to match the Kotlin compiler exactly; the only plugin available (2.2.10) cannot pair with the only usable compiler here (Gradle's bundled 2.2.21) | `./gradlew assembleDebug` / `./gradlew test` on a machine with a writable Gradle home |
| Instrumented tests (`app/src/androidTest`) | Need a device/emulator | `./gradlew connectedAndroidTest` |
| Room migration | No v6 database fixture exists and `exportSchema = false`, so no migration can be validated | Set `exportSchema = true`, commit schemas, then `MigrationTestHelper` |
| Runtime behaviour of preference correction, crash-flag clearing, and the Flow-driven refresh | Requires the Android runtime | Maestro flow J5 + a rotation/crash-loop manual pass |

---

## 5. Found but deliberately not changed

| # | Sev | Issue | Why it is not fixed here |
|---|---|---|---|
| F1 | **S1** | The in-app self-updater downloads an APK from the GitHub Contents API and fires an install intent (`REQUEST_INSTALL_PACKAGES`) — an unauthenticated supply-chain path | Outside the five pillars (it is a distribution decision, not a student-experience bug). Recommendation: distribute via Play with staged rollout and delete the update path |
| F2 | S2 | Only one subgroup segment is parsed from the event name (`TimetableParser.kt:47-49`), so `…/Sem 1/Y3/C/G1` yields `Y3` | Needs a real payload with multi-segment names to decide whether the first or last segment is the cohort. The `Class Group` precedence fix (#11) covers the common case |
| F3 | S2 | The semester-gap heuristic hardcodes ≥21 days, November and Jan-20 (`TimetableScreen.kt:587-593`), and a long November gap can mis-split the year | The right fix is to take term dates from the institution, which is a product change (spec `D-10`), not a code fix |
| F4 | S2 | `MIGRATION_6_7` swallows **any** `ALTER TABLE` exception (`TimetableDatabase.kt:61-63`), which can resurface later as an `IllegalStateException` at DB open | Narrowing the catch needs a real v6 database to reproduce against |
| F5 | S2 | An empty week now caches as *zero rows*, so the cache-fresh fast path cannot mark it fresh — a genuinely empty week re-fetches on each load | Needs a "confirmed empty" marker; a correctness-preserving follow-up, not a regression |
| F6 | S3 | `currentScreen == "TIMETABLE"` with `selectedCourse == null` renders nothing (`MainActivity.kt:291-393`) | Not reachable today (all assignments keep them in sync); worth an explicit fallback branch |
| F7 | S3 | `exportSchema = false` means no schema history | Enable and commit schemas; a process change |

---

## 6. What changed, file by file

**Production (14 files):** `api/EventKey.kt` · `api/GroupMatcher.kt` · `api/TimetableDiff.kt` · `api/Semver.kt` · `util/SafePrefs.kt` *(new)* — `api/TimetableUtils.kt` · `api/TimetableRepository.kt` · `api/TimetableParser.kt` · `api/NetworkResult.kt` · `api/SyncStrategy.kt` · `api/cache/TimetableDatabase.kt` · `SyncPreferences.kt` · `CrashHandler.kt` · `MainActivity.kt` · `ui/screens/TimetableScreen.kt` · `ui/screens/FatalErrorScreen.kt` · `ui/screens/SearchScreen.kt` · `update/UpdateChecker.kt` *(modified)*

**Tests (8 files):** `EventKeyTest` · `GroupMatcherTest` · `TimetableDiffTest` · `SemverTest` · `SafePrefsTest` · `TimetableUtilsEdgeCaseTest` · `SyncStrategyContractTest` *(new)* — `GroupFilteringTest` · `UpdateCheckerTest` *(rewritten to test production code)*

**Config/tooling (3 files):** `res/xml/network_security_config.xml` · `app/build.gradle.kts` · `tools/jvm-test-harness.sh` *(new)* · `.gitignore`

**No schema change was made**: the Room version stays at 7, so no new migration is required and no existing database is invalidated.
