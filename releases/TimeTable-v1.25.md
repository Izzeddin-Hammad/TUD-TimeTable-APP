# v1.25 — the app now identifies its real version to the university API

**Release date:** 2026-02-09
**APK:** `releases/TimeTable-v1.25-debug.apk`
**Requires:** Android 8.0+ (API 26)

> ⚠️ Still a **TU Dublin–only prototype**. Unsigned debug build; connects to the TU Dublin
> Scientia Publish API with anonymous auth and is not intended for production use.

## How to update

Open the app — it checks `releases/` on launch and offers **Update Now** — or use
**Settings → Check for updates**. Fresh install:
[download the APK](https://github.com/Izzeddin-Hammad/TUD-TimeTable-APP/raw/main/releases/TimeTable-v1.25-debug.apk)
and tap it.

## What changed

One change, plus every fix from v1.24:

- **The request User-Agent now carries the real app version.** It was hardcoded as
  `TimeTableApp/1.1` and stayed there while the app moved to 1.22, so a university reading its own
  access logs would have seen an ancient client. It reads `TimeTableApp/1.25 …` from the build's
  `versionName`, so it cannot go stale again. The `Open Source Student Utility` note and the
  project URL are unchanged — that is how campus network teams identify and whitelist the traffic.
- The value now lives in **one** place instead of being duplicated in two configuration classes,
  which is how it drifted in the first place.
- Its contact link pointed at `…/TimeTable-APP`, a repository that does not exist, so the URL a
  network team would follow was dead. It now points at `…/TUD-TimeTable-APP`.

Everything listed in [`TimeTable-v1.24.md`](TimeTable-v1.24.md) is included — the subgroup-filter
fix, the more accurate change detection, cancelled classes no longer reappearing, no crash loop
from corrupt settings, "Clear Cache & Restart" no longer deleting saved courses, and the removal
of the 2-minute background poll.

## Repository housekeeping

Old APKs (v1.18–v1.22) were removed from `releases/` to keep the directory — and the repository —
tidy. They remain recoverable from git history, and they were never needed by the updater, which
always picks the newest version present.
