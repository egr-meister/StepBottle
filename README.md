# StepBottle

A lightweight, offline daily log for **manually recorded water and activity minutes**. Native Android, Kotlin and Jetpack Compose.

StepBottle does **not** count steps, read sensors, use GPS/location, connect to Health Connect or wearables, detect exercise, calculate calories, score health or give medical advice. Every record is typed or tapped in by the user.

## Features

- **Two-card daily log** — a compact date selector, then two equally prominent cards:
  - **Water**: recorded volume (mL), water-entry count, three quick portions, *Add Water*.
  - **Activity Minutes**: recorded minutes, activity-entry count, three quick durations, *Add Activity*.
  - Cards stack on phones and sit side by side on screens ≥ 600 dp wide.
- **Daily Summary** (“750 mL water recorded.”, “25 activity minutes recorded.”, entry counts; “No entries.” / “No records for this day.” when empty).
- **Combined chronological entry list** with *All / Water / Activity* filters (the filter is remembered).
- **Quick entries**: water shortcuts log immediately at the current local date/time; activity shortcuts open a compact confirmation sheet with category selection (the last category is remembered). Each activation is protected from duplicate processing; deliberate separate taps create separate entries.
- **Custom entries** with Save/Cancel: water (mL, date, time) and activity (minutes, category, optional label, date, time).
- **Editing** of every field, including moving a record to another date; **deletion** with confirmation (editor) or a reversible *Undo* (list). Undo restores the exact record once.
- **Weekly history**: today plus the previous six dates, navigation to earlier seven-day ranges, two separate mini charts (Water in mL, Activity in minutes, each with its own labelled scale), a per-day table and seven-day totals. Selecting a date opens its daily log; Back returns to Week.
- **Optional daily reminder** (off by default, 19:00 suggested), approximate delivery.
- **Settings**: three water shortcuts, three activity shortcuts, reminder, privacy information, *Clear all local data*.
- First launch allows logging immediately and shows “Water and activity are entered manually.” No personal, physical or medical information is requested.

## Manual logging rules and limits

| Rule | Water | Activity |
|---|---|---|
| Amount per entry | 1–5,000 mL, whole mL | 1–600 min, whole minutes |
| Per-day limit | — | ≤ 1,440 recorded minutes per local date |
| Optional label | — | ≤ 40 characters (trimmed, whitespace collapsed) |
| Date | today or a past date | today or a past date |
| Time | no future timestamps | no future timestamps |
| Defaults | 150 / 250 / 500 mL | 5 / 15 / 30 min |

These are technical input limits, not consumption or exercise guidance. Activity categories: Walk, Exercise, Stretch, Other. An activity's timestamp only places it in the diary; it is not a recorded workout start/end interval, and overlaps are not detected or interpreted.

