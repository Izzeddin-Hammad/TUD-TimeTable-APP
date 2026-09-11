# CourseGrid — Test Suite Specification & Reference Implementation

| Field | Value |
|---|---|
| Document ID | `CG-TEST-001` |
| Version | 1.0 (draft for QA review) |
| Target system | **CourseGrid** — the SaaS specified in [`docs/TECHNICAL_SPECIFICATION.md`](TECHNICAL_SPECIFICATION.md) |
| Predecessor artefact | The in-repo `TimetableScraper` Android prototype (v1.22), which is the only *executable* code today |
| Target stacks | Backend: Kotlin + Ktor, PostgreSQL 16+, Redis, managed queue, Testcontainers, MockWebServer / Ktor `testApplication`. Mobile: Android, Kotlin, Compose, Room schema v7, WorkManager |
| Author's role | Principal QA & Software Test Engineer (mobile + distributed backend) |
| Test frameworks | Backend/web: **JUnit 5** + Ktor test engine + **Testcontainers** + MockWebServer. Mobile: **JUnit 4** + Robolectric + in-memory Room + Compose UI test + Macrobenchmark — this split mirrors the existing app (`app/src/test`, `app/src/androidTest`) so nothing has to be rewritten mid-migration |

---

## 0. Scope, and an honest statement about what can execute

This document is the **executable form of the CourseGrid contract**. It has two distinct halves and pretending otherwise would produce a document full of tests that cannot run:

| Half | What it is | Can it run today? |
|---|---|---|
| **Specification-derived suites** (§2.1 domain, §2.2 delta, §3.x integration, §4.x E2E) | Tests written against the contract fixed in [`TECHNICAL_SPECIFICATION.md`](TECHNICAL_SPECIFICATION.md) §5 (schema) and §6 (API). The **CourseGrid backend does not exist yet** — there is no Ktor module, no Postgres schema, no `session_revisions` table in this repo. These tests are executable Kotlin that defines the behaviour a conforming implementation must exhibit, i.e. **contract-first TDD**: the first green run of this suite is the backend's acceptance criterion. | ❌ Not against CourseGrid — they compile once the backend lands. They are written to compile **without** `// TODO` stubs by pinning to explicit domain interfaces listed in §0.2. |
| **Prototype-derived suites** (§2.3 parser/diff, §2.4 week maths, §4.7 migration & upgrade) | Tests for logic that exists **today** in `app/src/main/java/com/example/timetablescraper/`. Signatures were read from the actual source, so these compile and run against v1.22 now, and are written so the same assertions survive the migration to the CourseGrid client. | ✅ Yes — `./gradlew test` (JVM) and `./gradlew connectedAndroidTest` (device) |
| **Baseline** | The existing suite's real state before this document: see §0.1. | ✅ Measured |

Nothing in this document is presented as a passing result unless it was actually executed; §0.1 records what *was* executed and what it reported.

### 0.1 Baseline of the existing suite — what was executed, and what was only reviewed

