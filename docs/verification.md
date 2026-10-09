# Verification

## Automated checks

Run `python3 scripts/build.py --unsigned` and `bash scripts/test.sh`. The build verifies the pinned base and Apktool hashes and requires exact patch anchors. The tests cover component matching, duplicate services, stable ordering, missing active apps, package identity, feature availability, settings wiring, and the feature guide.

## Device checks

Use `FIRE_TV_UI_TEST_SERIAL=emulator-PORT python3 -m unittest tests.test_settings_routes -v` to verify that each bundled saver opens its declared settings activity. The test refuses non-emulator devices and restores the original screensaver selection and enabled state.

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

The signed version-code-24 APK includes the 17-entry catalog and has SHA-256 `7fa85b4072582adbafea8f8bb6a3fdaf850918aad2de6bf5919844613d7a6dcb`. The developer and ADB runtime checks below used the signed version-code-23 APK with SHA-256 `aad8dde8b78f570f3fbe4d36ee7c89f35eaa8f93fcb6275de45edde87abaf345`. Signature verification and 16 KB alignment passed. Each saver entry is checked against its GitHub release metadata. Preview images are inspected at 384x216; source captures and hashes are recorded in `app/previews/sources.json`.


The Fire TV checks below used the prior nine-entry build before the final byte-size formatting change. The final APK was installed and reboot-tested on the Android TV emulator. Small downloads now display kilobytes; RAM and storage totals can display gigabytes.

| Device | Observed result |
| --- | --- |
| Fire TV AFTDEC012E, Android 11 / API 30 | Signed APK installed. Settings opens Screensavers directly, with a near-full-page card, circular 48x48 selection indicators, sample images, and per-app preview actions. |
| Fire TV | Screensaver On/Off and selection were changed and read back. Colors ran as the normal system dream. The original AndroSaver selection was restored. |
| Fire TV | Sleep and screensaver timers, deep sleep Off/On, and the Fire OS Alexa auto-wake preference were exercised. Deep sleep On restored the prior sleep timeout. The user subsequently required deep sleep to stay Off. The saved preference and actual `sleep_timeout` were set to zero, with five minutes until the screensaver and Alexa auto-wake enabled. The 16-entry version-code-20 update completed through the CLI and preserved that choice. |
| Fire TV | Native Home routing was disabled and enabled. Home returned to Fire TV UI after the Amazon transition. The gear opened Amazon's full Settings activity while Home routing remained enabled; Wi-Fi settings stayed reachable. |
| Fire TV | Local settings protection reverted an outside screensaver change. Intentional CLI changes updated the desired values. Screensaver unlock allowed a new selection until protection was enabled again. Home Redirect 1.2.2 reported no conflicting restored settings. |
| Fire TV | The startup page's local updater blocker was enabled with TV VPN consent. Its selected UID scope excluded the launcher. Persistent backups were saved to TV Downloads and retrieved to private computer files without publishing layout data. |
| Android TV emulator, Android 12 / API 31 | Launcher menus, About, Help, native cards, and navigation were inspected. Settings has no Premium entry. About and Help have no rating, purchase, upgrade, or project prompts. |
| Android TV emulator, Android 12 / API 31 | Twilight Hearth, Nebula Drift, Vortex Spiral, Signal Rain, Retro Flight, Pipeworks Dream, Aqua Surface Drift, Earthbound Orbit, and Cloud Drift Clock were installed from their published APKs. The picker displayed their bundled previews; Cloud Drift Clock's preview started its actual DreamService. Settings opened the declared activities for the checked apps. The original system screensaver selection, enabled value, activation setting, and timeout were restored and read back unchanged. |
| Android TV emulator, Android 12 / API 31 | Install screensavers displayed the newly added release entries as installed. Earthbound Orbit was installed through the app's catalog flow. No physical TV was used for these checks. |
| Android TV emulator | CLI uninstall/reinstall and backup restoration preserved a 35-preference profile, including native controls. Native Aerial Views installation and removal completed through the catalog and Android confirmation pages. |
| Isolated Android TV emulator, final release checks | The 13-entry APK passed native Home routing Off and On, update preservation, and a complete uninstall/reinstall restoring all 36 saved settings from the TV Downloads backup. The rebuilt Home Redirect 1.2.2 helper was installed for the final installer checks. A fresh install granted TV listings access without the prior permission dialog. The final 16-entry update preserved the complete saved profile and Home routing. Earlier checks also passed three consecutive CLI updates. |
| Isolated Android TV emulator, final APK reboot | Fire TV UI became the resumed activity automatically after a real emulator reboot. The native protection service restarted, and all six checked Home, screensaver, enabled-state, and timer settings matched the saved baseline. No manual app launch was used to establish startup. |
| Isolated Android TV emulator, version code 21 | Clean simple setup set `sleep_timeout=0`. The local guard restored zero after an outside command set the timeout to 14 minutes. After a real emulator reboot, the timeout remained zero, Fire TV UI resumed automatically, and its protection service was running. No manual launch preceded the startup checks. The ADB protection command rejected the unsupported Android TV device. The complete original saved profile was restored and compared after the checks. |
| Isolated Android TV emulator, version code 22 | A fresh app profile defaulted to saved Deep sleep Off while the factory timeout was 14 minutes and general protection was Off. Outside factory changes were repaired to Off. Enabling general protection preserved the app choice instead of capturing the factory value. An intentional five-minute choice was saved and repaired correctly. The native timers page displayed Off. A real reboot retained Off and restarted the control service while general protection remained Off, before any manual app launch. The original complete emulator profile was restored afterward. |
| Fire TV AFTDEC012E, version code 23 | The signed APK was installed and the complete CLI update finished. Developer options, ADB, and Wi-Fi ADB read back as 1. With general protection Off, an outside developer-options change to 0 was restored to 1 by the app. All 43 prior backup preferences were preserved, including Deep sleep Off and Home On; the new saved ADB protection choice was true. Home returned to Fire TV UI, and the accessibility redirect service was actually bound. General protection was restored to On. The TV was not rebooted again. |

