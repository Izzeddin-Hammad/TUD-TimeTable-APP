# v1.27 — fixes the crash on launch (every build since v1.24 was affected)

**Release date:** 2026-02-09
**APK:** `releases/TimeTable-v1.27-debug.apk`
**Requires:** Android 8.0+ (API 26)

> ⚠️ Still a **TU Dublin–only prototype**. Unsigned debug build.

## If your app will not open

Install this APK directly — **do not use "Check for updates"**, because the installed build crashes
before that screen is reachable:

1. On your phone, open
   [TimeTable-v1.27-debug.apk](https://github.com/Izzeddin-Hammad/TUD-TimeTable-APP/raw/main/releases/TimeTable-v1.27-debug.apk)
   and tap it, **or** run `adb install -r releases/TimeTable-v1.27-debug.apk` from a computer.
2. Android will ask once to allow installing from this source; accept and install.

**Your data is safe.** The failure happened while the database was being *configured*, before the
database file was opened, so nothing was cleared or migrated — your saved courses, pinned course
and settings are untouched. No cache clear is needed.

## The defect

Every build from v1.24 to v1.26 crashed on launch with:

```
java.lang.IllegalArgumentException: Inconsistency detected. A Migration was supplied to
addMigration() that has a start or end version equal to a start version supplied to
fallbackToDestructiveMigrationFrom(). Start version is: 6
    at androidx.room.RoomDatabaseKt.validateMigrationsNotRequired(RoomDatabase.kt:509)
    at androidx.room.RoomDatabase$Builder.build(RoomDatabase.android.kt:1644)
    at ...TimetableDatabase$Companion.getInstance(TimetableDatabase.kt:12)
    at ...TimetableApplication.database_delegate$lambda$0(TimetableApplication.kt:25)
```

**Cause.** v1.24 replaced the blanket `fallbackToDestructiveMigration()` with an explicitly scoped
list — `1, 2, 3, 4, 5, 6` — as a hardening measure. But version 6 is the *start* of the registered
`MIGRATION_6_7`, and Room refuses to build a database when a destructive-fallback start version is
also the start **or end** version of a registered migration. The check runs inside
`Builder.build()`, i.e. while *creating* the database, so the app died before any UI appeared —
on every launch, for every install.

It also explains the related symptom: because the recovery screen's "Clear Cache & Restart"
restarts the app, and the app crashed again immediately, both buttons appeared to do nothing.

**Fix.** The destructive fallback is now `1, 2, 3, 4, 5` — exactly the versions that have no
migration — and the numbers live in one place, `MigrationPlan`, so the invariant can be tested.

## What stops this shipping again

- `MigrationPlan` holds the version plan as data: the registered migrations, the destructive
  fallback versions, and a `conflicts()` check that mirrors Room's rule.
- `MigrationPlanRoomRuleTest` calls **Room's actual validator**
  (`androidx.room.RoomDatabaseKt__RoomDatabaseKt.validateMigrationsNotRequired`, from the Room
  2.7.1 the app ships) and asserts that (a) it accepts the shipped plan, (b) it rejects the v1.24
  plan with the exact message above, and (c) it agrees with our rule for every version 1–8. A
  future Room upgrade that changes the rule turns this red instead of crashing a user's phone.
- `MigrationPlanTest` covers the same rules in fast, readable form.

## Also in this release

- **The crash is now reportable.** v1.26 made the crash details durable (written synchronously,
  recoverable from the on-disk marker) and visible (expanded by default, no longer hidden behind a
  collapsed button) — that is how this defect was identified. A stale, undeletable marker can also
  no longer trap the app on the recovery screen.
- The broken APKs (v1.24, v1.25, v1.26) were removed from `releases/` so they cannot be installed
  by mistake. Their release notes remain for the record.

Verified: `gradle test` → **194 tests, 0 failures** across 17 classes; `gradle assembleDebug` →
BUILD SUCCESSFUL.
