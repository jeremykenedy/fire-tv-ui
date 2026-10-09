# Verification

## Automated checks

Run `python3 scripts/build.py --unsigned` and `bash scripts/test.sh`. The build verifies the pinned base and Apktool hashes and requires exact patch anchors. The tests cover component matching, duplicate services, stable ordering, missing active apps, package identity, feature availability, settings wiring, and the feature guide.

## Device checks

Use a signed APK and a specific ADB serial. Save the current values of `secure screensaver_components`, `secure screensaver_enabled`, and `secure enabled_accessibility_services` before testing. Restore any system settings changed by the test.

1. Install and launch Fire TV UI. Check its icon, label, onboarding, Settings, About, and Help.
2. Before granting screensaver permission, select a different screensaver. Confirm that setup instructions appear and the system selection stays unchanged.
3. Grant `android.permission.WRITE_SECURE_SETTINGS` and reopen the picker.
4. Confirm that installed system and third-party dream services are listed and the current app is checked. Inspect each preview, including rows reached by scrolling. Wait for animation or video before accepting a new sample; a blank or faint loading frame is not useful.
5. Use the remote to select a different screensaver. Read `screensaver_components` back through ADB and check that it matches the selected row.
6. Toggle the screensaver Off and On. Read `screensaver_enabled` back after each change. Confirm that the selected app is preserved.
7. Restart Fire TV UI and confirm the selection persists.
8. Start the TV's screensaver through its existing device control. Confirm the selected dream is actually running, rather than relying on a saved setting alone.
9. Check wallpaper modes, widgets, app row counts, custom icons, folders, and backups. Confirm that no purchase prompt is present in Settings or Help.
10. If testing Home redirection, preserve unrelated accessibility services and check both Home and Amazon Settings. Test reboot separately before claiming boot persistence.

Testing a saved component alone does not establish that a particular screensaver can render on that TV. Keep runtime results separate from build and settings checks.

## Verified build on October 8, 2026

The signed `fire-tv-ui-1.0.0.apk` has SHA-256 `b84946de3c94f9952a3626d1ebf2fc4518ad41149c4c9f4aa42ff74f0531b3ca`. Signature verification, 16 KB alignment, ZIP integrity, 28 JVM checks, and six Python tests passed. The final catalog pins nine published APKs by package, version, byte count, and SHA-256. The new preview images were inspected at their displayed size; source captures and hashes are recorded in `app/previews/sources.json`.

| Device | Observed result |
| --- | --- |
| Fire TV AFTDEC012E, Android 11 / API 30 | Signed APK installed. Settings opens Screensavers directly, with a near-full-page card, circular 48x48 selection indicators, sample images, and per-app preview actions. |
| Fire TV | Screensaver On/Off and selection were changed and read back. Colors ran as the normal system dream. The original AndroSaver selection was restored. |
| Fire TV | Sleep and screensaver timers, deep sleep Off/On, and the Fire OS Alexa auto-wake preference were exercised. Deep sleep On restored the prior sleep timeout. The final baseline is 14 minutes until sleep, five minutes until the screensaver, with Alexa auto-wake enabled. |
| Fire TV | Native Home routing was disabled and enabled. Home returned to Fire TV UI after the Amazon transition. The gear opened Amazon's full Settings activity while Home routing remained enabled; Wi-Fi settings stayed reachable. |
| Fire TV | Local settings protection reverted an outside screensaver change. Intentional CLI changes updated the desired values. Screensaver unlock allowed a new selection until protection was enabled again. Home Redirect 1.2.2 reported no conflicting restored settings. |
| Fire TV | The startup page's local updater blocker was enabled with TV VPN consent. Its selected UID scope excluded the launcher. Persistent backups were saved to TV Downloads and retrieved to private computer files without publishing layout data. |
| Android TV emulator, Android 12 / API 31 | Launcher menus, About, Help, native cards, and navigation were inspected. Settings has no Premium entry. About and Help have no rating, purchase, upgrade, or project prompts. |
| Android TV emulator | CLI uninstall/reinstall and backup restoration preserved a 35-preference profile, including native controls. Native Aerial Views installation and removal completed through the catalog and Android confirmation pages. |

Evidence includes the [picker's first rows](../art/screenshots/tv-screensaver-first-tv-1920.png), [lower rows](../art/screenshots/tv-screensaver-lower-tv-1920.png), and [normal Colors dream](../art/screenshots/screensaver-colors-runtime-tv-1920.png). The README contains the remaining interface screenshots. Catalog screenshots predate the latest entries.

## Checks still requiring hardware access

A physical TV reboot was initiated after installing the final APK. The TV returned on the network, but its ADB port stopped accepting connections. Automatic startup, post-reboot protection and VPN state, and the final published-CLI installation therefore remain unverified. Do not count the reboot request itself as a successful boot test.

Physical Android TV and Google TV devices have not been tested. The Android TV emulator is not a substitute for those vendor implementations. Android 8.1 is the manifest minimum; device checks covered API 30 and 31. Every legacy widget and wallpaper source has not been tested. Play Protect blocked some older mirrored screensaver APKs on the emulator; those restrictions were not bypassed.

The toolkit's automated checks cover installer cancellation, clean setup, update preservation, backup retention and transfers, malformed files, checksums, rejected device settings, Home rollback, and guard interoperability. GitHub Actions results are separate from the native device checks. Signed release artifacts should be published after the remaining device checks are completed.