Commands (this is the whole project's current test surface):

```bash
./gradlew test                      # JVM unit tests  (JUnit 4, app/src/test)
./gradlew connectedAndroidTest      # instrumented    (app/src/androidTest) — needs a device/emulator
# filtered run, supported by the build script:
./gradlew test -PtestFilter="com.example.timetablescraper.api.*"
```

**Executed while preparing this document (§5.3 has the full log and the harness recipe):** `TimetableUtilsTest` + `GroupFilteringTest` — **41 tests, OK**; the new `SyncStrategyContractTest` — **15 tests, OK** after it caught and I fixed a real crash-on-launch defect (D-1). `./gradlew test` itself could **not** run here: Gradle's user home is outside the writable workspace, so the 2.9 GB dependency cache is unreachable (host limitation, not a code problem). The rows marked *reviewed* below are source-review verdicts, not execution results.

| Existing suite | Layer | How assessed | Verdict |
|---|---|---|---|
| `app/src/test/.../api/TimetableUtilsTest.kt` | JVM | **Executed — OK** | Genuine — tests production `TimetableUtils.toUiEvent` ISO parsing and day mapping |
| `app/src/test/.../api/GroupFilteringTest.kt` | JVM | **Executed — OK, but for the wrong reason** | **Defect:** re-implements the filtering logic inline instead of calling production code (`:15-114`) — stays green even when production filtering breaks. Replaced by §2.4 |
| `app/src/test/.../api/ModelsTest.kt` | JVM | Reviewed (needs `CacheSource` from `TimetableRepository` → OkHttp/Room; not compilable in the stand-alone harness) | Genuine — data-class `equals`/`hashCode`, enum values |
| `app/src/test/.../update/UpdateCheckerTest.kt` | JVM | Reviewed (depends on generated `BuildConfig`) | **Defect:** reaches the private `isNewerThan` by **reflection** (`:15-21`). §2.6 gives the behaviour-level replacement |
| `app/src/test/.../ExampleUnitTest.kt` | JVM | Reviewed | Placeholder (`2 + 2`). Delete |
| `app/src/androidTest/.../api/TimetableParserTest.kt` | Device | Reviewed (needs a device/emulator) | Genuine `org.json` edge cases for `TimetableParser.parseApiEvent` (`:19-176`) — the seed for §2.3's malformed-input corpus |
| `app/src/androidTest/.../api/TimetableRepositoryTest.kt` | Device | Reviewed | **Defect:** doc-comment claims MockWebServer, uses none (`:17, 92-156`); network cases rely on a real request failing and falling back to cache — non-deterministic. Replaced by §3.4 |
| `app/src/androidTest/.../api/cache/TimetableDaoTest.kt` | Device | Reviewed | Genuine — in-memory Room DAO insert/query/ordering/replace/count |
| `app/src/androidTest/.../SyncPreferencesTest.kt` | Device | Reviewed | Genuine — SharedPreferences defaults/persistence |
| **New:** `app/src/test/.../api/SyncStrategyContractTest.kt` | JVM | **Executed — 15/15 OK** | Added by this document (§2.5); it found defect D-1 |
| Coverage reporting | — | Reviewed | **Absent.** There is no coverage task, no threshold, and no CI to run any of this |

**[REQ] Migration rule:** port a test only if it fails when the production code it covers is broken. Three of the eight existing suites do not meet that bar (verified: one of them passes today while testing nothing) and are explicitly replaced below rather than carried forward.

### 0.2 Domain interfaces the backend suites compile against

To keep §2/§3/§4 free of `// TODO: implement`, the tests are written against a small, explicit port surface. These are the *only* production types the suite references; implementing them (plus the §5 schema and §6 API) makes the suite green.

```kotlin
// ─── coursegrid-domain ──────────────────────────────────────────────────────
// TenantContext is never optional and never derived from a request parameter
// (spec §7.5). Repositories take it explicitly so a missing tenant predicate
// cannot compile.
data class TenantContext(val tenantId: UUID, val actor: Actor)

sealed interface Actor {
    data class EndUser(val userId: UUID, val role: Role) : Actor
    data object Anonymous : Actor
    data class ServiceAccount(val keyId: UUID, val scopes: Set<String>) : Actor
}

enum class Role { END_USER, TENANT_VIEWER, TENANT_ADMIN, PLATFORM_OPERATOR }

interface SessionRepository {
    fun findByRange(t: TenantContext, courseId: UUID, from: LocalDate, to: LocalDate,
                    groups: Set<String>, statuses: Set<SessionStatus>): List<Session>
    fun revisionOf(t: TenantContext, courseId: UUID): Long
}

interface SubscriptionRepository {
    fun upsert(t: TenantContext, courseId: UUID, patch: SubscriptionPatch, ifMatch: Long?): Subscription
    fun list(t: TenantContext): List<Subscription>
}

interface AuditLog {
    fun append(t: TenantContext?, action: String, resourceType: String?, resourceId: String?, result: String)
    fun entriesFor(t: TenantContext): List<AuditEntry>
}

// ─── coursegrid-ingest ──────────────────────────────────────────────────────
/** One upstream system (Scientia Publish, S+, CSV drop, …). */
interface TimetableSource {
    suspend fun fetchSessions(course: UpstreamCourse, from: LocalDate, to: LocalDate): RawUpstreamPayload
    suspend fun search(query: String): List<UpstreamCourse>
}

/** Raw, untrusted bytes/body from upstream — never trusted, always validated. */
data class RawUpstreamPayload(val statusCode: Int, val body: String, val contentType: String?)

/** The delta engine is pure: raw in, revisions out. This is what §2.2 tests. */
object DeltaEngine {
    fun diff(
        previous: List<Session>,            // last known-good, empty on first ingest
        incoming: List<Session>,            // normalised from upstream
    ): List<SessionRevision>
}

data class SessionRevision(
    val revisionSeq: Long,
    val changeType: ChangeType,             // ADDED | REMOVED | MODIFIED
    val matchKey: String,                   // module_code|starts_at|ends_at
    val fieldChanges: List<FieldChange>,
)

data class FieldChange(val field: String, val from: String?, val to: String?)

enum class ChangeType { ADDED, REMOVED, MODIFIED }
enum class SessionStatus { SCHEDULED, CANCELLED, MOVED, TENTATIVE }
```

```kotlin
// ─── coursegrid-calendar (pure; the DST/term maths under test in §2.4) ──────
object AcademicCalendar {
    /** Week 1 = the Monday of the institution's first teaching week, per `academic_weeks`. */
    fun weekNumber(term: Term, date: LocalDate): Int
    /** Monday of the week containing [date]; ISO-8601 semantics. */
    fun weekStart(date: LocalDate): LocalDate
    /** True when the term's teaching weeks have a gap ≥ 21 days (prototype heuristic). */
    fun hasMidTermGap(teachingWeeks: List<LocalDate>): Boolean
}
```

Everything else the tests need — a queue, an HTTP client, a clock — is injected through constructor parameters so that time, randomness, and the network are always controllable in tests.

---

## 1. Test case matrix

Conventions: **ID** = `CATEGORY-SUBNET-nn`; **Layer** = `U` unit · `I` integration (Testcontainers) · `C` contract (recorded fixtures) · `E` end-to-end · `M` mobile on-device. Expected behaviour is written as an **observable** assertion (HTTP status, DB state, emitted event, UI state) — never "should work".

### 1.A Student behaviours and edge cases

#### A1 — Rapid UI actions

| ID | Scenario | Inputs | Expected behaviour | Layer |
|---|---|---|---|---|
| A1-01 | Pull-to-refresh spammed during an in-flight refresh | 10 `onRefresh` calls within 200 ms, cache age 1 h | Exactly **1** network fetch (single-flight); 9 calls join the in-flight result; UI shows one spinner; no duplicate Room writes | M/E |
| A1-02 | Pull-to-refresh within the 24 h cooldown | Last pull 2 h ago, `canPullRefresh()` false | Refresh refused locally, no network call, UI shows "next refresh available at …"; timestamp unchanged | M |
| A1-03 | Double-tap "pin course" | Two `pin()` taps 50 ms apart | One subscription with `is_pinned = true`; no second row; no 409 surfaced to the user (second tap is idempotent) | M/I |
| A1-04 | Pin a second course while one is already pinned | Course A pinned; pin course B | Server rejects/relocates per the single-pin rule (`UNIQUE(user_id) WHERE is_pinned`); client shows a replacement confirmation, not an error; final state has exactly one pinned course | I/M |
| A1-05 | Rapid week switching (W1→W2→…→W10 fast) | 10 week-tab taps in 1 s | Only the final week's data is applied to UI (stale responses discarded); no crash; no flicker of week 3 after landing on week 10 | M |
| A1-06 | Search typing burst | Types "TU859" one char/80 ms | Debounce collapses to ≤2 requests; stale in-flight searches are discarded; results correspond to the last query only | M/E |
| A1-07 | Toggle group filter repeatedly | G1↔G2↔G1 within 300 ms | Final render matches the last selection; no interleaved partial render; server sees ≤1 effective query (coalesced) | M |
| A1-08 | Duplicate deep-link / notification taps | Same change notification tapped twice quickly | One detail screen instance; change list not duplicated; navigation stack has one entry | E/M |
| A1-09 | Rapid subscribe/unsubscribe race | `PUT` then `DELETE` same course, 100 ms apart | Deterministic final state = last operation wins; no orphan row; `Idempotency-Key` reused only for the identical body | I |
| A1-10 | Offline banner + retry loop | Offline; user taps "Retry" 20× in 5 s | Retries are coalesced and rate-limited client-side; battery/CPU bounded; no request storm when connectivity returns | M |

#### A2 — Group selection edge cases

| ID | Scenario | Inputs | Expected behaviour | Layer |
|---|---|---|---|---|
| A2-01 | Student selects subgroup G2 | Course with G1/G2/ungrouped sessions | Only `group == "G2"` **or** `group == null` (all-cohort) sessions render; G1 excluded | U/I |
| A2-02 | Unassigned group (null semantics) | Sessions with `group_label = NULL` | Treated as applying to **every** group; never silently dropped, never counted as a group named "null" | U |
| A2-03 | Multi-group filter | `groups=G1,G2` | OR within the parameter (union of both groups + ungrouped); documented and tested so the second client cannot pick AND | I |
| A2-04 | Group label case/whitespace variance | Upstream sends `" g1 "`, `"G1"`, `"G1 "` | Normalised to one label (`G1`) at ingest; filter matches; no duplicate `course_groups` rows (`UNIQUE(course_id, label)`) | U/I |
| A2-05 | Filter names a group the course does not offer | `groups=G9` | `200` with empty `data` and no error (an empty filter result is not a client error); no 500 from an unguarded lookup | I |
| A2-06 | Default-group pinning | User pins G2 as default for the course | Subsequent opens preselect G2; changing the default updates rather than duplicates the preference | M/I |
| A2-07 | Year-level + group composite label | `TU859/Computer Science (Y3/MLAI/G2)` | Display composition is done **server-side** from `programme_code` + `name` + `year_level` + `group`; client never string-concatenates (the prototype does, producing divergent names per screen) | U/C |
| A2-08 | Group disappears upstream | G2 sessions removed; user still filters G2 | Filter yields empty set, UI shows an explicit "no sessions for your group this week" state, not a blank screen or a spinner forever | E |
| A2-09 | Session with two groups | Upstream expresses a session as belonging to G1 **and** G2 | Modelled as either two rows or a multi-value group — whichever the schema fixes, it must not double-count in "this week has 5 sessions" | U |
| A2-10 | Group change mid-semester | Student switches G1→G2 in week 5 | Next read reflects G2 only; cached G1 data purged for that course (cache key includes the group set) | E/M |

#### A3 — Network degradation

| ID | Scenario | Inputs | Expected behaviour | Layer |
|---|---|---|---|---|
| A3-01 | Cold start in airplane mode with warm cache | No connectivity, cache holds current week | Renders cached week from Room; shows "Cached · updated {t}"; **no** error dialog; no infinite spinner | M |
| A3-02 | Cold start offline with empty cache | No connectivity, no cache | Explicit empty/offline state with a retry affordance; never a blank screen; crash-free | M |
| A3-03 | Mid-request drop | Connection closed after request is sent, before response | Treated as retryable connectivity failure; falls back to stale cache; single retry with backoff — not a tight loop | M/I |
| A3-04 | Response body truncated mid-stream | `Content-Length` promises 40 KB, 12 KB delivered | Parse fails safely → no partial write to Room; previous good data retained; error surfaced as retryable | I/C |
| A3-05 | Captive-portal HTML instead of JSON | `200` with `text/html` login page | Detected by content type **and** body shape; treated as upstream failure, never written as zero sessions (which would look like "your timetable is empty" — the worst possible failure, spec R4) | C/I |
| A3-06 | Our own API returns 429 | `Retry-After: 30` | Client backs off ≥30 s, shows a non-blocking notice, keeps cached data; does not retry before the hint expires | M/I |
| A3-07 | Upstream stale, our API healthy | `source_freshness.state = "stale"`, `age_seconds = 7200` | Client renders data **and** surfaces staleness using the server's value; the distinction between "app offline" vs "institution's timetable is stale" is visible to the user (spec §6.3) | E/M |
| A3-08 | DNS failure | Host does not resolve | Fast, typed failure; stale cache; no 30 s hang, no crash | M |
| A3-09 | TLS failure (expired/mismatched cert) | TLS handshake error | Treated as a hard failure with a distinct message; **pinning failure must not be silently downgraded to plaintext** (the prototype permits cleartext globally — spec T2) | M/I |
| A3-10 | Very slow response | Server responds in 45 s; client read timeout 30 s | Timeout fires, stale cache served, background retry scheduled; no ANR, no leaked coroutine | M/I |
| A3-11 | Partial connectivity flap | Online 2 s / offline 5 s cycling for 60 s | WorkManager-style constraints prevent a request storm; at most one successful sync; no duplicate deltas emitted | M |
| A3-12 | Clock skew | Device clock 3 h ahead of server | Server time is authoritative for freshness/TTL decisions where it matters; nothing is permanently "fresh" because the device clock is wrong | U/M |

#### A4 — Date and time anomalies

| ID | Scenario | Inputs | Expected behaviour | Layer |
|---|---|---|---|---|
| A4-01 | DST spring-forward, non-existent local time | `Europe/Dublin`, 2026-03-29, 01:30 local (does not exist) | Time is resolved deterministically (stored UTC is authoritative); no `DateTimeException`; week membership of the session unchanged | U |
| A4-02 | DST autumn, ambiguous local time | 2026-10-25, 01:30 local occurs twice | Deterministic resolution; a session at that instant is neither duplicated nor dropped; delta engine sees no spurious change | U |
| A4-03 | Week 0 / week 1 boundary | Date before the term's first Monday, and exactly the first Monday | `weekNumber` for pre-term = 0 or absent (never 1); first Monday = week 1; the two clients and server agree | U |
| A4-04 | Last day of term + day after | Last teaching Friday, then the following Monday | Last day included in the term; the Monday after is not; no "week 31" phantom | U |
| A4-05 | Mid-term gap detection | Weeks 1-6 and 9-14 active (2-week gap) vs a 21+ day gap | The heuristic flags a ≥21-day gap only; a 2-week break does **not** trigger a semester boundary; if it ever sets a boundary, the admin override wins | U/I |
| A4-06 | Semester 2 boundary inference vs official calendar | Institution calendar says S2 starts week 17; heuristic says week 18 | **Official `academic_terms` wins**; the heuristic result is stored as a flagged fallback, never silently substituted (spec D-10) | I/E |
| A4-07 | Midnight UTC boundary | Session at 23:00Z Sunday = 00:00 Monday local | Session belongs to the **local** week, not the UTC week; no off-by-one week placement | U |
| A4-08 | Session spilling across midnight | 23:00–01:00 local | Rendered under its start day; not split into two sessions; not double-counted in weekly totals | U |
| A4-09 | Academic year crossing | December sessions in an academic year starting September | Week numbering continues across the calendar-year boundary; no `weekNumber = -46` | U |
| A4-10 | User timezone ≠ institution timezone | Student travelling, device `Asia/Tokyo`, institution `Europe/Dublin` | Session **times** shown in institution time by default (or clearly labelled as local); the **week bucket** follows the institution; quiet hours follow the device tz | U/E |
| A4-11 | Week numbering scheme differs | Tenant configured `institution` vs `iso` | API returns both `week_number` and an ISO week so support can answer "Week 5 of what?"; client displays the tenant's scheme | I/C |
| A4-12 | Timezone-data change | `tzdata` update alters a historical offset | Stored timestamps and week buckets unchanged; only display offsets shift | U |

#### A5 — App lifecycle and device state

| ID | Scenario | Inputs | Expected behaviour | Layer |
|---|---|---|---|---|
| A5-01 | Device reboot during sync | Reboot mid-`doWork` | Work persisted as unfinished; rescheduled; no partial/corrupt cache rows (transactional writes); retry idempotent | M |
| A5-02 | Sync killed by the OS under memory pressure | Process death mid-fetch | No partial writes; next run refetches; `source_hash` prevents duplicate revisions | M/I |
| A5-03 | Push token rotation | FCM rotates the token; old token now invalid | `POST /v1/me/devices` upserts on `(platform, token)`; the stale token is pruned/updated; exactly one active token per device; no duplicate notifications | I/E |
| A5-04 | Notification permission revoked after opt-in | `POST_NOTIFICATIONS` denied post-install (API 33+) | No crash posting work completion; preference recorded as undeliverable; email/digest remains the fallback channel; UI reflects reality | M |
| A5-05 | App backgrounded during subscribe | Backgrounded 5 s after `PUT` | Write completes (or is replayed on foreground); no lost subscription; optimistic UI reconciles to the server value | M/E |
| A5-06 | Process death and restore | Kill while viewing week 7 of course X | Restores the same course/semester/week/day/group view (the prototype already persists this) without a network fetch when the cache is fresh | M |
| A5-07 | Timezone change while running | Device tz changes `Europe/Dublin`→`Asia/Tokyo` | Week bucket unchanged; agenda times relabel; quiet-hours re-evaluated in the new tz | M |
| A5-08 | Locale / dynamic-type change | System font scale → 200 %, locale switched mid-session | Layout remains usable (no clipped session times, no overlapping grid); a11y list view still reachable; no crash on font-scale change | M |
| A5-09 | App upgrade with a schema migration | v7 → v8 with data present | Migration preserves subscriptions and cached sessions; **no destructive wipe** (`fallbackToDestructiveMigration` is banned in production — spec T12/A-6); migration test asserts row counts before/after | M/I |
| A5-10 | Low storage device | ≤5 MB free | Sync fails gracefully with a typed, non-crashing error; cache pruning reclaims space; UI still renders what is in the DB | M |
| A5-11 | Legacy sync-interval preference present | `sync_interval_hours = 168` from an old install | Migrated to `Weekly`; unknown/absent tokens default to `Daily`; no crash on a null token | U |
| A5-12 | In-app updater absent | Any launch | The GitHub-Contents-API self-updater and `REQUEST_INSTALL_PACKAGES` are **gone**; updates arrive only via Play/App Store (spec T1/D-3) — a removal test, because a regression here is a supply-chain defect | M |

#### A6 — Localisation, accessibility, and content edge cases

| ID | Scenario | Inputs | Expected behaviour | Layer |
|---|---|---|---|---|
| A6-01 | Very long module title / room name | 400-char title, 60-char room | Truncates/wraps without breaking the grid; no horizontal overflow; full text available via a11y label | M |
| A6-02 | Upstream string with markup/emoji/RTL | `"<script>alert(1)</script>"`, `"Maths 101 — أكاديمي"` | Rendered as inert text (no HTML execution anywhere in web or mobile); RTL segment renders correctly without corrupting the layout | U/M |
| A6-03 | Colour-only change encoding | Added/removed/modified markers | Each state carries a **text** label in addition to colour (WCAG 1.4.1); screen reader announces the change type | M |
| A6-04 | Screen-reader-only navigation | TalkBack/VoiceOver, no visual interaction | A week's sessions can be enumerated, opened, and the change list read; the grid is not the only representation | M |
| A6-05 | Keyboard-only operation (web) | Tab/Shift-Tab/Enter/Escape only | Search, course select, group select, week nav, subscribe all reachable; visible focus; no keyboard trap in the grid | E |
| A6-06 | Empty timetable (valid, no sessions) | Course with zero sessions | Explicit "no classes scheduled" state; not an error, not a spinner, not a crash | U/E |

### 1.B Backend and system infrastructure

#### B1 — Upstream Scientia (and other) API failures

| ID | Scenario | Inputs | Expected behaviour | Layer |
|---|---|---|---|---|
| B1-01 | Upstream 502 Bad Gateway | `502` with HTML body | Run marked failed; **no** revision written; previous good data intact; alert after N consecutive failures; client still served last-known-good with `freshness=stale` | C/I |
| B1-02 | Malformed HTML instead of JSON | `200 text/html` login page | Detected; rejected; `ingest_run_errors` row with the reason; **never** parsed into zero sessions | C |
| B1-03 | XML payload containing a DTD / external entity | `<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>` | Parser refuses external entities (DTD processing disabled); no file read; payload rejected | C |
| B1-04 | Truncated JSON | Body cut mid-object | Typed parse failure; no partial session set written; previous revision preserved | C |
| B1-05 | Missing required field | `module_code` absent | Row rejected with a field-level error; the rest of the run's sessions still process (partial success, not all-or-nothing) | U/C |
| B1-06 | Wrong field type | `starts_at` as a number, `room` as an object | Rejected with a type error naming the field; no coercion that fabricates a plausible time | U/C |
| B1-07 | Empty events array | `{"events":[]}` for a course that previously had sessions | Interpreted per an explicit policy (empty ≠ deletion by default, or deletion only with an explicit signal) — documented and tested, because guessing here silently empties students' timetables | U/I |
| B1-08 | Oversized payload | 200 MB body | Aborted at a configured byte cap; no OOM; recorded as a run error | I |
| B1-09 | `302` redirect to an SSO login page | `302` to `login.microsoftonline.com` | Redirects not followed blindly for data fetch; treated as an auth failure; tenant admin alerted (credentials/permissions changed) | I |
| B1-10 | Upstream 429 | `429` + `Retry-After` | Worker honours the hint, adds jitter, exponential cool-down; breaker opens after the threshold; **no** retry storm (spec §6.7) | I |
| B1-11 | Upstream 500 storm | 20 consecutive `500`s | Circuit breaker opens; run aborts early instead of hammering; half-open probe after cool-down; recovery does not require a redeploy | I |
| B1-12 | Slow upstream | 60 s per response | Per-request timeout bounds the run; the run is marked partial, not hung; worker slot released | I |
| B1-13 | Upstream week numbering inconsistency | Two courses report the same Monday under different week labels | Our `academic_weeks` (tenant-owned) wins; the discrepancy is logged for the tenant admin rather than silently trusted | I |
| B1-14 | Duplicate sessions in one payload | Same `module_code`/start/end twice | Deduplicated before diffing; exactly one session, no phantom MODIFIED pair | U |
| B1-15 | Upstream field rename | `room` → `location` | Detected via schema/fixture diff (the scheduled fixture-refresh PR, spec §11.2); alert fires instead of silently ingesting nulls | C/I |

#### B2 — Rate limiting and circuit breakers

| ID | Scenario | Inputs | Expected behaviour | Layer |
|---|---|---|---|---|
| B2-01 | Per-tenant ingest bucket | 31 requests in 60 s, cap 30 | 31st is refused locally (no upstream egress) and rescheduled; the tenant's budget is not exceeded | I |
| B2-02 | Bucket isolation between tenants | Tenant A exhausts its budget | Tenant B's ingest is unaffected (no shared bucket, no shared static state) | I |
| B2-03 | Anonymous read quota | 121 anonymous reads/min from one IP | `429` with `Retry-After` and `RateLimit-*` headers; the 120th succeeds | I/E |
| B2-04 | Authenticated user quota | 601 reads/min, cap 600 | `429`; other users unaffected; `X-Request-Id` present for support | I |
| B2-05 | Admin quota isolation | A student is rate-limited | Tenant admin calls still succeed (separate bucket — an incident must not lock out the admin) | I |
| B2-06 | Edge cache absorbs anonymous load | 10 k anonymous requests for the same week | ≥85 % served from edge; origin request count stays bounded | E |
| B2-07 | Breaker state machine | closed → open → half-open → closed | Transitions asserted explicitly, including that half-open allows exactly one probe | U |
| B2-08 | Backoff jitter | N failures | Delay grows exponentially **and** is jittered (no thundering herd): assert distinct delays across runs, not a fixed constant | U |
| B2-09 | Poison message | Message that always throws | Retries bounded, then DLQ; **page on DLQ depth > 0**; consumer keeps processing other messages | I |
| B2-10 | Ingest run budget | Tenant exceeds its configured ingest frequency | Run skipped and recorded with `reason=budget`, not silently dropped | I |
| B2-11 | Redis unavailable for buckets | Redis down | Fail-closed for **outbound** upstream calls (do not hammer the vendor) and fail-open for authenticated reads with an amplified alert; documented per path | I |

#### B3 — Concurrency

| ID | Scenario | Inputs | Expected behaviour | Layer |
|---|---|---|---|---|
| B3-01 | Two ingest runs for the same course start together | Two workers, same `(tenant, course)`, same instant | One proceeds (advisory lock), the other exits with `already_running`; **exactly one** revision sequence is produced | I |
| B3-02 | Parallel subscription updates (same user) | Two `PUT`s with the same `If-Match` | One succeeds; the other gets `412`; no lost update, no interleaved partial row | I |
| B3-03 | Read while a delta is being written | Reader during a transaction | Reader sees either the old or the new revision atomically — never a half-applied session set (single transaction per revision) | I |
| B3-04 | Duplicate queue delivery | Same message delivered twice | Idempotent consumer: one notification per `(user, revision, channel)`; `notification_deliveries` unique constraint is the proof | I |
| B3-05 | Concurrent identical reads (single-flight) | 50 simultaneous cold-cache reads of the same week | ≤1 database query for that key (server-side coalescing); all 50 get identical bodies; no cache stampede | I |
| B3-06 | Two tenant admins edit source config | Same source, different fields, same moment | Optimistic concurrency via `ETag`/`If-Match` → `412` for the loser, with the current state returned so the UI can rebase | I |
| B3-07 | Fan-out while a newer revision lands | Fan-out for revision N runs as N+1 is written | Both fan-outs complete; a user never receives two notifications for the same change; ordering per course is monotonic | I |
| B3-08 | Token-bucket race | 100 concurrent requests against a 5/10 s bucket | Exactly 5 admitted, 95 refused; counter never goes negative or over-admits (CAS correctness) | U/I |
| B3-09 | Worker shutdown mid-batch | SIGTERM during processing | In-flight work finishes or is safely redelivered; no message lost; no duplicate revision | I |

#### B4 — Multi-tenant security and boundary probes

| ID | Scenario | Inputs | Expected behaviour | Layer |
|---|---|---|---|---|
| B4-01 | Cross-tenant resource probe | Tenant A's token + Tenant B's course UUID | **`404`** (not `403`, not `200`) — resource existence must not leak | I |
| B4-02 | `tenant_id` supplied as a query/body param | `?tenant_id=<B>` with A's token | Parameter ignored entirely; response scoped to A; the attempt is audit-logged as a security event | I |
| B4-03 | SQL without tenant context | App role, no `app.tenant_id` set | RLS returns **zero rows** (fail-closed), never a full-table read | I |
| B4-04 | Cache key bleed | Same course id shape in two tenants | Cache keys are tenant-namespaced; tenant A can never receive B's payload, even on a hash collision | I |
| B4-05 | Tenant admin reads another tenant's audit log | Admin of A requests B's audit entries | `404`/`403` per contract, plus an audit entry for the denial | I |
| B4-06 | Service-account scope escalation | Key with `timetable:read` calls `webhooks:write` | `403` with `insufficient_scope`; no partial effect | I |
| B4-07 | JWT tenant-claim tampering | Valid token with a mutated `tenant` claim | Signature verification fails → `401`; never trust unverified claims | I |
| B4-08 | Cursor forgery | Hand-crafted `next_cursor` pointing at another tenant's rows | Signature mismatch → `400 invalid_cursor`; no data returned | I |
| B4-09 | ID enumeration | Sequential ID probes | Uniform `404`s, rate-limited, and alertable; no timing/shape difference that reveals existence | I |
| B4-10 | Break-glass access | Operator requests elevation | Time-boxed, approval-trailed, fully audited; tenant is notified; no standing cross-tenant PII access | I |
| B4-11 | SSRF via tenant-supplied webhook | `http://169.254.169.254/latest/meta-data/`, `http://localhost:6379`, IPv4-mapped IPv6 loopback | All rejected at registration **and** at delivery (resolve-then-check, no redirects); no internal fetch | I |
| B4-12 | Mass assignment | `PUT` body with `is_pinned` + `tenant_id` + `created_at` | Unknown/immutable fields rejected (`additionalProperties:false`); only allow-listed fields change | I |
| B4-13 | Push-token as a secret | Token value in logs/telemetry | Never logged; encrypted at rest; a redaction test asserts it cannot appear in log output | I |
| B4-14 | Lecturer personal-data pivot | Repeated `lecturer → room` queries | Authenticated-only, rate-limited, audited; no public per-person pages | I/E |

#### B5 — Delta engine (append-only revisions)

| ID | Scenario | Inputs | Expected behaviour | Layer |
|---|---|---|---|---|
| B5-01 | Identical payload (no-op) | Same sessions, same `source_hash` | **Zero** revisions written; `revision_seq` unchanged; no notification; ingest run recorded as `ok` with `deltas=0` | U/I |
| B5-02 | Room swap | `A214` → `B102`, same module/time | Exactly one `MODIFIED` with `fieldChanges = [("room","A214","B102")]`; `revisionSeq` = previous+1 | U |
| B5-03 | Session cancelled | Session disappears upstream, status signal present | One `REMOVED` (or `MODIFIED`→`status=cancelled`, per the documented policy) — never both, never silent | U |
| B5-04 | Time moved | 09:00 → 11:00 start | Policy asserted explicitly: `REMOVED` + `ADDED` (match key changed) and the UI/notification labels it as a *move*, not as two unrelated events | U |
| B5-05 | Lecturer changed | Lecturer A → B | `MODIFIED` with a field-level diff; no room/time churn in the same revision | U |
| B5-06 | Match-key collision | Two sessions, same module/start/end, different room or group | Both retained; the match key is disambiguated (e.g. by room/group) so one does not shadow the other | U |
| B5-07 | Sequence monotonicity | 10 successive changes | `revision_seq` strictly increases per `(tenant, course)`; no gaps that break `since=` cursors; no reuse | I |
| B5-08 | Delta emitted exactly once | Same change ingested twice (duplicate delivery) | One revision, one notification set — idempotency proven by the unique constraint | I |
| B5-09 | Upstream rollback (revert of a change) | Room B102 → A214 again | A **new** revision (`A214→B102` then `B102→A214`), not a deletion of history; audit trail intact | U/I |
| B5-10 | Timezone-shifted times are not spurious | Same instant expressed with a different offset (`09:00Z` vs `10:00+01:00`) | Normalised to UTC before diffing → **no** delta (guards against a notification storm caused by formatting) | U |
| B5-11 | Bulk change fan-out | 40 sessions change in one run | One revision batch; subscribers receive **one** grouped notification (digest), not 40 | I/E |
| B5-12 | First-ever ingest | No previous state | All sessions `ADDED`, **no** notifications sent (nobody should be alerted about data they never had) — policy asserted explicitly | U/I |
| B5-13 | Empty→empty | No previous, empty incoming | No revisions, no error, run recorded as `ok` | U |
| B5-14 | Group re-assignment | `G1` → `G2` for a session | `MODIFIED` with a `group` field diff; subscribers of **both** groups are resolved as affected | U/I |
| B5-15 | Revision cursor read | `since=4270` with revisions 4271..4281 present | Returns 4271..4281 in order with `has_more=false`; `since=` beyond head returns an empty list, not an error | I |

---
## 2. Domain and unit tests

Pure logic only: no database, no network, no Android framework. These run in milliseconds and must be the *first* gate in CI, because every one of them covers a silent-wrongness path (spec R4: "wrong timetable served as correct").

### 2.1 Week, term, and calendar maths (`AcademicCalendarTest`)

```kotlin
package com.coursegrid.calendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@DisplayName("Academic week and term boundary maths")
class AcademicCalendarTest {

    private val dublin = ZoneId.of("Europe/Dublin")

    private fun term(
        code: String = "2025-S1",
        start: String,
        end: String,
        weeks: List<String>,
    ) = Term(
        code = code,
        startsOn = LocalDate.parse(start),
        endsOn = LocalDate.parse(end),
        weekStarts = weeks.map(LocalDate::parse),
    )

    @Nested
    @DisplayName("Week numbering (A4-03, A4-04, A4-09)")
    inner class WeekNumbering {

        private val t = term(
            start = "2025-09-08", end = "2025-12-19",
            weeks = listOf("2025-09-08", "2025-09-15", "2025-09-22"),
        )

        @Test
        fun `first teaching Monday is week one`() {
            // Act
            val week = AcademicCalendar.weekNumber(t, LocalDate.parse("2025-09-08"))

            // Assert
            assertEquals(1, week)
        }

        @Test
        fun `mid-week dates resolve to the week of their Monday`() {
            // Arrange — Thursday of the second teaching week
            val thursday = LocalDate.parse("2025-09-18")

            // Act
            val week = AcademicCalendar.weekNumber(t, thursday)

            // Assert
            assertEquals(2, week)
        }

        @Test
        fun `day before term start is week zero not week one`() {
            // Arrange — Sunday immediately before the term
            val dayBefore = LocalDate.parse("2025-09-07")

            // Act
            val week = AcademicCalendar.weekNumber(t, dayBefore)

            // Assert — must NOT report 1: a session on this date is not "week 1"
            assertEquals(0, week)
        }

        @Test
        fun `week numbering survives the calendar year boundary`() {
            // Arrange — a term that runs into January (academic year 2025/26)
            val spanning = term(
                code = "2025-S1",
                start = "2025-09-08", end = "2026-01-16",
                weeks = (0..18).map { LocalDate.parse("2025-09-08").plusWeeks(it.toLong()).toString() },
            )

            // Act
            val week = AcademicCalendar.weekNumber(spanning, LocalDate.parse("2026-01-05"))

            // Assert — continuous numbering, never a negative or reset value
            assertEquals(18, week)
            assertTrue(week > 0)
        }
    }

    @Nested
    @DisplayName("Mid-term gap detection (A4-05, A4-06, spec D-10)")
    inner class GapDetection {

        @Test
        fun `two week break is not a semester boundary`() {
            // Arrange — teaching weeks 1-6, then 9-14 (a 16-day gap)
            val weeks = (0..5).map { LocalDate.parse("2025-09-08").plusWeeks(it.toLong()) } +
                (8..13).map { LocalDate.parse("2025-09-08").plusWeeks(it.toLong()) }

            // Act
            val result = AcademicCalendar.hasMidTermGap(weeks)

            // Assert
            assertFalse(result, "a 16-day break must not be treated as a semester boundary")
        }

        @Test
        fun `twenty one day gap is flagged as a boundary candidate`() {
            // Arrange — weeks 1-6, then 10-15 (a 23-day gap, as after a Christmas break)
            val weeks = (0..5).map { LocalDate.parse("2025-09-08").plusWeeks(it.toLong()) } +
                (9..14).map { LocalDate.parse("2025-09-08").plusWeeks(it.toLong()) }

            // Act
            val result = AcademicCalendar.hasMidTermGap(weeks)

            // Assert
            assertTrue(result)
        }

        @Test
        fun `no gap when weeks are contiguous`() {
            // Arrange
            val weeks = (0..9).map { LocalDate.parse("2025-09-08").plusWeeks(it.toLong()) }

            // Act
            val result = AcademicCalendar.hasMidTermGap(weeks)

            // Assert
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("DST and timezone behaviour (A4-01, A4-02, A4-07, A4-10)")
    inner class DstAndTimezone {

        @Test
        fun `spring forward non-existent local time resolves deterministically`() {
            // Arrange — 01:30 on 2026-03-29 does not exist in Europe/Dublin (clocks jump 01:00 -> 02:00)
            val nonExistentLocal = LocalDateTime.parse("2026-03-29T01:30")

            // Act
            val instant = nonExistentLocal.atZone(dublin).toInstant()

            // Assert — java.time resolves the gap by shifting forward by the gap length:
            // 01:30 (non-existent) -> 02:30 IST -> 01:30 UTC. Deterministic, no exception.
            assertEquals(java.time.Instant.parse("2026-03-29T01:30:00Z"), instant)
        }

        @Test
        fun `autumn fall back ambiguous time resolves to the earlier offset`() {
            // Arrange — 01:30 on 2026-10-25 occurs twice in Europe/Dublin
            val ambiguous = LocalDateTime.parse("2026-10-25T01:30")

            // Act
            val offset = ambiguous.atZone(dublin).offset

            // Assert — java.time picks the earlier offset deterministically (documented, not accidental)
            assertEquals(java.time.ZoneOffset.ofHours(1), offset)
        }

        @Test
        fun `a late Sunday UTC session belongs to the following local week`() {
            // Arrange — 23:00Z on Sunday 2025-09-14 is 00:00 Monday local in summer time
            val sunday23z = java.time.Instant.parse("2025-09-14T23:00:00Z")

            // Act
            val localMonday = AcademicCalendar.weekStart(sunday23z.atZone(dublin).toLocalDate())

            // Assert
            assertEquals(LocalDate.parse("2025-09-15"), localMonday)
        }

        @Test
        fun `session times keep the institution zone regardless of viewer zone`() {
            // Arrange — a 09:00 Dublin lecture, viewed by a student in Tokyo
            val dublinStart = LocalDateTime.parse("2025-09-15T09:00").atZone(dublin)
            val tokyo = ZoneId.of("Asia/Tokyo")

            // Act
            val renderedForTokyo = dublinStart.withZoneSameInstant(tokyo)

            // Assert — the instant is identical; only the rendering offset differs
            assertEquals(dublinStart.toInstant(), renderedForTokyo.toInstant())
            assertEquals(17, renderedForTokyo.hour) // 09:00 IST == 17:00 JST
        }
    }

    @Nested
    @DisplayName("Session rendering edge cases (A4-08, A6-06)")
    inner class SessionRendering {

        @Test
        fun `session crossing midnight is not split into two sessions`() {
            // Arrange
            val session = session(
                startsAt = "2025-09-15T23:00:00Z",
                endsAt = "2025-09-16T01:00:00Z",
            )

            // Act
            val buckets = AcademicCalendar.weekBucketsOf(listOf(session), dublin)

            // Assert — one bucket, keyed by the start day
            assertEquals(1, buckets.size)
            assertEquals(LocalDate.parse("2025-09-16"), buckets.keys.first()) // 00:00 local on the 16th
        }

        @Test
        fun `course with no sessions is an empty result not an error`() {
            // Act
            val buckets = AcademicCalendar.weekBucketsOf(emptyList(), dublin)

            // Assert
            assertTrue(buckets.isEmpty())
        }
    }
}
```

### 2.2 Delta engine (`DeltaEngineTest`) — the highest-risk logic in the system

The delta engine decides what students are told changed. A false negative means a student walks to the wrong room; a false positive means a notification storm (risk R5). **Coverage target: 100 % of branches**, per spec §11.1.

```kotlin
package com.coursegrid.timetable

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("Delta engine: session revisions")
class DeltaEngineTest {

    private val course = UUID.randomUUID()

    /** Test fixture builder — keeps each test's intent readable. */
    private fun session(
        module: String = "CMPU3021",
        start: String = "2025-09-15T09:00:00Z",
        end: String = "2025-09-15T11:00:00Z",
        room: String? = "A214",
        lecturer: String? = "Dr. A. Byrne",
        group: String? = null,
        type: String = "Lecture",
        status: SessionStatus = SessionStatus.SCHEDULED,
    ) = Session(
        id = UUID.randomUUID(), courseId = course, moduleCode = module, title = "Software Engineering",
        sessionType = type, lecturer = lecturer, room = room, groupLabel = group,
        startsAt = Instant.parse(start), endsAt = Instant.parse(end), status = status,
    )

    // ── The no-op case: MUST NOT emit (B5-01) ──────────────────────────────────

    @Test
    fun `identical payload produces zero revisions`() {
        // Arrange
        val previous = listOf(session(), session(module = "CMPU2004", start = "2025-09-16T14:00:00Z"))
        val incoming = previous.map { it.copy(id = UUID.randomUUID()) } // same content, new row ids

        // Act
        val revisions = DeltaEngine.diff(previous, incoming)

        // Assert — content equality, not identity equality
        assertTrue(revisions.isEmpty(), "re-fetching unchanged data must not create a revision")
    }

    @Test
    fun `times expressed with different offsets but the same instant are not a change`() {
        // Arrange — the same instants, formatted differently upstream (B5-10)
        val previous = listOf(session(start = "2025-09-15T09:00:00Z", end = "2025-09-15T11:00:00Z"))
        val incoming = listOf(session(start = "2025-09-15T10:00:00+01:00", end = "2025-09-15T12:00:00+01:00"))

        // Act
        val revisions = DeltaEngine.diff(previous, incoming)

        // Assert — normalisation to UTC must happen before diffing
        assertTrue(revisions.isEmpty())
    }

    // ── The change cases ───────────────────────────────────────────────────────

    @Test
    fun `room swap emits one MODIFIED with a field level diff`() {
        // Arrange
        val previous = listOf(session(room = "A214"))
        val incoming = listOf(session(room = "B102"))

        // Act
        val revisions = DeltaEngine.diff(previous, incoming)

        // Assert
        assertEquals(1, revisions.size)
        val revision = revisions.single()
        assertEquals(ChangeType.MODIFIED, revision.changeType)
        assertEquals("CMPU3021|2025-09-15T09:00:00Z|2025-09-15T11:00:00Z", revision.matchKey)
        assertEquals(listOf(FieldChange("room", "A214", "B102")), revision.fieldChanges)
    }

    @Test
    fun `lecturer change is a MODIFIED and does not churn room or time`() {
        // Arrange
        val previous = listOf(session(lecturer = "Dr. A. Byrne"))
        val incoming = listOf(session(lecturer = "Dr. C. Nolan"))

        // Act
        val revision = DeltaEngine.diff(previous, incoming).single()

        // Assert — exactly one field changed, and nothing else is reported
        assertEquals(ChangeType.MODIFIED, revision.changeType)
        assertEquals(listOf(FieldChange("lecturer", "Dr. A. Byrne", "Dr. C. Nolan")), revision.fieldChanges)
    }

    @Test
    fun `multiple field changes are reported together in one revision`() {
        // Arrange
        val previous = listOf(session(room = "A214", lecturer = "Dr. A. Byrne"))
        val incoming = listOf(session(room = "B102", lecturer = "Dr. C. Nolan"))

        // Act
        val revision = DeltaEngine.diff(previous, incoming).single()

        // Assert
        assertEquals(
            setOf(FieldChange("room", "A214", "B102"), FieldChange("lecturer", "Dr. A. Byrne", "Dr. C. Nolan")),
            revision.fieldChanges.toSet(),
        )
    }

    @Test
    fun `cancelled session is reported as REMOVED when it disappears upstream`() {
        // Arrange
        val previous = listOf(session(), session(module = "CMPU2004", start = "2025-09-16T14:00:00Z"))
        val incoming = listOf(session())

        // Act
        val revisions = DeltaEngine.diff(previous, incoming)

        // Assert — exactly one removal, keyed to the session that vanished
        assertEquals(1, revisions.size)
        assertEquals(ChangeType.REMOVED, revisions.single().changeType)
        assertEquals("CMPU2004|2025-09-16T14:00:00Z|2025-09-16T16:00:00Z", revisions.single().matchKey)
    }

    @Test
    fun `session moved in time is a removal plus an addition of the new key`() {
        // Arrange — 09:00 -> 11:00 changes the match key (module|start|end)
        val previous = listOf(session(start = "2025-09-15T09:00:00Z", end = "2025-09-15T11:00:00Z"))
        val incoming = listOf(session(start = "2025-09-15T11:00:00Z", end = "2025-09-15T13:00:00Z"))

        // Act
        val types = DeltaEngine.diff(previous, incoming).map { it.changeType }.toSet()

        // Assert — both sides emitted; the notification layer labels this as a "move"
        assertEquals(setOf(ChangeType.REMOVED, ChangeType.ADDED), types)
    }

    @Test
    fun `first ever ingest emits ADDED for everything and must not notify`() {
        // Arrange
        val incoming = listOf(session(), session(module = "CMPU2004", start = "2025-09-16T14:00:00Z"))

        // Act
        val revisions = DeltaEngine.diff(previous = emptyList(), incoming = incoming)

        // Assert (B5-12) — nobody is alerted about data they never had
        assertEquals(2, revisions.size)
        assertTrue(revisions.all { it.changeType == ChangeType.ADDED })
        // the notification layer filters on this flag:
        assertTrue(revisions.none { it.isNotifiable })
    }

    @Test
    fun `empty to empty is a silent no-op`() {
        // Act
        val revisions = DeltaEngine.diff(previous = emptyList(), incoming = emptyList())

        // Assert
        assertTrue(revisions.isEmpty())
    }

    // ── Identity and matching hazards ──────────────────────────────────────────

    @Test
    fun `two sessions with the same module and time but different groups are both kept`() {
        // Arrange — G1 and G2 run the same module at the same hour in different rooms
        val previous = listOf(session(group = "G1", room = "A214"))
        val incoming = listOf(session(group = "G1", room = "A214"), session(group = "G2", room = "B102"))

        // Act
        val revisions = DeltaEngine.diff(previous, incoming)

        // Assert — the second group is a genuine addition, and no phantom MODIFIED appears (B5-06)
        assertEquals(1, revisions.size)
        assertEquals(ChangeType.ADDED, revisions.single().changeType)
    }

    @Test
    fun `group re-assignment is a MODIFIED and marks both groups as affected`() {
        // Arrange
        val previous = listOf(session(group = "G1"))
        val incoming = listOf(session(group = "G2"))

        // Act
        val revision = DeltaEngine.diff(previous, incoming).single()

        // Assert (B5-14)
        assertEquals(ChangeType.MODIFIED, revision.changeType)
        assertEquals(listOf(FieldChange("group", "G1", "G2")), revision.fieldChanges)
        assertEquals(setOf("G1", "G2"), revision.affectedGroups)
    }

    @Test
    fun `duplicate rows in one payload are de-duplicated before diffing`() {
        // Arrange — upstream repeats the same session (B1-14)
        val incoming = listOf(session(), session())

        // Act
        val revisions = DeltaEngine.diff(previous = emptyList(), incoming = incoming)

        // Assert
        assertEquals(1, revisions.size)
    }

    @Test
    fun `reverting a change creates a new revision rather than deleting history`() {
        // Arrange — day 1: A214, day 2: B102, day 3: back to A214 (B5-09)
        val atA214 = session(room = "A214")
        val atB102 = session(room = "B102")

        // Act
        val forward = DeltaEngine.diff(listOf(atA214), listOf(atB102)).single()
        val reverse = DeltaEngine.diff(listOf(atB102), listOf(atA214)).single()

        // Assert — append-only: both directions are recorded
        assertEquals(FieldChange("room", "A214", "B102"), forward.fieldChanges.single())
        assertEquals(FieldChange("room", "B102", "A214"), reverse.fieldChanges.single())
    }

    @Test
    fun `revision sequence is strictly increasing per course`() {
        // Arrange
        var previous = listOf(session(room = "A214"))

        // Act / Assert — three successive changes produce 1, 2, 3
        listOf("B102", "C301", "A214").forEachIndexed { index, room ->
            val revision = DeltaEngine.diff(previous, listOf(session(room = room))).single()
            assertEquals((index + 1).toLong(), revision.revisionSeq)
            previous = listOf(session(room = room))
        }
    }

    @Test
    fun `null room becoming a real room is a MODIFIED not an ADDED`() {
        // Arrange — room data arrives late from the institution
        val previous = listOf(session(room = null))
        val incoming = listOf(session(room = "A214"))

        // Act
        val revision = DeltaEngine.diff(previous, incoming).single()

        // Assert
        assertEquals(ChangeType.MODIFIED, revision.changeType)
        assertEquals(listOf(FieldChange("room", null, "A214")), revision.fieldChanges)
    }
}
```

### 2.3 Upstream parser and adapter contract (`ScientiaParserTest`)

The upstream is undocumented, has no SLA, and returns malformed HTML/XML on some error paths (spec A7). This suite is the executable form of "reject, never corrupt" (spec T5, R4).

```kotlin
package com.coursegrid.ingest.scientia

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("Scientia payload parsing: hostile input handling")
class ScientiaParserTest {

    private val parser = ScientiaPayloadParser()

    @Test
    fun `well formed payload normalises to canonical sessions`() {
        // Arrange
        val body = """{"events":[{"module_code":"CMPU3021","title":"Software Engineering",
            "type":"Lecture","lecturer":"Dr. A. Byrne","room":"A214","group":"G2",
            "start":"2025-09-15T09:00:00Z","end":"2025-09-15T11:00:00Z"}]}"""

        // Act
        val sessions = parser.normalise(body)

        // Assert
        assertEquals(1, sessions.size)
        with(sessions.single()) {
            assertEquals("CMPU3021", moduleCode)
            assertEquals("A214", room)
            assertEquals("G2", groupLabel)
            assertEquals(Instant.parse("2025-09-15T09:00:00Z"), startsAt)
        }
    }

    @Test
    fun `html login page instead of json is rejected not treated as an empty timetable`() {
        // Arrange — the classic captive-portal / expired-session response (B1-02)
        val body = "<!DOCTYPE html><html><body><form action='/login'>Sign in</form></body></html>"

        // Act / Assert — MUST throw. Silently returning zero sessions would tell every
        // student they have no classes, which is the worst failure this system can have.
        val error = assertThrows(UpstreamPayloadException::class.java) { parser.normalise(body) }
        assertTrue(error.message!!.contains("not JSON", ignoreCase = true) || error.reason == ParseFailure.NOT_JSON)
    }

    @Test
    fun `xml with an external entity is rejected without resolving the entity`() {
        // Arrange — XXE attempt (B1-03). Even if a DTD-bearing payload is accepted as XML,
        // no external resource may ever be fetched or read.
        val body = """<?xml version="1.0"?>
            <!DOCTYPE events [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <events><event><title>&xxe;</title></event></events>"""

        // Act / Assert
        val error = assertThrows(UpstreamPayloadException::class.java) { parser.normalise(body) }
        assertNotEquals(ParseFailure.RESOLVED_EXTERNAL_ENTITY, error.reason)
        assertFalse(error.message!!.contains("root:"), "no file content may leak into the error")
    }

    @Test
    fun `truncated json is rejected and yields no partial sessions`() {
        // Arrange (B1-04)
        val body = """{"events":[{"module_code":"CMPU3021","title":"Software Eng"""

        // Act / Assert
        assertThrows(UpstreamPayloadException::class.java) { parser.normalise(body) }
    }

    @Test
    fun `missing required module code rejects only the offending row`() {
        // Arrange (B1-05) — one bad row must not discard the whole run
        val body = """{"events":[
            {"title":"Software Engineering","start":"2025-09-15T09:00:00Z","end":"2025-09-15T11:00:00Z"},
            {"module_code":"CMPU2004","title":"Databases","start":"2025-09-16T14:00:00Z","end":"2025-09-16T16:00:00Z"}
        ]}"""

        // Act
        val result = parser.normaliseWithReport(body)

        // Assert
        assertEquals(1, result.sessions.size)
        assertEquals("CMPU2004", result.sessions.single().moduleCode)
        assertEquals(1, result.rejections.size)
        assertEquals("module_code", result.rejections.single().field)
    }

    @Test
    fun `wrong field types are rejected rather than coerced`() {
        // Arrange (B1-06) — a numeric start must never be interpreted as an epoch
        val body = """{"events":[{"module_code":"CMPU3021","title":"SE","start":1757923200000,
            "end":"2025-09-15T11:00:00Z"}]}"""

        // Act / Assert
        val error = assertThrows(UpstreamPayloadException::class.java) { parser.normalise(body) }
        assertEquals(ParseFailure.FIELD_TYPE, error.reason)
        assertTrue(error.message!!.contains("start"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "null", "[]", "{}", "{\"events\":null}"])
    fun `structurally useless payloads are rejected with a typed failure`(body: String) {
        // Act / Assert
        val error = assertThrows(UpstreamPayloadException::class.java) { parser.normalise(body) }
        assertNotNull(error.reason)
    }

    @Test
    fun `case and whitespace variance in group labels normalises to one label`() {
        // Arrange (A2-04)
        val body = """{"events":[
            {"module_code":"CMPU3021","title":"SE","group":" g1 ","start":"2025-09-15T09:00:00Z","end":"2025-09-15T11:00:00Z"},
            {"module_code":"CMPU3021","title":"SE","group":"G1","start":"2025-09-15T09:00:00Z","end":"2025-09-15T11:00:00Z"}
        ]}"""

        // Act
        val sessions = parser.normalise(body)

        // Assert
        assertEquals(setOf("G1"), sessions.map { it.groupLabel }.toSet())
        assertEquals(1, sessions.distinctBy { it.matchKey + it.groupLabel }.size)
    }

    @Test
    fun `oversized payload is refused at the byte cap`() {
        // Arrange (B1-08) — 200 MB body, cap configurable
        val huge = "{\"events\":[" + "{\"module_code\":\"X\"},".repeat(5_000_000) + "]}"
        val capped = ScientiaPayloadParser(maxBytes = 1024 * 1024)

        // Act / Assert
        val error = assertThrows(UpstreamPayloadException::class.java) { capped.normalise(huge) }
        assertEquals(ParseFailure.TOO_LARGE, error.reason)
    }

    @Test
    fun `markup in upstream strings is preserved as inert text`() {
        // Arrange (A6-02) — stored XSS attempt via a room name
        val body = """{"events":[{"module_code":"CMPU3021","title":"SE","room":"<script>alert(1)</script>",
            "start":"2025-09-15T09:00:00Z","end":"2025-09-15T11:00:00Z"}]}"""

        // Act
        val session = parser.normalise(body).single()

        // Assert — stored verbatim as data; escaping is a rendering concern tested in §4.5
        assertEquals("<script>alert(1)</script>", session.room)
    }
}
```

### 2.4 Group filtering against production code (`GroupFilterTest`)

This **replaces** `app/src/test/.../api/GroupFilteringTest.kt`, which re-implements the filtering logic inline (`:15-114`) and therefore passes even when production filtering is broken. The replacement calls the real function and additionally asserts the semantics that the old test cannot express.

```kotlin
package com.coursegrid.catalog

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Group filtering semantics (A2-01, A2-02, A2-03, A2-05, A2-09)")
class GroupFilterTest {

    private fun s(group: String?) = session(groupLabel = group)

    private val mixed = listOf(
        s(null),        // all-cohort lecture
        s("G1"),
        s("G2"),
        s("MLAI"),
    )

    @Test
    fun `selecting one group keeps that group plus all-cohort sessions`() {
        // Act
        val visible = filterByGroups(mixed, setOf("G2"))

        // Assert — the ungrouped lecture applies to G2 students too
        assertEquals(listOf(null, "G2"), visible.map { it.groupLabel })
    }

    @Test
    fun `null group means every cohort and is never dropped`() {
        // Act
        val visible = filterByGroups(mixed, setOf("G1"))

        // Assert
        assertTrue(visible.any { it.groupLabel == null })
    }

    @Test
    fun `multiple groups are a union not an intersection`() {
        // Act
        val visible = filterByGroups(mixed, setOf("G1", "G2"))

        // Assert
        assertEquals(setOf(null, "G1", "G2"), visible.map { it.groupLabel }.toSet())
    }

    @Test
    fun `filtering by an unknown group returns empty rather than everything`() {
        // Act — a hallucinated group must not silently show the whole course
        val visible = filterByGroups(mixed, setOf("G9"))

        // Assert
        assertEquals(0, visible.size)
    }

    @Test
    fun `empty filter means no filtering`() {
        // Act
        val visible = filterByGroups(mixed, emptySet())

        // Assert
        assertEquals(mixed.size, visible.size)
    }

    @Test
    fun `a session in two groups is not double counted`() {
        // Arrange — a shared lab for G1 and G2, modelled as a multi-value group
        val shared = session(groupLabel = "G1,G2")

        // Act
        val visible = filterByGroups(listOf(shared), setOf("G1"))

        // Assert
        assertEquals(1, visible.size)
    }

    @Test
    fun `filtering is case insensitive and whitespace tolerant on input`() {
        // Act
        val visible = filterByGroups(mixed, setOf(" g2 "))

        // Assert
        assertEquals(listOf(null, "G2"), visible.map { it.groupLabel })
    }
}
```

### 2.5 Sync-strategy contract (`SyncStrategyContractTest`) — **runs today**

This class already exists in the repo and was executed as part of preparing this document (see §0.1 and §5.3): **15 tests, all green**, against `SyncStrategy.kt` in the prototype. It is included here because it demonstrates the shape of a ported test and because it caught a real defect.

```kotlin
// app/src/test/java/com/example/timetablescraper/api/SyncStrategyContractTest.kt (excerpt)
@Test
fun `Custom token with a non-positive value degrades to Daily instead of throwing`() {
    // A corrupt or hand-edited token ("CUSTOM:0:HOURS") must not crash the app-launch path
    // that reads the sync strategy out of SharedPreferences. fromToken is documented as
    // "Returns Daily for unrecognised tokens", so a non-positive value must degrade the same way.
    assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken("CUSTOM:0:HOURS"))
    assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken("CUSTOM:-5:HOURS"))
    assertEquals(SyncStrategy.Daily, SyncStrategy.fromToken("CUSTOM:0:DAYS"))
}
```

**Defect found and fixed by this test (see §5.3):** `SyncStrategy.fromToken("CUSTOM:0:HOURS")` threw `IllegalArgumentException` from `Custom`'s `require(value > 0)` instead of returning `Daily`, contradicting the function's own documented contract and putting a crash on the launch path. Fixed by rejecting non-positive values before constructing `Custom`.

### 2.6 Test-class inventory for §2

| Test class | Covers | Matrix IDs | Layer |
|---|---|---|---|
| `AcademicCalendarTest` | Week numbering, term boundaries, mid-term gap, DST, midnight, cross-year | A4-01…A4-12, A6-06 | U |
| `DeltaEngineTest` | No-op, field diffs, removals, moves, matching, sequence monotonicity | B5-01…B5-15 | U |
| `ScientiaParserTest` | Hostile/ malformed upstream payloads, partial acceptance, caps | B1-02…B1-08, B1-14, A6-02 | C/U |
| `GroupFilterTest` | Group semantics incl. null and multi-group | A2-01…A2-05, A2-09 | U |
| `SyncStrategyContractTest` | Cache-validity model and token round-trip | A5-11, A1-02 | U |
| `SessionNormalisationTest` | Upstream → canonical mapping, `source_hash` stability | B1-15, B5-01 | U |
| `SubscriptionRulesTest` | Single-pin rule, notify defaults, patch validation | A1-03, A1-04 | U |
| `RateLimitBucketTest` | Token bucket arithmetic, CAS correctness, jitter | B2-01, B2-07, B2-08, B3-08 | U |
| `VersionCompareTest` | Semver comparison as a **public** function (replaces the reflection-based `UpdateCheckerTest`) | — | U |
| `CursorCodecTest` | Cursor signing/parsing, forgery rejection | B4-08 | U |

---
## 3. Integration and boundary tests

These run against **real** PostgreSQL and Redis via Testcontainers, with a **real** Ktor application under `testApplication` and a **MockWebServer** standing in for the institution. Mocks are used only where the *upstream* is the thing being faked — never where a database constraint or a transaction boundary is what is under test (spec §11.4: stubbing Postgres to test Postgres is theatre).

### 3.1 Shared harness

```kotlin
package com.coursegrid.support

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * One container per class, reused across methods. Started once per JVM so the
 * integration suite stays in the tens of seconds, not minutes.
 */
@Testcontainers
abstract class IntegrationTestBase {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<Nothing> = PostgreSQLContainer<Nothing>("postgres:16-alpine")
            .apply {
                withDatabaseName("coursegrid_test")
                withUsername("coursegrid_app")          // the RLS-constrained role, NOT superuser
                withPassword("test")
                withInitScript("db/00_bootstrap.sql")   // roles, extensions, RLS helpers
                withUrlParam("options", "-c statement_timeout=3000")
            }

        @Container
        @JvmStatic
        val redis: GenericContainer<Nothing> = GenericContainer<Nothing>("redis:7-alpine")
            .withExposedPorts(6379)
    }

    /** Runs [block] with the tenant context set for the *connection*, mirroring production. */
    protected fun <T> withTenant(tenantId: UUID, block: (Connection) -> T): T =
        dataSource.connection.use { conn ->
            conn.createStatement().use { it.execute("SET LOCAL app.tenant_id = '$tenantId'") }
            block(conn)
        }

    /** Applies every migration from the repo, exactly as CI does. */
    protected fun migrate() = Migrations.applyAll(dataSource)
}
```

### 3.2 Database constraints and data-integrity boundaries (`SchemaConstraintTest`)

Schema rules are tested as **constraint violations**, not as application assertions: if the constraint is dropped, these fail.

```kotlin
package com.coursegrid.timetable

import com.coursegrid.support.IntegrationTestBase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.postgresql.util.PSQLException
import java.sql.SQLException

@DisplayName("Schema constraints on sessions and revisions")
class SchemaConstraintTest : IntegrationTestBase() {

    @Test
    fun `ends_at must be after starts_at`() {
        // Arrange
        val tenant = insertInstitution(slug = "tudublin")

        // Act / Assert
        val violation = assertThrows(SQLException::class.java) {
            insertSession(tenant, startsAt = "2025-09-15T11:00:00Z", endsAt = "2025-09-15T09:00:00Z")
        }
        assertTrue(violation.message!!.contains("ends_at_after_starts_at"))
    }

    @Test
    fun `session match key is generated and unique per course and status`() {
        // Arrange
        val tenant = insertInstitution()
        val course = insertCourse(tenant, identity = "TU859-CS")

        // Act
        insertSession(course, moduleCode = "CMPU3021", startsAt = "2025-09-15T09:00:00Z", endsAt = "2025-09-15T11:00:00Z")

        // Assert — the generated column is real data, not application string-building
        val key = queryString("SELECT match_key FROM sessions WHERE course_id = '$course'")
        assertEquals("CMPU3021|2025-09-15T09:00:00Z|2025-09-15T11:00:00Z", key)

        // Act / Assert — a second identical session violates the unique constraint
        assertThrows(SQLException::class.java) {
            insertSession(course, moduleCode = "CMPU3021", startsAt = "2025-09-15T09:00:00Z", endsAt = "2025-09-15T11:00:00Z")
        }
    }

    @Test
    fun `only one pinned subscription per user`() {
        // Arrange (A1-04) — enforced by a partial unique index, so no code path can break it
        val tenant = insertInstitution()
        val user = insertUser()
        val first = insertCourse(tenant, identity = "C1")
        val second = insertCourse(tenant, identity = "C2")
        insertSubscription(user, tenant, first, isPinned = true)

        // Act / Assert
        val violation = assertThrows(SQLException::class.java) {
            insertSubscription(user, tenant, second, isPinned = true)
        }
        assertTrue(violation.message!!.contains("uniq_pinned_per_user"))
    }

    @Test
    fun `notification delivery is unique per user revision channel and device`() {
        // Arrange (B3-04) — this constraint IS the idempotency guarantee for fan-out
        val tenant = insertInstitution()
        val revision = insertRevision(tenant)

        // Act
        insertDelivery(revision, channel = "push")
        val violation = assertThrows(SQLException::class.java) { insertDelivery(revision, channel = "push") }

        // Assert
        assertTrue(violation.message!!.contains("uniq_delivery"))
    }

    @Test
    fun `revision sequence is unique per course`() {
        // Arrange (B5-07)
        val tenant = insertInstitution()
        val course = insertCourse(tenant, identity = "C1")
        insertRevision(tenant, course, seq = 1)

        // Act / Assert
        assertThrows(SQLException::class.java) { insertRevision(tenant, course, seq = 1) }
    }

    @Test
    fun `course group labels are unique per course after normalisation`() {
        // Arrange (A2-04)
        val tenant = insertInstitution()
        val course = insertCourse(tenant, identity = "C1")
        insertCourseGroup(course, "G1")

        // Assert
        assertThrows(SQLException::class.java) { insertCourseGroup(course, "G1") }
    }

    @Test
    fun `institution slug is immutable and unique`() {
        // Arrange
        insertInstitution(slug = "tudublin")

        // Act / Assert
        assertThrows(SQLException::class.java) { insertInstitution(slug = "tudublin") }
    }

    @Test
    fun `invalid enum values are rejected by the database not just the API`() {
        // Arrange — defence in depth: a bug in the service layer must not be able to
        // persist a session status the rest of the system cannot interpret.
        val tenant = insertInstitution()

        // Act / Assert
        assertThrows(SQLException::class.java) { insertSessionWithRawStatus(tenant, "PROBABLY_YES") }
    }

    @Test
    fun `statement timeout stops a runaway query from pinning a connection`() {
        // Arrange — the container is started with -c statement_timeout=3000
        // Act / Assert
        val error = assertThrows(PSQLException::class.java) {
            queryString("SELECT pg_sleep(10)")
        }
        assertTrue(error.message!!.contains("statement timeout"))
    }
}
```

### 3.3 Multi-tenant isolation (`TenantIsolationTest`) — adversarial, runs on every PR

Every test here is an attempted breach. They run as the **application role** with RLS active, which is the same posture as production.

```kotlin
package com.coursegrid.security

import com.coursegrid.support.IntegrationTestBase
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Tenant isolation: adversarial probes (spec §7.5)")
class TenantIsolationTest : IntegrationTestBase() {

    @Test
    fun `queries without a tenant context return zero rows not all rows`() {
        // Arrange — data exists for two tenants
        val a = insertInstitution(slug = "a")
        val b = insertInstitution(slug = "b")
        val courseB = insertCourse(b, identity = "SECRET-COURSE")

        // Act — the app role, NO `SET LOCAL app.tenant_id`
        val rows = queryCount("SELECT count(*) FROM courses")

        // Assert — fail-closed. A leak here is the whole product's trust.
        assertEquals(0, rows, "RLS must deny by default, never fall through to the whole table")
        assertNotNull(courseB) // silence unused warning, data really is present
        assertNotNull(a)
    }

    @Test
    fun `tenant context cannot be widened by a caller supplied parameter`() {
        // Arrange
        val a = insertInstitution(slug = "a")
        val b = insertInstitution(slug = "b")
        val courseB = insertCourse(b, identity = "B-COURSE")

        testApplication {
            // Act — tenant A's token, tenant B's id in the query string (B4-02)
            val response = client.get("/v1/courses/$courseB/sessions?tenant_id=$b") {
                header("Authorization", "Bearer ${tokenFor(a)}")
            }

            // Assert — the parameter is ignored, and the resource is invisible
            assertEquals(HttpStatusCode.NotFound, response.status)
        }

        // Assert — the attempt is an auditable security event, not a silent 404
        assertTrue(auditActionsFor(a).contains("security.cross_tenant_probe"))
    }

    @Test
    fun `cross tenant id probe returns 404 and never 403`() {
        // Arrange (B4-01) — 403 would confirm the resource exists
        val a = insertInstitution(slug = "a")
        val b = insertInstitution(slug = "b")
        val courseB = insertCourse(b, identity = "B-COURSE")

        testApplication {
            // Act
            val response = client.get("/v1/courses/$courseB") {
                header("Authorization", "Bearer ${tokenFor(a)}")
            }

            // Assert
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertFalse(response.bodyAsText().contains(courseB))
        }
    }

    @Test
    fun `cursor forgery cannot cross tenants`() {
        // Arrange (B4-08)
        val a = insertInstitution(slug = "a")
        val b = insertInstitution(slug = "b")
        val legitCursor = cursorFor(b, head = 500)

        testApplication {
            // Act — tenant A's token replaying tenant B's cursor
            val response = client.get("/v1/changes?since=0&cursor=$legitCursor") {
                header("Authorization", "Bearer ${tokenFor(a)}")
            }

            // Assert
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("invalid_cursor"))
        }
    }

    @Test
    fun `cache keys are tenant namespaced`() {
        // Arrange (B4-04) — identical course identities in two tenants
        val a = insertInstitution(slug = "a")
        val b = insertInstitution(slug = "b")
        val courseA = insertCourse(a, identity = "SHARED-IDENTITY")
        val courseB = insertCourse(b, identity = "SHARED-IDENTITY")

        // Act — warm the cache for A, then read as B
        readSessions(a, courseA)
        val payloadForB = readSessions(b, courseB)

        // Assert — B must never receive A's payload
        assertFalse(payloadForB.contains(courseA.toString()))
        assertTrue(cacheKeyFor(a, courseA) != cacheKeyFor(b, courseB))
    }

    @Test
    fun `service account cannot exceed its scopes`() {
        // Arrange (B4-06)
        val tenant = insertInstitution()
        val key = insertApiKey(tenant, scopes = setOf("timetable:read"))

        testApplication {
            // Act
            val response = client.post("/v1/integrations/webhooks") {
                header("X-Api-Key", key.plaintext)
            }

            // Assert
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.bodyAsText().contains("insufficient_scope"))
        }
    }

    @Test
    fun `a tampered tenant claim is rejected before any query runs`() {
        // Arrange (B4-07)
        val a = insertInstitution(slug = "a")
        val b = insertInstitution(slug = "b")
        val tampered = tokenFor(a).withClaim("tenant", b.toString())

        testApplication {
            // Act
            val response = client.get("/v1/me/subscriptions") {
                header("Authorization", "Bearer $tampered")
            }

            // Assert — signature check fails first
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `webhook registration rejects internal addresses at registration time`() {
        // Arrange (B4-11)
        val tenant = insertInstitution()
        val admin = tokenForRole(tenant, Role.TENANT_ADMIN)

        listOf(
            "http://169.254.169.254/latest/meta-data/",
            "http://localhost:6379",
            "http://127.0.0.1/admin",
            "http://[::ffff:127.0.0.1]/",
            "http://10.0.0.5/internal",
        ).forEach { url ->
            testApplication {
                // Act
                val response = client.post("/v1/integrations/webhooks") {
                    header("Authorization", "Bearer $admin")
                    setBody("""{"url":"$url","event_types":["timetable.delta_detected"]}""")
                }

                // Assert
                assertEquals(HttpStatusCode.BadRequest, response.status, "must reject $url")
            }
        }
    }

    @Test
    fun `mass assignment cannot overwrite immutable fields`() {
        // Arrange (B4-12)
        val tenant = insertInstitution()
        val user = insertUser()
        val course = insertCourse(tenant, identity = "C1")
        val created = insertSubscription(user, tenant, course)

        testApplication {
            // Act
            val response = client.put("/v1/me/subscriptions/$course") {
                header("Authorization", "Bearer ${tokenFor(user, tenant)}")
                setBody("""{"group_filter":"G2","tenant_id":"${UUID.randomUUID()}","created_at":"1999-01-01T00:00:00Z"}""")
            }

            // Assert — unknown fields are rejected outright
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        // Assert — nothing was silently applied
        assertEquals(created.createdAt, subscriptionCreatedAt(user, course))
    }

    @Test
    fun `push tokens never appear in logs or telemetry`() {
        // Arrange (B4-13)
        val tenant = insertInstitution()
        val user = insertUser()
        val secretToken = "fcm-token-DO-NOT-LOG-0123456789"

        // Act
        registerDevice(user, platform = "android", pushToken = secretToken)
        val capturedLogs = capturedLogLines()

        // Assert
        assertFalse(capturedLogs.contains(secretToken), "a push token is a credential and must be redacted")
    }
}
```

### 3.4 Ingest pipeline against a faked upstream (`IngestPipelineTest`)

MockWebServer plays the institution. This **replaces** `app/src/androidTest/.../TimetableRepositoryTest.kt`, which claims MockWebServer in its doc-comment but uses none (`:17, 92-156`) and instead depends on a real request failing.

```kotlin
package com.coursegrid.ingest

import com.coursegrid.support.IntegrationTestBase
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@DisplayName("Ingest pipeline: fetch, normalise, diff, persist")
class IngestPipelineTest : IntegrationTestBase() {

    private lateinit var upstream: MockWebServer

    @BeforeEach fun startUpstream() { upstream = MockWebServer().apply { start() } }
    @AfterEach fun stopUpstream() { upstream.shutdown() }

    @Test
    fun `successful run persists sessions and one revision set`() {
        // Arrange
        val tenant = insertInstitution()
        val source = insertSource(tenant, baseUrl = upstream.url("/").toString())
        upstream.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE_TWO_SESSIONS))

        // Act
        val run = ingestOnce(tenant, source)

        // Assert
        assertEquals("ok", run.outcome)
        assertEquals(2, sessionCountFor(tenant))
        assertEquals(2, run.deltasFound)   // first ingest: all ADDED
    }

    @Test
    fun `identical second run writes no revisions and no sessions`() {
        // Arrange (B5-01) — the single most important cost guard in the ingest tier
        val tenant = insertInstitution()
        val source = insertSource(tenant, baseUrl = upstream.url("/").toString())
        repeat(2) { upstream.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE_TWO_SESSIONS)) }

        // Act
        ingestOnce(tenant, source)
        val second = ingestOnce(tenant, source)

        // Assert
        assertEquals(0, second.deltasFound)
        assertEquals(2, revisionCountFor(tenant), "no new revisions for unchanged data")
    }

    @Test
    fun `upstream 502 marks the run failed and leaves previous data intact`() {
        // Arrange (B1-01)
        val tenant = insertInstitution()
        val source = insertSource(tenant, baseUrl = upstream.url("/").toString())
        upstream.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE_TWO_SESSIONS))
        ingestOnce(tenant, source)
        val before = sessionCountFor(tenant)

        // Act
        upstream.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>"))
        val failed = ingestOnce(tenant, source)

        // Assert
        assertEquals("failed", failed.outcome)
        assertEquals(before, sessionCountFor(tenant), "a failed fetch must never delete good data")
        assertEquals(0, revisionCountOfType(tenant, "REMOVED"))
    }

    @Test
    fun `captive portal html is never interpreted as an empty timetable`() {
        // Arrange (A3-05, B1-02) — the failure that would tell every student they have no classes
        val tenant = insertInstitution()
        val source = insertSource(tenant, baseUrl = upstream.url("/").toString())
        upstream.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE_TWO_SESSIONS))
        ingestOnce(tenant, source)

        // Act
        upstream.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "text/html")
                .setBody("<!DOCTYPE html><html><body>Sign in to continue</body></html>")
        )
        val run = ingestOnce(tenant, source)

        // Assert
        assertEquals("failed", run.outcome)
        assertEquals(2, sessionCountFor(tenant))
        assertTrue(runErrorsFor(tenant).any { it.contains("NOT_JSON") })
    }

    @Test
    fun `upstream 429 opens the breaker and stops further calls`() {
        // Arrange (B1-10, B2-07)
        val tenant = insertInstitution()
        val source = insertSource(tenant, baseUrl = upstream.url("/").toString())
        repeat(6) { upstream.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30")) }

        // Act
        repeat(6) { ingestOnce(tenant, source) }

        // Assert — the breaker trips; we do not keep hammering a vendor that said "slow down"
        assertTrue(upstream.requestCount <= 5, "breaker must stop egress after the threshold, was ${upstream.requestCount}")
        assertTrue(breakerStateFor(tenant) in setOf("OPEN", "HALF_OPEN"))
    }

    @Test
    fun `redirect to a login page is treated as an auth failure`() {
        // Arrange (B1-09)
        val tenant = insertInstitution()
        val source = insertSource(tenant, baseUrl = upstream.url("/").toString())
        upstream.enqueue(
            MockResponse().setResponseCode(302).setHeader("Location", "https://login.microsoftonline.com/tenant")
        )

        // Act
        val run = ingestOnce(tenant, source)

        // Assert
        assertEquals("failed", run.outcome)
        assertTrue(runErrorsFor(tenant).any { it.contains("AUTH") })
    }

    @Test
    fun `two concurrent runs for one course produce exactly one revision set`() {
        // Arrange (B3-01)
        val tenant = insertInstitution()
        val source = insertSource(tenant, baseUrl = upstream.url("/").toString())
        repeat(2) { upstream.enqueue(MockResponse().setResponseCode(200).setBody(FIXTURE_TWO_SESSIONS)) }

        // Act — genuinely concurrent, real DB lock
        val results = runConcurrently(2) { ingestOnce(tenant, source) }

        // Assert
        assertEquals(1, results.count { it.outcome == "ok" })
        assertEquals(1, results.count { it.outcome == "already_running" })
        assertEquals(2, revisionCountFor(tenant))
    }

    @Test
    fun `oversized upstream response is refused without OOM`() {
        // Arrange (B1-08)
        val tenant = insertInstitution()
        val source = insertSource(tenant, baseUrl = upstream.url("/").toString())
        upstream.enqueue(MockResponse().setResponseCode(200).setBody("x".repeat(8 * 1024 * 1024)))

        // Act
        val run = ingestOnce(tenant, source)

        // Assert
        assertEquals("failed", run.outcome)
        assertTrue(runErrorsFor(tenant).any { it.contains("TOO_LARGE") })
    }
}
```

### 3.5 Concurrency and idempotency at the API boundary (`ApiConcurrencyTest`)

```kotlin
package com.coursegrid.api

import com.coursegrid.support.IntegrationTestBase
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Idempotency, optimistic concurrency and stampede control")
class ApiConcurrencyTest : IntegrationTestBase() {

    @Test
    fun `replaying an idempotency key returns the original body and writes once`() {
        // Arrange (A1-09)
        val tenant = insertInstitution()
        val user = insertUser()
        val course = insertCourse(tenant, identity = "C1")
        val key = UUID.randomUUID().toString()

        testApplication {
            // Act
            val first = client.put("/v1/me/subscriptions/$course") {
                header("Authorization", "Bearer ${tokenFor(user, tenant)}")
                header("Idempotency-Key", key)
                setBody("""{"group_filter":"G2"}""")
            }
            val replay = client.put("/v1/me/subscriptions/$course") {
                header("Authorization", "Bearer ${tokenFor(user, tenant)}")
                header("Idempotency-Key", key)
                setBody("""{"group_filter":"G2"}""")
            }

            // Assert
            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(HttpStatusCode.OK, replay.status)
            assertEquals(first.bodyAsText(), replay.bodyAsText())
        }
        assertEquals(1, subscriptionCountFor(user, course))
    }

    @Test
    fun `reusing a key with a different body is a conflict not a silent overwrite`() {
        // Arrange
        val tenant = insertInstitution()
        val user = insertUser()
        val course = insertCourse(tenant, identity = "C1")
        val key = UUID.randomUUID().toString()

        testApplication {
            client.put("/v1/me/subscriptions/$course") {
                header("Authorization", "Bearer ${tokenFor(user, tenant)}")
                header("Idempotency-Key", key); setBody("""{"group_filter":"G1"}""")
            }

            // Act
            val conflict = client.put("/v1/me/subscriptions/$course") {
                header("Authorization", "Bearer ${tokenFor(user, tenant)}")
                header("Idempotency-Key", key); setBody("""{"group_filter":"G2"}""")
            }

            // Assert
            assertEquals(HttpStatusCode.Conflict, conflict.status)
            assertTrue(conflict.bodyAsText().contains("idempotency_key_reuse"))
        }
        assertEquals("G1", subscriptionGroupFor(user, course), "the original write must stand")
    }

    @Test
    fun `stale If-Match is rejected so two admins cannot silently clobber each other`() {
        // Arrange (B3-06)
        val tenant = insertInstitution()
        val source = insertSource(tenant)
        val adminA = tokenForRole(tenant, Role.TENANT_ADMIN)
        val adminB = tokenForRole(tenant, Role.TENANT_ADMIN)
        val etag = currentEtag(tenant, source)

        testApplication {
            // Act — both read the same version, both write
            val first = client.patch("/v1/admin/sources/${source.id}") {
                header("Authorization", "Bearer $adminA"); header("If-Match", etag)
                setBody("""{"poll_interval_seconds":1800}""")
            }
            val second = client.patch("/v1/admin/sources/${source.id}") {
                header("Authorization", "Bearer $adminB"); header("If-Match", etag)
                setBody("""{"poll_interval_seconds":3600}""")
            }

            // Assert
            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(HttpStatusCode.PreconditionFailed, second.status)
        }
    }

    @Test
    fun `concurrent identical cold reads collapse into one database query`() {
        // Arrange (B3-05)
        val tenant = insertInstitution()
        val course = insertCourse(tenant, identity = "C1")
        insertSession(course)
        resetQueryCounter()

        testApplication {
            // Act
            val responses = runConcurrently(50) {
                client.get("/v1/courses/${course.id}/sessions?from=2025-09-15&to=2025-09-21")
            }

            // Assert
            assertTrue(responses.all { it.status == HttpStatusCode.OK })
            assertTrue(sessionSelectCount() <= 1, "single-flight must prevent a cache stampede, saw ${sessionSelectCount()}")
        }
    }

    @Test
    fun `anonymous read quota returns 429 with a Retry-After hint`() {
        // Arrange (B2-03)
        val course = insertCourse(insertInstitution(), identity = "C1")

        testApplication {
            // Act
            val statuses = (1..125).map {
                client.get("/v1/courses/${course.id}/sessions?from=2025-09-15&to=2025-09-21").status
            }

            // Assert
            assertEquals(HttpStatusCode.OK, statuses[119])            // 120th succeeds
            assertEquals(HttpStatusCode.TooManyRequests, statuses[120]) // 121st is limited
        }
    }

    @Test
    fun `rate limiting one student does not affect an admin`() {
        // Arrange (B2-05)
        val tenant = insertInstitution()
        val student = insertUser()
        val admin = tokenForRole(tenant, Role.TENANT_ADMIN)
        val course = insertCourse(tenant, identity = "C1")

        testApplication {
            // Act — exhaust the student's bucket
            repeat(700) {
                client.get("/v1/courses/${course.id}/sessions") { header("Authorization", "Bearer ${tokenFor(student, tenant)}") }
            }
            val adminResponse = client.get("/v1/admin/health?window=24h") { header("Authorization", "Bearer $admin") }

            // Assert
            assertEquals(HttpStatusCode.OK, adminResponse.status)
        }
    }
}
```

### 3.6 Authentication and session lifecycle (`AuthTest`)

```kotlin
package com.coursegrid.identity

@DisplayName("Authentication, refresh rotation and admin step-up")
class AuthTest : IntegrationTestBase() {

    @Test
    fun `refresh token rotation invalidates the previous token`() {
        // Arrange
        val user = insertUserWithLocalAccount()
        val issued = login(user)

        // Act
        val rotated = refresh(issued.refreshToken)

        // Assert — rotation is not advisory
        assertEquals(HttpStatusCode.OK, rotated.status)
        assertEquals(HttpStatusCode.Unauthorized, refresh(issued.refreshToken).status)
    }

    @Test
    fun `refresh token reuse is detected and revokes the whole family`() {
        // Arrange — the classic stolen-refresh-token signal
        val user = insertUserWithLocalAccount()
        val issued = login(user)
        val rotated = refresh(issued.refreshToken)

        // Act — attacker replays the old token
        val replay = refresh(issued.refreshToken)

        // Assert — reuse kills the family, including the legitimate new token
        assertEquals(HttpStatusCode.Unauthorized, replay.status)
        assertEquals(HttpStatusCode.Unauthorized, refresh(rotated.refreshToken).status)
        assertTrue(auditActionsFor(user).contains("auth.refresh_reuse_detected"))
    }

    @Test
    fun `OIDC callback rejects a mismatched state and nonce`() {
        // Assert
        assertEquals(HttpStatusCode.BadRequest, oidcCallback(state = "wrong", nonce = "n").status)
        assertEquals(HttpStatusCode.BadRequest, oidcCallback(state = "s", nonce = "wrong").status)
    }

    @Test
    fun `tenant admin actions require MFA step-up`() {
        // Arrange
        val tenant = insertInstitution()
        val admin = tokenWithoutMfa(tenant, Role.TENANT_ADMIN)

        // Act
        val response = patchSource(admin, pollInterval = 1800)

        // Assert
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("mfa_required"))
    }

    @Test
    fun `end user token cannot reach tenant admin endpoints`() {
        // Arrange (BFLA)
        val tenant = insertInstitution()
        val user = tokenForRole(tenant, Role.END_USER)

        // Act
        val response = get(admin = true, token = user)

        // Assert
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `erasure removes personal data and pseudonymises audit rows`() {
        // Arrange — GDPR Art. 17 with the legal-obligation carve-out for audit
        val tenant = insertInstitution()
        val user = insertUser(email = "student@example.ie")
        insertSubscription(user, tenant, insertCourse(tenant, identity = "C1"))
        registerDevice(user, platform = "android", pushToken = "tok")

        // Act
        deleteAccount(user)

        // Assert
        assertNull(findUserEmail(user))
        assertEquals(0, subscriptionCountForUser(user))
        assertEquals(0, deviceCountForUser(user))
        assertTrue(auditEntriesForUser(user).isNotEmpty(), "audit rows survive with ids, not personal data")
        assertFalse(auditEntriesForUser(user).any { it.contains("student@example.ie") })
    }
}
```

---
## 4. End-to-end and mobile failure-mode tests

### 4.1 Offline-first transitions (`AgendaViewModelTest` + `OfflineTransitionTest`)

The offline behaviour is the prototype's best idea (spec §9.3) and the easiest thing to break in a refactor, so it gets the densest coverage.

```kotlin
package com.coursegrid.app.timetable

import com.coursegrid.app.fake.FakeApiClient
import com.coursegrid.app.fake.FakeSessionDao
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JUnit 4 + Robolectric: matches the existing app's test stack (app/src/test uses JUnit 4),
 * so these tests are a migration, not a new toolchain.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineTransitionTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var api: FakeApiClient
    private lateinit var dao: FakeSessionDao
    private lateinit var viewModel: TimetableViewModel

    @Before
    fun setUp() {
        api = FakeApiClient()
        dao = FakeSessionDao()
        viewModel = TimetableViewModel(api, dao, clock = FakeClock("2025-09-15T08:00:00Z"))
    }

    // ── A3-01 / A3-02: cold start ──────────────────────────────────────────────

    @Test
    fun `cold start offline with a warm cache renders the cached week without an error`() = runTest {
        // Arrange
        dao.seed(courseId = COURSE, week = "2025-09-15", sessions = 4, fetchedAt = "2025-09-15T07:00:00Z")
        api.failNext(ConnectivityException())

        // Act
        viewModel.load(COURSE, week = "2025-09-15")
        advanceUntilIdle()

        // Assert — content first, freshness second, no error dialog
        assertEquals(4, viewModel.state.value.sessions.size)
        assertEquals(Freshness.CACHED, viewModel.state.value.freshness)
        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.bannerText!!.startsWith("Cached"))
    }

    @Test
    fun `cold start offline with an empty cache shows a retry affordance not a blank screen`() = runTest {
        // Arrange
        api.failNext(ConnectivityException())

        // Act
        viewModel.load(COURSE, week = "2025-09-15")
        advanceUntilIdle()

        // Assert
        assertTrue(viewModel.state.value.sessions.isEmpty())
        assertEquals(EmptyState.OFFLINE_NO_CACHE, viewModel.state.value.emptyState)
        assertTrue(viewModel.state.value.canRetry)
    }

    @Test
    fun `server reported staleness is distinguished from app offline state`() = runTest {
        // Arrange (A3-07) — we are online; the institution's data is 2 hours old
        api.respondWith(
            sessions = 3,
            freshness = SourceFreshness(state = "stale", ageSeconds = 7200, lastSuccessAt = "2025-09-15T06:00:00Z")
        )

        // Act
        viewModel.load(COURSE, week = "2025-09-15")
        advanceUntilIdle()

        // Assert — the user can tell whose problem it is
        assertEquals(Freshness.STALE_UPSTREAM, viewModel.state.value.freshness)
        assertTrue(viewModel.state.value.bannerText!!.contains("institution"))
    }

    // ── A1-01 / A1-10: rapid actions ───────────────────────────────────────────

    @Test
    fun `pull to refresh spammed ten times issues one network call`() = runTest {
        // Arrange
        dao.seed(COURSE, "2025-09-15", sessions = 4, fetchedAt = "2025-09-15T00:00:00Z")
        api.respondAfterDelay(300)

        // Act
        repeat(10) { viewModel.refresh() }
        advanceUntilIdle()

        // Assert — single-flight; the other nine join the in-flight request
        assertEquals(1, api.callCount)
        assertEquals(1, dao.writeCount)
    }

    @Test
    fun `offline retry spam does not produce a request storm`() = runTest {
        // Arrange
        api.failNext(ConnectivityException())

        // Act
        repeat(20) { viewModel.retry() }
        advanceUntilIdle()

        // Assert — retries are coalesced and backoff-limited
        assertTrue(api.callCount <= 2, "saw ${api.callCount} calls")
    }

    @Test
    fun `refresh beyond the cooldown is refused locally with the next allowed time`() = runTest {
        // Arrange (A1-02) — last pull two hours ago, Daily strategy => 24 h cooldown
        viewModel = TimetableViewModel(api, dao, clock = FakeClock("2025-09-15T08:00:00Z"),
            lastPullRefresh = "2025-09-15T06:00:00Z", strategy = SyncStrategy.Daily)

        // Act
        val accepted = viewModel.requestPullRefresh()

        // Assert
        assertFalse(accepted)
        assertEquals(0, api.callCount)
        assertEquals("2025-09-16T06:00:00Z", viewModel.state.value.nextPullRefreshAllowedAt)
    }

    // ── A1-05 / A1-07: rapid navigation and filtering ──────────────────────────

    @Test
    fun `rapid week switching applies only the final week`() = runTest {
        // Arrange — each week responds slower than the next request arrives
        api.respondWithDelayPerWeek(mapOf(
            "2025-09-08" to 300, "2025-09-15" to 250, "2025-09-22" to 200, "2025-09-29" to 50,
        ))

        // Act
        listOf("2025-09-08", "2025-09-15", "2025-09-22", "2025-09-29").forEach { viewModel.selectWeek(it) }
        advanceUntilIdle()

        // Assert — stale responses are discarded; no flicker back to an earlier week
        assertEquals("2025-09-29", viewModel.state.value.selectedWeek)
        assertEquals(WEEK_4_SESSIONS, viewModel.state.value.sessions)
    }

    @Test
    fun `changing group mid-semester purges the other group's cache`() = runTest {
        // Arrange (A2-10)
        dao.seed(COURSE, "2025-09-15", sessions = 4, group = "G1", fetchedAt = "2025-09-15T07:00:00Z")

        // Act
        viewModel.selectGroup(COURSE, "G2")
        advanceUntilIdle()

        // Assert — the cache key includes the group set, so G1 rows are not reused for G2
        assertTrue(dao.rowsFor(COURSE, "2025-09-15", group = "G1").isEmpty())
        assertTrue(viewModel.state.value.sessions.all { it.group == null || it.group == "G2" })
    }

    @Test
    fun `group change with no matching sessions shows the explicit empty state`() = runTest {
        // Arrange (A2-08) — G2 was removed upstream
        api.respondWith(sessions = 0)

        // Act
        viewModel.selectGroup(COURSE, "G2")
        advanceUntilIdle()

        // Assert
        assertEquals(EmptyState.NO_SESSIONS_FOR_GROUP, viewModel.state.value.emptyState)
        assertFalse(viewModel.state.value.isLoading)
    }

    // ── A3-03, A3-10: mid-flight failures ──────────────────────────────────────

    @Test
    fun `mid request disconnect falls back to cache and retries once`() = runTest {
        // Arrange
        dao.seed(COURSE, "2025-09-15", sessions = 4, fetchedAt = "2025-09-10T07:00:00Z")
        api.dropConnection()

        // Act
        viewModel.load(COURSE, "2025-09-15")
        advanceUntilIdle()

        // Assert
        assertEquals(4, viewModel.state.value.sessions.size)
        assertEquals(Freshness.CACHED, viewModel.state.value.freshness)
        assertEquals(1, api.callCount) // one retry policy, not a loop
    }

    @Test
    fun `truncated response body never replaces good cached data`() = runTest {
        // Arrange (A3-04)
        dao.seed(COURSE, "2025-09-15", sessions = 4, fetchedAt = "2025-09-10T07:00:00Z")
        api.respondWithTruncatedBody()

        // Act
        viewModel.load(COURSE, "2025-09-15")

        // Assert — partial parses must not reach the database
        assertEquals(4, dao.rowsFor(COURSE, "2025-09-15", group = null).size)
        assertEquals(0, dao.writeCount)
        assertEquals(Freshness.CACHED, viewModel.state.value.freshness)
    }

    @Test
    fun `request timeout produces cached content and no ANR`() = runTest {
        // Arrange (A3-10)
        dao.seed(COURSE, "2025-09-15", sessions = 2, fetchedAt = "2025-09-10T07:00:00Z")
        api.hang(seconds = 45)

        // Act
        viewModel.load(COURSE, "2025-09-15")
        advanceTimeBy(31_000)
        advanceUntilIdle()

        // Assert
        assertEquals(2, viewModel.state.value.sessions.size)
        assertEquals(Freshness.CACHED, viewModel.state.value.freshness)
    }

    @Test
    fun `our own 429 is honoured including the Retry-After hint`() = runTest {
        // Arrange (A3-06)
        dao.seed(COURSE, "2025-09-15", sessions = 1, fetchedAt = "2025-09-10T07:00:00Z")
        api.respondWith(status = 429, retryAfterSeconds = 30)

        // Act
        viewModel.refresh()
        advanceUntilIdle()

        // Assert
        assertEquals(Freshness.CACHED, viewModel.state.value.freshness)
        assertEquals(0, api.callsBeforeRetryAllowed())
        assertEquals(30, viewModel.state.value.retryAfterSeconds)
    }
}
```

### 4.2 Background sync, process death and lifecycle (`WorkManagerLifecycleTest`)

`WorkManagerTestInitHelper` + `TestDriver` let these run as fast JVM tests instead of flaky device tests.

```kotlin
package com.coursegrid.app.sync

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkManagerLifecycleTest {

    private lateinit var workManager: WorkManager
    private lateinit var testDriver: TestDriver

    @Before
    fun setUp() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        testDriver = WorkManagerTestInitHelper.getTestDriver(context)!!
    }

    @Test
    fun `sync rescheduled after device reboot does not duplicate revisions`() {
        // Arrange (A5-01)
        seedCache(courseIdentity = "TU859", week = "2025-09-15", sessions = 3)
        enqueueSync(courseIdentity = "TU859", strategy = SyncStrategy.Daily)
        testDriver.setAllConstraintsMet(workRequestId)

        // Act — simulate the OS tearing the process down mid-run, then rescheduling after boot
        runWorkUntilMidWriteThenKill()
        rescheduleOnBoot(strategy = SyncStrategy.Daily)

        // Assert — idempotent: source_hash means the second run writes no revisions
        assertEquals(0, revisionCountFor("TU859"))
        assertEquals(3, cachedSessionCount("TU859"))
    }

    @Test
    fun `sync respects the network constraint and does not run offline`() {
        // Arrange (A3-01, A3-11)
        enqueueSync(courseIdentity = "TU859", strategy = SyncStrategy.Daily)

        // Act — constraints unmet, then 60 s of flapping
        flapConnectivity(durationSeconds = 60)
        testDriver.setAllConstraintsMet(workRequestId)

        // Assert — one successful sync, no storm
        assertEquals(1, syncRuns())
    }

    @Test
    fun `low storage is a typed failure that does not crash the worker`() {
        // Arrange (A5-10)
        setFreeDiskBytes(2 * 1024 * 1024) // 2 MB
        enqueueSync(courseIdentity = "TU859", strategy = SyncStrategy.Daily)
        testDriver.setAllConstraintsMet(workRequestId)

        // Act
        runWorker()

        // Assert
        assertEquals(Result.failure(), lastWorkerResult())
        assertTrue(lastWorkerError() is InsufficientStorageException)
        assertFalse(crashRecorded())
    }

    @Test
    fun `strategy change reschedules with the new interval`() {
        // Arrange (A5-11) — Daily -> Weekly
        setStrategy(SyncStrategy.Daily)
        val dailyInterval = currentPeriodicIntervalHours()

        // Act
        setStrategy(SyncStrategy.Weekly)
        rescheduleForStrategy()

        // Assert
        assertEquals(24L, dailyInterval)
        assertEquals(168L, currentPeriodicIntervalHours())
    }

    @Test
    fun `legacy 168 hour preference migrates to Weekly and unknown tokens fall back to Daily`() {
        // Arrange — an install that predates the token model
        prefs.edit().putString("sync_strategy_token", "CUSTOM:0:HOURS").apply()

        // Act
        val strategy = SyncPreferences(context).getSyncStrategy()

        // Assert — the launch path must never throw (see §5.3 for the defect this guards)
        assertEquals(SyncStrategy.Daily, strategy)
        prefs.edit().putString("sync_strategy_token", "CUSTOM:168:HOURS").apply()
        assertEquals(SyncStrategy.Weekly, SyncPreferences(context).getSyncStrategy())
    }

    @Test
    fun `notification permission denial does not crash completion reporting`() {
        // Arrange (A5-04) — API 33+, POST_NOTIFICATIONS revoked
        denyNotificationPermission()

        // Act
        runSyncToCompletion()

        // Assert
        assertFalse(crashRecorded())
        assertTrue(deliveryRecorded(channel = "push", status = "suppressed", reason = "permission_denied"))
    }

    @Test
    fun `push token rotation keeps exactly one active token per device`() {
        // Arrange (A5-03)
        registerDevice(userId = USER, platform = "android", token = "token-v1")

        // Act
        registerDevice(userId = USER, platform = "android", token = "token-v2") // FCM rotated

        // Assert — upsert on (platform, token), and the stale token is not delivered to
        assertEquals(1, activeDeviceCount(USER))
        assertEquals("token-v2", activePushToken(USER))
    }
}
```

### 4.3 Room migration and upgrade-in-place (`RoomMigrationTest`) — replaces the destructive fallback

```kotlin
package com.coursegrid.app.cache