Evidence includes the [picker's first rows](../art/screenshots/tv-screensaver-first-tv-1920.png), [lower rows](../art/screenshots/tv-screensaver-lower-tv-1920.png), and [normal Colors dream](../art/screenshots/screensaver-colors-runtime-tv-1920.png). The final catalog's [first entries](../art/screenshots/install-screensavers-tv-1920.png), [last entries](../art/screenshots/install-screensavers-lower-tv-1920.png), and updated [memory display](../art/screenshots/memory-tv-1920.png) were captured on the Android TV emulator. The README contains the remaining interface screenshots.

## Checks still requiring hardware access

A physical TV reboot was initiated after installing the prior nine-entry APK. The TV returned on the network, but its ADB port stopped accepting connections. After the user re-enabled debugging, the prior native Home and protection services and updater VPN were running. Deep sleep was changed to Off and backed up before installing the signed 16-entry version-code-20 APK and rebuilt Home Redirect 1.2.2 helper through the CLI. The device's persistent ADB flag was changed to enabled, but another physical reboot still returned with port 5555 refusing connections. That flag alone did not fix restart persistence.

Version code 21 adds an explicit Fire TV ADB protection choice, global debugging-setting observers, and startup repair before Home setup. Version code 22 makes the app preference authoritative for sleep, defaults missing choices to Off, and reapplies the choice before ADB, Home, preview, and general-protection checks. Factory values cannot replace that preference, and the control service keeps running when general protection is Off. Version code 23 defaults developer/ADB protection to On in code and restores the three global flags independently of general protection. Physical installation and runtime repair were verified after the connection returned. Boot persistence of this correction has not been retested on the physical TV; no further TV reboot was performed. Do not count the reboot request or emulator results as proof of physical Fire TV debugging persistence.

APK updates can briefly restart adbd when enabling Fire TV debugging, and native Home binding can still be settling when the installer checks it. The toolkit now reconnects and retries the read-only helper check after a connection exit, and retries a failed Home restoration once. The complete physical TV update passed with these changes. Three focused local regression cases passed, including preservation of a genuine Home rejection.

Physical Android TV and Google TV devices have not been tested. The Android TV emulator is not a substitute for those vendor implementations. Android 8.1 is the manifest minimum; device checks covered API 30 and 31. Every legacy widget and wallpaper source has not been tested. Play Protect blocked some older mirrored screensaver APKs on the emulator; those restrictions were not bypassed.

The toolkit's 413 automated checks passed on Node 18 and 20 for commit `ce619ea266545749c5e838487ce5265e060c8fe3`. All six existing GitHub checks passed. The SonarCloud quality gate for that exact revision passed with 100% new-code coverage and all security hotspots reviewed. The installer downloads private GitHub assets, strips credentials from redirects, verifies helper upgrades, and preserves existing TV listings permission choices. A live download of the private draft asset matched the signed APK; published-tag retrieval cannot be tested until publication. Fire TV UI itself has no CI workflow. Signed release artifacts remain drafts until the remaining device checks are completed. The GUI APK stays in its private repository.
