# v2.4 — the Custom theme sliders, and a tidier repo

**Release date:** 2026-09-15
**APK:** `releases/TimeTable-v2.4-release.apk` (1.9 MB, signed release build)
**Requires:** Android 8.0+ (API 26)

> ⚠️ **TU Dublin only.** Unofficial student project, not affiliated with TU Dublin.

Installs in place over v2.0–v2.3 (same key).

## Fixed

- **Dragging the Custom theme sliders wrote to preferences on every frame.** Each drag step
  committed the hue *and* the saturation — two preference writes, up to sixty times a second — while
  repainting the whole app. The recolour is still live as you drag; the choice is now written once,
  when you let go. (This was the last thing on the known-issues list from the v2.2 sweep.)

## Repo housekeeping (no app change)

- **The README no longer carries a "What's new" section per release.** Those sections duplicated the
  per-release notes in [`releases/`](../releases/), so the README now has a short **Releases** table
  that links to them — the detail lives in one place.
- **Releases before v2.0 are no longer published.** Their APKs and notes were removed from
  `releases/`. They were debug-signed, and v2.0 changed the signing key, so they could not be
  updated in place anyway — keeping them only risked someone installing one by mistake. The v2.3
  APK remains the one the in-app updater offers.

## Verified

- `gradle testDebugUnitTest` → **246 tests, 0 failures**; `gradle assembleRelease` → BUILD
  SUCCESSFUL, signed.
- On an emulator: the hue slider recolours live as it is dragged, the dragged colour survives a
  restart with the theme still set to Custom, and there are 0 fatal exceptions.
