# Privacy Policy — TimeTable

**Last updated:** 15 September 2026  
**App version:** 2.1  
**Developer:** Izzeddin Hammad  
**Repository:** [Izzeddin-Hammad/TUD-TimeTable-APP](https://github.com/Izzeddin-Hammad/TUD-TimeTable-APP)

---

## The short version

**Nothing about you leaves your device, except the two requests the app has to make to work.**

There is no telemetry, no analytics SDK, no advertising, and no tracking of any kind. No account, no
sign-in, no device identifier, no crash reporting back to the developer.

What the app *does* keep on your device is your own timetable information — and you can erase all of
it from inside the app, in one tap. See [Erasing everything](#erasing-everything).

---

## What is sent over the network

| Destination | What is sent | When |
|---|---|---|
| `scientia-eu-v4-api-d4-01.azurewebsites.net` (TU Dublin's timetable API) | The course you searched for, and the course/cohort identity of the timetable you opened. No account, no cookie, no device or user identifier (`Authorization: Anonymous`) | When you search, or when a timetable is loaded or refreshed |
| `api.github.com` (GitHub) | Nothing but the request itself. There is no identifying payload — no identifier, no analytics | Once per launch *if* "Check for updates automatically" is on (it defaults to on and can be turned off in Settings → Privacy), and whenever you tap "Check for updates" |
| GitHub's release download host | Nothing. This request only happens if you accept an offered update, and it only downloads an APK | Only when you tap to install an update |

All requests are **HTTPS**. Neither request carries a name, email address, account, advertising ID or
any other identifier that links the request to you — but a server always sees the IP address it is
contacted from, so a university or GitHub log can associate a request with a network, and with the
time it was made. That is unavoidable for any app that fetches data. If you would rather the app
never contacted GitHub on its own, turn the automatic update check off.

**A downloaded update is verified before it is installed.** The app checks that the APK is signed
with the same key as the installed app and refuses to hand anything else to the system installer.

---

## What is stored on your device

Everything below is in the app's own private storage. Nothing here is uploaded anywhere, and
**Android backup is switched off** (`android:allowBackup="false"`, with explicit excludes in the
backup and device-transfer rules) — so none of it goes to Google's cloud backup or to a new phone
via device transfer.

| Data | Where | Why | How to delete just this |
|---|---|---|---|
| Cached timetable events — module code, title, lecturer, room, dates, and the class/cohort group of the timetable you opened | Room DB (`timetable_cache.db`) | So your schedule opens instantly and works offline | Settings → Clear All Cached Data, or per course |
| Saved / bookmarked courses (name, programme code, cohort) | Room DB | Quick access, and the pinned home screen | Settings → Remove next to the course |
| Search history — the exact terms you typed, with timestamps | Room DB | Quick re-search | Search screen → History → Delete All |
| Your pinned ("starred") course, and the last cohort you picked per course | SharedPreferences | Reopen where you left off | Erase all app data, or unstar the course |
| View state — semester, week, day tab, per course | SharedPreferences | Same | Erase all app data |
| Theme and custom colour | SharedPreferences | Your appearance choice | Erase all app data |
| Cached week markers / anchor weeks | SharedPreferences | Knows which weeks were already fetched, including weeks with no classes | Erase all app data |
| Sync settings (strategy, hide-empty-weeks, update-check toggle) | SharedPreferences | Your choices | Erase all app data |
| The saved download ID for an update | SharedPreferences | Tracks an in-progress update download | Erase all app data |
| **Crash record** — if the app ever crashes, the exception message and stack trace | A marker file in app storage, plus SharedPreferences | So the next launch can offer to recover instead of failing silently. **This is never sent anywhere** — it exists only so the recovery screen can show it to you | Try Again / Clear Cache on the recovery screen, or Erase all app data |

A crash's stack trace can incidentally contain the course identity the app was working with. It
stays on the device; it is not uploaded.

### What is NOT stored

No name, email address, phone number, contact list, location, advertising ID, or any hardware or
install identifier. The app has no login and no server of its own.

---

## Erasing everything

**Settings → Privacy → Erase all app data** deletes every one of the items in the table above — the
cached timetable, saved courses, search history, settings, theme and the crash record — and restarts
the app. It cannot be undone.

That control exists because the previous version of this policy said there was nothing to erase
while the app was in fact holding all of the above. It was, and this is the fix.

You can also remove data one piece at a time with the per-item controls in the table, or wipe
everything from Android's **Settings → Apps → TimeTable → Storage → Clear data**.

---

## Your rights under the GDPR (Articles 15–21)

The developer holds no personal data about you: nothing is transmitted to the developer, and there
is no server of theirs to hold it. So there is nothing for them to give you access to, rectify,
port, or erase.

The data in the table above is **yours, on your device**, and you remain in control of it:

- **Access / portability** — the app shows your saved courses, cached data statistics and crash
  record, and it all lives in app storage on your device.
- **Erasure / restriction** — the per-item controls listed above, or **Erase all app data** for
  everything at once.

Two third parties may hold request logs of the network calls described earlier — TU Dublin (for
timetable requests) and GitHub (for update checks) — under their own privacy policies, below.

---

## Third-Party Services

| Service | Role | What it may log | Privacy notice |
|---|---|---|---|
| TU Dublin Scientia Publish API | Timetable data provider | Request IP, the course requested, time | Governed by TU Dublin's privacy policy |
| GitHub (`api.github.com`, release downloads) | Release hosting for app updates | Request IP, time | [GitHub Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-privacy-statement) |

---

## Permissions the app asks for, and why

| Permission | Why it is needed |
|---|---|
| `INTERNET` | To fetch timetables, and to check for updates |
| `POST_NOTIFICATIONS` | To tell you when a background sync finished (you can decline this; the app still works) |
| `REQUEST_INSTALL_PACKAGES` | To hand a downloaded update to Android's package installer. **The app cannot install anything silently** — Android always shows you a confirmation, and the update is checked against the app's own signing key first |

No storage permission is requested: the update APK is written to the app's own scoped directory,
which needs none. (An earlier version declared `WRITE_EXTERNAL_STORAGE` and never used it.)

---

## Contact

For privacy-related questions, open an issue on the [repository](https://github.com/Izzeddin-Hammad/TUD-TimeTable-APP/issues) or contact the developer via GitHub.

---

## Changes to This Policy

If this policy changes, the new version will be posted here and the "Last updated" date revised. It
was last substantially revised in v2.1, to describe what the app actually stores and to add the
in-app eraser.