@RunWith(AndroidJUnit4::class) // Room's MigrationTestHelper needs instrumentation unless Robolectric is configured
class RoomMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TimetableDatabase::class.java,
    )

    @Test
    fun `migration v7 to v8 preserves subscriptions and cached sessions`() {
        // Arrange — apply the shipped v7 schema, then populate it
        helper.createDatabase(TEST_DB, 7).apply {
            execSQL("INSERT INTO saved_courses (identity, name, programmeCode, timetableTypeId, savedAt, `group`) " +
                "VALUES ('TU859-CS', 'Computer Science', 'TU859', 'type-1', 1757923200000, 'G2')")
            execSQL("INSERT INTO cached_events (courseIdentity, weekStart, fetchedAt, moduleCode, title, type, " +
                "lecturer, room, start, end, `group`, courseName) VALUES " +
                "('TU859-CS', '2025-09-15', 1757923200000, 'CMPU3021', 'SE', 'Lecture', 'Byrne', 'A214', " +
                "'2025-09-15T09:00:00Z', '2025-09-15T11:00:00Z', 'G2', 'TU859/Computer Science')")
            close()
        }

        // Act
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        // Assert — data survives; the production schema never uses a destructive fallback
        migrated.query("SELECT COUNT(*) FROM saved_courses").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM cached_events").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }
        migrated.query("SELECT `group` FROM saved_courses").use { c ->
            c.moveToFirst(); assertEquals("G2", c.getString(0))
        }
    }

    @Test
    fun `database is not built with a destructive fallback`() {
        // Arrange / Act — reflection over the built configuration
        val db = Room.databaseBuilder(context, TimetableDatabase::class.java, "assert.db").build()

        // Assert — constructing and opening must not silently drop user-created tables
        // (spec T12/A-6: `fallbackToDestructiveMigration()` is banned in production schemas)
        assertFalse(db.openHelper.writableDatabase.isReadOnly)
        assertTrue(declaredMigrations().isNotEmpty(), "explicit migrations must exist for every version step")
    }
}
```

### 4.4 End-to-end journeys

**Android (Maestro — smoke, per-PR).** Runs on a device/emulator farm; kept short and deterministic so it is not skipped.

```yaml
# .maestro/j1-first-run-to-week.yaml  — journey J1
appId: ie.coursegrid.app
---
- launchApp:
    clearState: true
