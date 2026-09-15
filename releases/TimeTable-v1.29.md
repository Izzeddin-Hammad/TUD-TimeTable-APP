# v1.29 — pick a theme, with five cozy ones to pick from

**Release date:** 2026-09-15
**APK:** `releases/TimeTable-v1.29-debug.apk`
**Requires:** Android 8.0+ (API 26)

> ⚠️ Still a **TU Dublin–only prototype**. Unsigned debug build.

Settings now has an **Appearance** section: six themes, each shown with a live preview swatch of
its own colours. Picking one re-tints the whole app immediately, and the choice is remembered
across restarts.

## The themes

| Theme | Palette |
|-------|---------|
| **Classic** (default) | Apple's system palette — unchanged, so nothing moves unless you choose it |
| **Cozy Latte** | Warm cream and caramel |
| **Cozy Sage** | Soft sage and warm white |
| **Cozy Dusk** | Muted plum and lavender |
| **Cozy Peach** | Peach and terracotta |
| **Cozy Mist** | Calm blue-grey |

A "cozy" palette is deliberately warm and low-contrast: the background is an off-white (or a warm
deep grey in dark mode) rather than pure white/black, and the accent is muted rather than
saturated. Every theme carries a light *and* a dark variant, so the app keeps following the
system's light/dark setting — only the hue is the student's.

## How it works

- Each theme is a full `IosColors` palette — surfaces, labels, separators, fills and the six accent
  hues — built from a handful of anchors by `Color.kt`'s `cozy()`, with the derived greys computed
  from the ink colour. That keeps every palette internally consistent and keeps hex values in the
  one file `IosDesignLayerTest` allows them in.
- The choice is stored as `app_theme_id` and read once at the root, *above* `TimetableScraperTheme`,
  so changing it recomposes the whole app rather than a single screen.
- An unknown or missing id falls back to Classic, so a stale preference can never break the theme.
- `AppThemeTest` pins the ids (they are persisted keys — renaming one would silently reset every
  student's choice) and checks each theme is a distinct light/dark pair with a unique accent.

## Also in this release

- Settings is now titled "Settings" rather than "Sync & Cache Settings", since it also holds
  Appearance.

## Verified

`gradle testDebugUnitTest` → **236 tests, 0 failures** (5 new). `gradle assembleDebug` → BUILD
SUCCESSFUL (`versionName` 1.29). On an emulator: picking Cozy Latte repainted both Search and
Settings, the preference survived a restart, and with the system in dark mode the dark palette
applied exactly (`#1F1813`).