The 1,440-minute daily limit is checked **inside a Room transaction** when adding, editing (excluding the entry's own minutes, on the target date), and when undoing a deletion.

Adding from a past date preselects that date and **requires a time**; quick portions/durations on a past date open the editor rather than silently logging against today. No record is ever created automatically.

## Summary and weekly formulas

All totals are derived from stored records (SQL `SUM`/`COUNT` over Room rows, `Long` aggregates). Water and activity are never combined into a score and never converted into each other.

- Daily water = Σ `amountMl` of water entries whose `loggedLocalDate` = D.
- Daily activity = Σ `durationMinutes` of activity entries whose `loggedLocalDate` = D.
- Week = the 7 dates `end-6 … end` (default `end` = today).
- Total recorded water / activity = Σ of the daily values over the 7 dates.
- Days with water records = number of dates with ≥ 1 water entry; same for activity.
- **Average water on days with water records** = total water ÷ days with water records (rounded half-up; shown as “No entries” when 0 days).
- **Average activity on days with activity records** = total activity ÷ days with activity records.

The two averages use separate denominators and are labelled as descriptions of the records, not targets, diagnoses or health assessments. A day without entries is shown as “No entries”, never as proof of zero intake or inactivity.

## Date attribution

Each entry stores the absolute timestamp, the logged local date, the logged local time, the zone ID, and creation/update timestamps. History is grouped by the **saved local date/time**, so travelling or changing the time zone never moves records between dates. Only an explicit edit changes the date attribution. Ambiguous local times (DST overlap) use the earlier instant; nonexistent local times (DST gap) are rejected with a message. Today refreshes at midnight, on foreground return and on time/time-zone changes, without touching records. No online time check is used.

## Architecture

One `:app` module, manual DI (`AppContainer`).

```
core/                TimeProvider (injectable clock + zone), TodayClock, Format
data/local           Room entities/DAOs/database (+ migrations array), DataStore settings
data/repository      LogRepository (both record types, transactional limits, restore)
domain/water         water entry model, limits, validation
domain/activity      activity entry model, categories, limits, label rules, validation
domain/summary       daily summary, combined list/filters, weekly statistics
domain/reminders     next occurrence, delivery decision, permission/channel availability
domain/time          whole-number parsing, DST-aware time resolution, date/time rules
domain/actions       duplicate-activation guard, single-use undo slot
reminders/           AlarmManager scheduling, notification, receivers
ui/today ui/entry ui/week ui/settings ui/theme ui/components ui/navigation
FeedbackCenter       confirmations with Undo shared by the log and the editor
```

Kotlin, Jetpack Compose, customised Material 3, Navigation Compose, ViewModel + `StateFlow`, `collectAsStateWithLifecycle`, coroutines, Room (KSP) and DataStore. All database work uses Room's suspend/Flow APIs off the main thread. Drafts live in `SavedStateHandle` (survive rotation and process death); leaving a form with unsaved changes asks first. Back handling is predictive-Back compatible (`enableOnBackInvokedCallback`, `BackHandler` only while a draft is dirty). Back from a historical daily log returns to Week; Back from Today exits normally.

### Room

- `water_entries(id, amountMl, timestamp, loggedLocalDate, loggedLocalTime, zoneId, createdAt, updatedAt)`
- `activity_entries(id, durationMinutes, category, label, timestamp, loggedLocalDate, loggedLocalTime, zoneId, createdAt, updatedAt)`
- Both indexed by `(loggedLocalDate, loggedLocalTime)`.
- Schemas are exported to `app/schemas/` (CI fails if the committed schema differs from the generated one). Future versions add explicit `Migration`s to `StepBottleDatabase.MIGRATIONS`; destructive migration is never enabled.

DataStore holds the shortcuts, reminder settings (enabled, time, schedule generation), last activity category, the “permission already asked” flag, the intro dismissal and the list filter.

## Toolchain (pinned)

| Component | Version |
|---|---|
| Android Gradle Plugin | 8.12.0 |
| Gradle (wrapper, committed) | 8.14.3 |
| Kotlin / Compose compiler plugin | 2.2.0 |
| KSP | 2.2.0-2.0.2 |
| Compose BOM | 2025.06.00 |
| Room | 2.7.1 |
| DataStore | 1.1.7 |
| Navigation Compose | 2.9.0 |
| Lifecycle | 2.9.0 |
| JDK | 17 (Temurin in CI) |
| compileSdk / targetSdk | **36** |
| minSdk | **26** |
| Build tools (CI) | 36.0.0 |

Android 16 (API 36) behaviour handled: edge-to-edge is always on (insets handled with `safeDrawing`, IME and cutouts), predictive Back is enabled, the app is resizable and supports portrait/landscape/tablets, no fixed orientation or aspect-ratio restrictions, and only inexact alarms are used.

## Offline, privacy, storage and backup

- No `INTERNET` or `ACCESS_NETWORK_STATE`; no networking libraries, accounts, backend, Firebase, ads, analytics, payments or cloud services.
- Data is stored in app-private storage (Room database + DataStore).
- `android:allowBackup="false"`, `fullBackupContent` and `dataExtractionRules` exclude everything from cloud backup and device-to-device transfer.
- User-written labels are never logged.
- Bundled privacy information is available in Settings → Privacy information.

### Permission allowlist

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Optional daily reminder (requested on Android 13+ only right after the user turns the reminder on, at most once) |
| `RECEIVE_BOOT_COMPLETED` | Restore the reminder schedule after a restart |
| `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Signature-level permission added by AndroidX Core; not a runtime permission |

Not requested: `ACTIVITY_RECOGNITION`, location, body sensors, Health Connect, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `WAKE_LOCK`, foreground services, battery-optimisation exemptions. CI checks the packaged APK, the AAB manifest and the merged manifest against the allowlist.

### Reminder behaviour and limitations

- One daily reminder: title “StepBottle”, body “Would you like to update today’s water and activity log?”. It never tells anyone to drink or exercise. Tapping it opens Today.
- Scheduling uses `AlarmManager.setAndAllowWhileIdle` (inexact one-shot) for the next occurrence in the device's current time zone, with an explicit, immutable `PendingIntent` to a non-exported receiver. **Delivery time is approximate.**
- Before posting, the receiver confirms the reminder is still enabled, the callback belongs to the current schedule generation and time, it is not early and not from a previous day, and notification permission plus app/channel availability. One notification ID is used; no backlog is ever delivered.
- Rescheduled after delivery, settings changes, reboot, time/time-zone change, app update and every app start.
- If permission is denied, logging keeps working; Settings explains the state and offers the system notification settings. The app does not prompt repeatedly.
- **Force-stopping the app cancels its alarms; reminders resume only after StepBottle is opened again.**

## Building

```bash
./gradlew testDebugUnitTest lintRelease          # focused tests + release lint
./gradlew assembleDebug                           # debug build (".debug" applicationId suffix)
./gradlew assembleRelease bundleRelease           # signed release (needs signing credentials)
./gradlew assembleRelease bundleRelease -PstepbottleMinify=true   # R8 + resource shrinking (only after the non-minified release is verified)
```

Artifacts:

- APK: `app/build/outputs/apk/release/app-release.apk` (local install/testing only)
- AAB: `app/build/outputs/bundle/release/app-release.aab` (**the only artifact uploaded to Google Play**)
- R8 mapping (when minified): `app/build/outputs/mapping/release/mapping.txt` — keep it for every minified release.

## Signing (PKCS12) and GitHub Secrets

The release build type explicitly uses `signingConfigs.getByName("release")`. Missing credentials fail every release packaging task; there is no fallback to debug signing.

Credentials come from environment variables (CI) or from an **uncommitted** `keystore.properties` in the project root:

```properties
storeFile=/absolute/path/to/stepbottle-release.p12
storePassword=…
keyAlias=…
keyPassword=…
```

The keystore and its credentials are kept outside the repository. `.gitignore` excludes `keystore.properties`, `*.p12`, `*.jks`, `*.keystore`, `*.base64` and `secrets.txt`.

GitHub → Settings → Secrets and variables → Actions:

| Secret | Content |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64 of the PKCS12 keystore (one line) |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password |

CI (`.github/workflows/android.yml`) uses the committed wrapper and JDK 17, runs unit tests and release lint, decodes the keystore to the runner's temp directory, builds the signed APK and AAB, runs `apksigner verify --print-certs` (fails on errors, on `CN=Android Debug` or on a signer that differs from the keystore certificate), verifies the AAB with `jarsigner -verify` and checks its signer, checks permissions and native alignment, uploads the verified artifacts and always deletes the temporary keystore. A self-signed upload certificate is accepted. No emulator test is required in CI. A manual run with `minify = true` repeats all checks for the R8 build and uploads the mapping file.

## 16 KB page-size compatibility

CI inspects the APK and AAB for native `.so` files (including transitive dependencies) and, if any exist, verifies ELF `LOAD` segment alignment ≥ 16 KB and APK zip alignment (`zipalign -c -P 16`). StepBottle uses no NDK code and its dependencies (AndroidX, Compose, Room with the framework SQLite driver, DataStore, coroutines) are pure JVM; the expected finding is **no native libraries**, which CI records in `verification/native-libs.txt` and in the run summary. Targeting API 36 alone is not treated as proof; if a future dependency adds native code, the alignment checks apply and a 16 KB device/emulator test is required before claiming runtime compatibility.

## Local install and logcat verification

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.egrmeister.stepbottle/.MainActivity
adb logcat --pid=$(adb shell pidof -s com.egrmeister.stepbottle)
```

Check: offline launch (airplane mode), both entry types, shortcuts, historical edits, summaries, weekly history, reminder grant/deny and delivery, reboot recovery (`adb reboot`), process recreation (Developer options → Don't keep activities, or `adb shell am kill` while backgrounded), TalkBack and large font sizes, rotation, Clear all data. Record the device, Android version and results in `VERIFICATION.md`.

## Artifact size and pending checks

Release APK/AAB sizes are reported in each CI run summary and in `verification/sizes.txt` inside the uploaded artifact. See `VERIFICATION.md` for the current status of every check; anything not yet performed is marked **pending**.