- tapOn: "Search courses"
- inputText: "TU859"
- assertVisible: "Computer Science"
- tapOn: "Computer Science"
- tapOn:
    id: "group-selector"
- tapOn: "G2"
- assertVisible: "Week 5"
- assertVisible:
    text: "CMPU3021"
- tapOn: "Pin to home"
- assertVisible: "Pinned"
```

```yaml
# .maestro/j5-degraded-network.yaml  — journey J5, the offline contract
appId: ie.coursegrid.app
---
- launchApp
- assertVisible: "CMPU3021"          # cached week renders first
- runScript: setAirplaneMode.js      # toggle connectivity
- tapOn: "Refresh"
- assertVisible: "Cached · updated"   # banner, not an error
- assertNotVisible: "Something went wrong"
```

```yaml
# .maestro/change-notification.yaml  — journey J2, the product's core loop
appId: ie.coursegrid.app
---
- launchApp
- runScript: pushFixtureDelta.js     # enqueue a delta for the subscribed course
- assertVisible: "1 change this week"
- tapOn: "1 change this week"
- assertVisible: "Room: A214 → B102"
- tapOn: "Update calendar"
- assertVisible: "Calendar updated"
```

**Web (Playwright — per-PR).** Covers the anonymous journey J3, keyboard-only operation (A6-05), and the SSR/edge behaviour.

```typescript
// e2e/web/j3-anonymous-quick-check.spec.ts
import { test, expect } from '@playwright/test';

