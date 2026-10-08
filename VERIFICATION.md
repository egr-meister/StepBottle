# StepBottle — verification notes

Status: ✅ done · ⏳ pending · ⛔ blocked (reason given)

Upload certificate (PKCS12 release key) SHA-256:
`37:92:D3:49:75:70:A0:EA:7A:E5:6C:B1:76:53:62:B6:7A:FB:A7:DF:83:17:55:34:A6:B9:89:03:42:9F:FB:10`

## Build and signing (CI)

| Check | Status | Notes |
|---|---|---|
| Focused unit tests (`testDebugUnitTest`) | ✅ CI run #1 | |
| Release lint (`lintRelease`) | ✅ CI run #1 | |
| Room schema exported and committed | ⏳ pending | `1.json` (identityHash `3f33ad28…`) taken from CI run #1, committed |
| Signed non-minified APK + AAB | ⏳ pending | |
| `apksigner verify --print-certs`, no `CN=Android Debug`, signer = release key | ✅ CI run #1 (APK) | v2 scheme, signer SHA-256 matches the keystore |
| AAB `jarsigner -verify` + expected signer | ⏳ pending | |
| Permission allowlist (APK, AAB, merged manifest) | ⏳ pending | Allowed: POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED (+ AndroidX signature permission) |
| Native `.so` inventory / 16 KB alignment | ⏳ pending | Run #1 found `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`; alignment check on APK, AAB and AAB-generated universal APK pending |
| 16 KB runtime test (16 KB emulator/device) | ⏳ pending (device) | Required because native libraries are present |
| Release artifact size | ⏳ pending | From the CI run summary |
| R8 + resource shrinking run, checks repeated, mapping kept | ⏳ pending | Only after the non-minified release is verified |

## Device checks (`adb install` of the signed release APK + `adb logcat`)

Device / Android version: ⏳ not recorded yet

| Check | Status |
|---|---|
| Offline launch (airplane mode) | ⏳ pending |
| Water quick portions, custom entry, Undo | ⏳ pending |
| Activity quick durations sheet, category memory, custom entry, 1,440-minute limit | ⏳ pending |
| Historical day: date preselected, time required | ⏳ pending |
| Editing and moving records between dates (summaries + week update) | ⏳ pending |
| Deletion confirmation / Undo restores once | ⏳ pending |
| Weekly history, earlier ranges, separate averages, open day → Back to Week | ⏳ pending |
| Reminder: enable, permission grant/deny, settings link, delivery, tap opens Today | ⏳ pending |
| Reboot recovery | ⏳ pending |
| Process recreation (draft kept) | ⏳ pending |
| Accessibility (TalkBack labels, chart text alternative, font scale 200%) | ⏳ pending |
| Rotation, tablet/resizable layout | ⏳ pending |
| Clear all data (reminder cancelled, defaults restored) | ⏳ pending |
| logcat: no crashes, no user labels logged | ⏳ pending |