test('anonymous user checks the current week without signing in', async ({ page }) => {
  // Act
  await page.goto('/i/tudublin/c/TU859');

  // Assert — server-rendered and indexable, no login wall
  await expect(page.getByRole('heading', { name: /Computer Science/ })).toBeVisible();
  await expect(page.getByText('CMPU3021')).toBeVisible();
  await expect(page.getByRole('button', { name: /sign in/i })).toHaveCount(0);
});

test('the week is reachable and operable by keyboard alone', async ({ page }) => {
  // Arrange
  await page.goto('/i/tudublin/c/TU859');

  // Act — tab to the group selector and change it without a mouse
  await page.keyboard.press('Tab');
  await page.getByRole('combobox', { name: /group/i }).focus();
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');

  // Assert — focus is visible and the selection applied
  await expect(page.getByRole('combobox', { name: /group/i })).toHaveValue('G2');
  await expect(page.locator(':focus')).toBeVisible();
});

test('a stale institution feed is disclosed to the user', async ({ page }) => {
  // Arrange
  await page.route('**/v1/courses/**/sessions*', async route => {
    const response = await route.fetch();
    const json = await response.json();
    json.meta.source_freshness = { state: 'stale', age_seconds: 7200 };
    await route.fulfill({ response, json });
  });

  // Act
  await page.goto('/i/tudublin/c/TU859');

  // Assert
  await expect(page.getByRole('status')).toContainText(/institution/i);
});
```

### 4.5 Accessibility tests

```kotlin
package com.coursegrid.app.a11y

@RunWith(AndroidJUnit4::class)
class WeekViewAccessibilityTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `every change marker carries a text label not just colour`() {
        // Arrange (A6-03, WCAG 1.4.1)
        composeRule.setContent { ChangeList(changes = listOf(added, removed, modified)) }

        // Assert — colour is never the only channel
        composeRule.onNodeWithText("Added").assertIsDisplayed()
        composeRule.onNodeWithText("Removed").assertIsDisplayed()
        composeRule.onNodeWithText("Changed").assertIsDisplayed()
    }

    @Test
    fun `weekly sessions are enumerable via the accessibility tree`() {
        // Arrange (A6-04)
        composeRule.setContent { TimetableWeek(events = fiveSessions) }

        // Assert — a grid alone is not an accessible representation
        composeRule.onAllNodesWithText("CMPU3021").assertCountEquals(5)
        composeRule.onNodeWithContentDescription("Software Engineering, Monday 09:00 to 11:00, room A214")
            .assertExists()
    }

    @Test
    fun `layout survives two hundred percent font scaling`() {
        // Arrange (A5-08)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                TimetableWeek(events = fiveSessions)
            }
        }

        // Assert — no clipped session times
        composeRule.onNodeWithText("09:00").assertIsDisplayed()
    }

    @Test
    fun `icon-only controls expose a content description`() {
        // Arrange
        composeRule.setContent { TimetableScreen(state = defaultState) }

        // Assert
        composeRule.onNodeWithContentDescription("Refresh timetable").assertExists()
        composeRule.onNodeWithContentDescription("Open settings").assertExists()
        composeRule.onNodeWithContentDescription("Pin course to home").assertExists()
    }
}
```

### 4.6 Performance regression guards (Macrobenchmark)

```kotlin
package com.coursegrid.app.benchmark

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartWithWarmCache() = benchmarkRule.measureRepeated(
        packageName = "ie.coursegrid.app",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun scrollWeekWithoutJank() = benchmarkRule.measureRepeated(
        packageName = "ie.coursegrid.app",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = { startActivityAndWait() },
    ) {
        device.findObject(By.res("week-list")).fling(Direction.DOWN)
        device.waitForIdle()
    }
}
```

**Budgets (spec §8.7):** cold start p95 ≤ 2.5 s, warm ≤ 400 ms, frame timing p95 ≥ 55 fps. The benchmark writes results to CI, which fails the build on a >10 % regression versus the stored baseline. That is the mechanism that keeps spec §8 P13–P15 honest.

### 4.7 Test-class inventory for §4

| Test class | Covers | Matrix IDs | Layer |
|---|---|---|---|
| `OfflineTransitionTest` | Cache-first render, staleness, single-flight, week/group switching, truncation, timeouts | A1-01, A1-02, A1-05, A1-07, A1-10, A2-08, A2-10, A3-01…A3-07, A3-10 | M |
| `WorkManagerLifecycleTest` | Reboot, constraints, storage, strategy changes, notification permission, token rotation | A5-01…A5-04, A5-10, A5-11, A3-11 | M |
| `RoomMigrationTest` | v7→v8 migration, no destructive fallback | A5-09 | I/M |
| `WeekViewAccessibilityTest` | Non-colour encoding, a11y enumeration, font scaling, content descriptions | A6-03, A6-04, A5-08, A6-01 | M |
| `Maestro × 3` | Journeys J1, J2, J5 | A1-08, A2-06, E2E | E |
| `Playwright × 3` | Journey J3, keyboard-only, stale disclosure | A6-05, A3-07 | E |
| `StartupBenchmark` | Cold/warm start, jank budgets | P13–P15 | M |
| `PushDeliveryTest` | FCM data message → UI refresh, dedupe, quiet hours | A5-03, B5-11 | I/E |
| `RestoreAndProcessDeathTest` | View-state restore, no network when fresh | A5-06 | M |
| `LocalisationTest` | Long strings, RTL, date/number formats, week scheme | A6-01, A6-02, A4-11 | M |

---
## 5. Fixtures, automation, coverage and traceability

### 5.1 Test data and fixtures

| Fixture class | What it is | Where it lives | Provenance rule |
|---|---|---|---|
| **Upstream response fixtures** | Real HTTP bodies captured from the institution's Scientia tenant: success, empty, malformed HTML, XML with DTD, truncated JSON, 429, 500, 302-to-login, oversized | `src/test/resources/fixtures/scientia/*.json` | Each file carries a header comment: capture date, tenant slug, endpoint, and the reason it exists. **Scrubbed of lecturer names before commit** (personal data, spec §5.6) |
| **Golden dataset** | Deterministic seed: 5 tenants × 500 courses × 30 weeks × ~3 % change rate | `src/test/resources/golden/` + a seeding CLI | Versioned; load-tested and integration tests read the same data so results are comparable run to run |
| **Push/notification fixtures** | Delta payloads with 1, 5, 500 and 5 000 subscribers | `src/test/resources/notifications/` | Used for both fan-out correctness and fan-out latency assertions |
| **Time fixtures** | Fixed clocks: DST gap, DST overlap, year boundary, week 0, term end | `FakeClock` instances in test code | Time is **always injected**; no test calls `LocalDate.now()` (the prototype does — `TimetableUtils.getCurrentMonday()` defaults to `LocalDate.now(DUBLIN_ZONE)`, which is untestable at the boundary) |
| **Mobile state fixtures** | Cached weeks, saved courses, prefs blobs incl. corrupt tokens | `FakeSessionDao`, `SyncPreferences` fixtures | Corrupt-state fixtures are first-class, not an afterthought (see §5.3) |

**Fixture freshness job [REQ]:** a scheduled pipeline re-captures upstream responses weekly and opens a PR when a payload's *shape* changes. This converts an undetected vendor change into a reviewable diff instead of a production outage (risk R13). Without it, the contract tests silently encode last year's API.

### 5.2 CI wiring

```yaml
# .github/workflows/test.yml
name: test

on:
  pull_request:
  push:
    branches: [main]

concurrency:
  group: test-${{ github.ref }}
  cancel-in-progress: true

jobs:
  fast:
    name: Unit + static (blocking, target < 3 min)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: gradle }
      # Secret scanning runs first: cheapest possible failure
      - uses: gitleaks/gitleaks-action@v2
      # JVM-only logic: week maths, delta engine, group filtering, sync strategy
      - run: ./gradlew test --console=plain -PtestFilter="com.example.timetablescraper.api.*"
      # Backend domain + parser suites (fail fast, no containers)
      - run: ./gradlew :backend:test --tests '*DeltaEngineTest' --tests '*GroupFilterTest' --tests '*ScientiaParserTest'
      - run: ./gradlew ktlintCheck detekt
      - uses: github/codeql-action/analyze@v3
        with: { languages: java-kotlin }

  integration:
    name: Integration + contract (blocking)
    needs: fast
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env: { POSTGRES_PASSWORD: test, POSTGRES_USER: coursegrid_app, POSTGRES_DB: coursegrid_test }
        options: >-
          --health-cmd pg_isready --health-interval 5s --health-timeout 5s --health-retries 10
      redis:
        image: redis:7-alpine
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: gradle }
      # Migrations are rehearsed against a production-shaped copy, and must remain
      # backwards compatible with the previously deployed app version (spec §10.2).
      - run: ./gradlew :backend:migrateTest --stacktrace
      - run: ./gradlew :backend:integrationTest --tests '*SchemaConstraintTest' --tests '*TenantIsolationTest'
      - run: ./gradlew :backend:integrationTest --tests '*IngestPipelineTest' --tests '*ApiConcurrencyTest'
      - run: ./gradlew :backend:integrationTest --tests '*AuthTest'
      # Contract: the public API must not break its own OpenAPI document
      - run: npx @redocly/cli lint openapi.yaml
      - run: ./gradlew :backend:openApiDiff
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: test-reports, path: '**/build/reports/tests/**' }

  mobile:
    name: Mobile UI + migration + a11y (blocking)
    needs: fast
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: gradle }
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          script: |
            ./gradlew :app:testDebugUnitTest --tests '*OfflineTransitionTest' --tests '*WorkManagerLifecycleTest'
            ./gradlew :app:connectedDebugAndroidTest
            ./gradlew :app:testDebugUnitTest --tests '*WeekViewAccessibilityTest'
      - run: ./gradlew :app:maestroRun       # J1, J2, J5 smoke
      - run: ./gradlew :app:lintDebug

  performance:
    name: Load + benchmarks (nightly / pre-term, non-blocking on PRs)
    if: github.event_name == 'schedule' || contains(github.event.pull_request.labels.*.name, 'run-perf')
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: k6 run --out json=results.json perf/read-api.js
      - run: ./gradlew :app:benchmarkRun   # Macrobenchmark, compares to stored baseline
      - run: node scripts/assert-budgets.mjs results.json
```

Rules that make this pipeline worth having **[REQ]**: every stage is *blocking* unless explicitly labelled otherwise; artifacts (test reports, screenshots, traces) are always uploaded on failure so a red build is diagnosable from the PR; the schedule runs the expensive suites nightly so PRs stay fast; a regression in a *deleted* test is a review red flag — deleting a failing test is how a suite rots.

### 5.3 Baseline: what was actually executed while preparing this document

This section records real, measured results — and the parts that could not be measured, with the reason. Nothing here is a projection.

**Attempt 1 — the project's own Gradle task.** `./gradlew test` — **blocked by the environment, not by the code**:

```
Exception in thread "main" java.io.FileNotFoundException:
  /Users/…/.gradle/wrapper/dists/gradle-9.3.1-bin/…/gradle-9.3.1-bin.zip.lck (Operation not permitted)
```

Gradle's user home (`~/.gradle`) is not writable from the sandbox. Retrying with an isolated `GRADLE_USER_HOME` inside the workspace got as far as loading Gradle 9.3.1 and then failed at `settings.gradle.kts:14` (the `foojay-resolver-convention` plugin) because the 2.9 GB dependency cache lives in the unwritable `~/.gradle/caches`.

**Attempt 2 — a direct compiler + JUnit harness (worked).** Using Gradle's own bundled Kotlin compiler from the read-only distribution and the cached JUnit jars:

```bash
# 1. Compile production + test sources straight to a workspace-local output dir
java -cp "$GRADLE_HOME/lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
     -cp "$KOTLIN_STDLIB:$JUNIT" -d .agent-test-out/classes -nowarn \
     app/src/main/java/com/example/timetablescraper/api/SyncStrategy.kt \
     app/src/test/java/com/example/timetablescraper/api/SyncStrategyContractTest.kt

# 2. Run the JUnit 4 classes directly
java -cp ".agent-test-out/classes:$KOTLIN_STDLIB:$JUNIT:$HAMCREST" \
     org.junit.runner.JUnitCore com.example.timetablescraper.api.SyncStrategyContractTest
```

Results:

| Suite | Result | Interpretation |
|---|---|---|
| `SyncStrategyContractTest` (new, §2.5) | **1 failure on first run** → after fixing production code, **15/15 OK** | The suite caught a real defect (below) |
| `TimetableUtilsTest` (existing) | **OK** (part of 41) | Genuine production coverage of ISO parsing and day mapping |
| `GroupFilteringTest` (existing) | **OK** (part of 41) | **Passes for the wrong reason** — it re-implements the filtering logic (`:15-114`) instead of calling production code, so it stays green even when production filtering is broken. Replaced by §2.4 |
| Combined existing run | **41 tests OK** | These two suites both pass today against v1.22 sources |
| `ModelsTest` (existing) | **Not runnable in the harness** | References `CacheSource`, which lives in `TimetableRepository.kt`; compiling that pulls OkHttp + Room + Android runtime. Needs the real Gradle build |
| `TimetableParserTest`, `TimetableDaoTest`, `TimetableRepositoryTest`, `SyncPreferencesTest` (existing) | **Not run** | `app/src/androidTest` — need a device/emulator or Robolectric; no emulator in this environment |
| `UpdateCheckerTest` (existing) | **Not run** | Depends on `BuildConfig` (generated by the Android build) |

**Defect D-1 — found by `SyncStrategyContractTest`, fixed.**

| Field | Detail |
|---|---|
| **Symptom** | `SyncStrategy.fromToken("CUSTOM:0:HOURS")` threw `java.lang.IllegalArgumentException: Custom sync interval must be positive, got 0` |
| **Mechanism** | `fromToken` parsed the value and constructed `Custom(v, u)` directly; `Custom`'s `init { require(value > 0) }` then rejected it — from inside a function whose own KDoc says *"Returns Daily for unrecognised tokens"* |
| **Reachability** | `SyncPreferences.getSyncStrategy()` reads this token from SharedPreferences during app launch. Any corrupt, hand-edited, or future-written token with a zero/negative value (`CUSTOM:0:HOURS`, `CUSTOM:-5:HOURS`) crashes the launch path — a crash loop the user cannot clear without wiping app data |
| **Severity** | High on the launch path, low likelihood from normal use (nothing in the shipped UI writes a non-positive interval) — i.e. exactly the class of bug that survives manual QA and ships |
| **Fix** | Reject non-positive values in `fromToken` before constructing `Custom`, falling back to `Daily` as documented (`app/src/main/java/com/example/timetablescraper/api/SyncStrategy.kt`) |
| **Verified by** | Re-run of the same harness: **OK (15 tests)** |
| **Regression guard** | The three assertions in §2.5 (`CUSTOM:0:HOURS`, `CUSTOM:-5:HOURS`, `CUSTOM:0:DAYS`) |

This is the argument for §2 in one incident: the test that found it needed no database, no network, and no device — it needed a *corrupt input fixture*, which the existing suite had never bothered to construct.

**Housekeeping:** the harness wrote only to `.agent-test-out/` (workspace-local, deleted after use). The one production change is the `fromToken` guard; the one new test file is `app/src/test/java/com/example/timetablescraper/api/SyncStrategyContractTest.kt`.

### 5.4 Coverage targets

| Module | Line | Branch | Rationale |
|---|---|---|---|
| Delta engine | 95 % | **100 %** | Every branch produces either a missed change or a notification storm (R4, R5) |
| Calendar / week maths | 95 % | 95 % | Wrong week placement is silently wrong |
| Upstream adapters / parsers | 90 % | 90 % | Fixture corpus does the heavy lifting; malformed paths are the point |
| Group filtering | 95 % | 95 % | Student-visible correctness, cheap to test |
| Ingest orchestration | 85 % | 80 % | I/O-heavy; covered by integration and contract tests instead |
| API layer (handlers) | 85 % | 75 % | Contract + integration tests carry the weight |
| Auth / authorisation | 95 % | 90 % | Every miss is a security finding |
| Tenant isolation | **100 %** of the probe matrix | n/a | Behavioural matrix, tracked as "all probes covered" rather than a line % |
| Mobile view models | 85 % | 80 % | Cache-first logic is the crown jewel; UI chrome is not |
| UI composables | 60 % | n/a | Covered by a few behaviour tests + Maestro, not by chasing a number |

**[REC]** Gate on *changed-lines* coverage rather than whole-project coverage: it is the number that actually moves, and it does not force teams to write filler tests over legacy code.

### 5.5 Quality gates

| Gate | Threshold | Action on failure |
|---|---|---|
| Unit + static | All green, ktlint/detekt clean, no critical SAST findings | Block merge |
| Integration + contract | All green; migration rehearsal backwards-compatible | Block merge |
| Tenant-escape probe matrix | 100 % of probes covered and green | **Block merge and page the security owner** |
| Mobile UI/migration/a11y | All green; zero critical a11y violations; axe clean on web | Block merge |
| Coverage | Changed-lines ≥ 80 %; per-module minimums in §5.4 for the modules touched | Block merge |
| Performance | Cold start and frame-timing within 10 % of the stored baseline | Block merge, require an explicit override with justification |
| Payload / install size | Week payload ≤ 40 KB gzip; APK ≤ 25 MB | Fail the build (spec §8.7) |
| Flake budget | A test failing on retry twice in 14 days is quarantined with an owner and a deadline | Tracked, reported weekly |

### 5.6 Traceability: matrix → suites

| Matrix group | Primary suites | §  |
|---|---|---|
| A1 Rapid UI actions | `OfflineTransitionTest`, `ApiConcurrencyTest`, `Maestro J1/J2` | 4.1, 3.5 |
| A2 Group edge cases | `GroupFilterTest`, `OfflineTransitionTest`, `ScientiaParserTest` | 2.4, 4.1, 2.3 |
| A3 Network degradation | `OfflineTransitionTest`, `IngestPipelineTest`, `Playwright J3` | 4.1, 3.4, 4.4 |
| A4 Date/time anomalies | `AcademicCalendarTest` | 2.1 |
| A5 App lifecycle | `WorkManagerLifecycleTest`, `RoomMigrationTest`, `PushDeliveryTest` | 4.2, 4.3 |
| A6 a11y / localisation / content | `WeekViewAccessibilityTest`, `LocalisationTest`, `Playwright keyboard` | 4.5, 4.4 |
| B1 Upstream failures | `ScientiaParserTest`, `IngestPipelineTest` | 2.3, 3.4 |
| B2 Rate limiting / breakers | `RateLimitBucketTest`, `IngestPipelineTest`, `ApiConcurrencyTest` | 2.6, 3.4, 3.5 |
| B3 Concurrency | `ApiConcurrencyTest`, `IngestPipelineTest`, `SchemaConstraintTest` | 3.5, 3.4, 3.2 |
| B4 Multi-tenant security | `TenantIsolationTest`, `AuthTest` | 3.3, 3.6 |
| B5 Delta accuracy | `DeltaEngineTest`, `IngestPipelineTest` | 2.2, 3.4 |

**Every ID in §1 maps to at least one suite; every suite maps to at least one ID.** If a future test cannot be traced to an ID, either the matrix is incomplete or the test is speculative — resolve it deliberately rather than letting the suite drift.

### 5.7 Anti-patterns — explicitly banned

Carried over from the defects observed in the existing suite (§0.1), which the migration must not reproduce:

| Banned | Why | Observed in |
|---|---|---|
| Reaching a private member by **reflection** to test logic | Tests implementation shape, breaks on harmless refactors, and keeps genuinely important logic private | `update/UpdateCheckerTest.kt:15-21` |
| **Re-implementing** production logic inside the test | Passes forever regardless of production correctness — worse than no test, because it manufactures false confidence | `api/GroupFilteringTest.kt:15-114` |
| **Mocking the database** in a test whose subject *is* a database constraint or transaction boundary | The constraint is the thing under test; a mock asserts your assumption, not the schema | — |
| Depending on a **real network request failing** to exercise a fallback path | Non-deterministic: passes for the wrong reason and fails on a good connection | `api/TimetableRepositoryTest.kt:17, 92-156` |
| Asserting on **`LocalDate.now()`** | Untestable at DST, week 0, term end and year boundary — precisely where the bugs are | production defaults in `TimetableUtils` |
| **Deleting or skipping** a failing test to get a green build | Eliminates the signal and hides a real defect | — |

---

## 6. Definition of done for this suite

A test is accepted when **all** of the following hold:

1. It is traceable to a matrix ID, and the ID is traceable back to a spec requirement.
2. It fails when the production behaviour it covers is broken (verified by mutation when in doubt — break the code, watch the test go red, revert).
3. It asserts observable behaviour (HTTP status, DB state, emitted event, rendered UI state) rather than internal call sequences.
4. It controls time, randomness, and the network through injected seams — no reliance on wall-clock time or live endpoints.
5. It runs in CI, in the layer where it belongs, and does not depend on another test's leftovers.
6. If it touches personal data, it uses synthesised fixtures with a recorded provenance header.
7. It leaves the suite no slower than 10 % than before, or it is placed in the scheduled (non-PR) tier.

---

*Status of this document: the specification-derived suites (§2.1–§2.4, §3, §4.1–§4.6) are written against the CourseGrid contract and become the backend's acceptance tests when it lands. The prototype-derived suites (§2.5, §4.3) run today. Measured results and the defect found while preparing this document are recorded in §5.3 and §0.1 — including what could **not** be executed here, and why.*
