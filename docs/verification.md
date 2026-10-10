# Verification

## Automated checks

Run `python3 scripts/build.py --unsigned` and `bash scripts/test.sh`. The build verifies the pinned base and Apktool hashes and requires exact patch anchors. The tests cover component matching, duplicate services, stable ordering, missing active apps, package identity, feature availability, settings wiring, and the feature guide.

## Device checks

### Fire TV UI 1.1.3, version code 32

The signed patch was installed over version code 31 on the physical Fire TV AFTDEC012E running Fire OS / Android 11. The device regression test opened Settings repeatedly and confirmed that Back, Menu, and Home dismissed it. Theme Mode returned to the launcher with Back, and Settings could be reopened and dismissed afterward. The gear icon opened Amazon Settings, and Back returned to Fire TV UI.

Aquarium 4K 1.3.6 activated automatically after inactivity with a temporary 15-second delay. Dream Manager reported the Aquarium service as a normal, non-test dream, and two captured frames showed the fish moving. Back stopped the dream and returned to Fire TV UI without a Settings panel. The original five-minute delay was restored, and all 45 saved launcher preferences matched the pre-update backup. The runtime frames are [Aquarium after idle](../art/screenshots/aquarium-idle-fire-tv-1920.png) and [a later animation frame](../art/screenshots/aquarium-motion-fire-tv-1920.png).

`FIRE_TV_UI_MENU_TEST_SERIAL=YOUR_TV_SERIAL bash scripts/test.sh` passed 38 Java checks and nine Python checks. The separate catalog settings-route test was skipped because its emulator serial was not configured. UI capture retries discard stale hierarchy files when Android's automation bridge cannot return a root during a transition.

The signed APK SHA-256 is `7e793de0224699c44bbecd7b177aeff6143620c381454285555ce66eb771627a`. The build passed APK alignment and v2/v3 signature verification. This private repository has no CI workflow; verification ran locally and on the physical TV. No physical reboot was performed for this patch.

### Fire TV UI 1.1.2, version code 31

| Platform | Result | Notes |
| --- | --- | --- |
| Physical Fire TV AFTDEC012E, Fire OS / Android 11, 1920x1080 | Verified | Installed the signed update over version code 30. Opened Settings > Screensavers > System timers, selected the five-minute TV Sleep Timer, and confirmed the native Fire OS sleep and notification alarms were scheduled. Selected Off and confirmed both alarms were removed. The timer page returned to Off. The screensaver delay stayed at five minutes, Deep Sleep remained Off, and its timer stayed at 20 minutes. The final timer screen was captured and visually checked. |
| Android TV and Google TV devices | Not tested for this release | Looking for device owners to install the update and report the model, OS/API, resolution, and timer behavior. |

The signed version-code-31 APK SHA-256 is `0d4ca2276846961e1eb02155f2c73f6d7cd70efe06b1582ac8e3f0877e76a547`. Its checksum sidecar is included with the release. The APK was installed over the previous release, preserving its app identity and settings.

### Fire TV UI 1.1.1, version code 30

| Platform | Result | Notes |
| --- | --- | --- |
| Android TV emulator, Android 12 / API 31, 1920x1080 | Verified | Installed the signed build, opened the timers page, and confirmed separate native TV Sleep Timer, screensaver delay, Deep Sleep toggle, and Deep Sleep timer controls. Set `sleep_timeout` to 60,000 ms and 0, then restored its original 1,200,000 ms value. The temporary emulator-only Fire TV setting key allowed the Deep Sleep toggle UI to be checked. |
| Physical Fire TV | Not tested for this build | Looking for a Fire TV owner to test the controls and report the model, Fire OS/API, and observed behavior. Emulator key simulation does not establish Fire OS or Alexa behavior. |

The final emulator captures are [About](../art/screenshots/about-tv-1920.png) and [System timers](../art/screenshots/timers-tv-1920.png). No physical TV was used for this release verification.

### Fire TV UI 1.1.0, version code 29

| Platform | Result | Notes |
| --- | --- | --- |
| Android TV emulator, Android 12 / API 31, 1920x1080 | Verified | Updated from version code 25. The Home Redirect picker selected Helios Lightfield, and the choice remained active after the protection interval. Restored the original Colors screensaver and verified it remained enabled. |
| Google TV emulator, Android 14 / API 34, 1920x1080 | Verified | Installed the signed version-code-29 build, checked settings navigation for every catalog screensaver, and verified download, checksum validation, installation, and installed-state display for new catalog entries. Both new saver previews were captured and visually checked at 1920x1080. |
| Physical Fire TV | Not tested for this build | Looking for a Fire TV owner to test selection and restart behavior, then report the device model, Fire OS/API, resolution, tested behavior, and results in an issue. |
| Physical Android TV | Not tested | Looking for an Android TV owner to test installation and screensaver selection, then report the model, OS/API, resolution, tested behavior, and results in an issue. |
| Physical Google TV | Not tested | Looking for a Google TV owner to test installation and screensaver selection, then report the model, OS/API, resolution, tested behavior, and results in an issue. |

The version-code-29 catalog adds Skyburst Nocturne and Pulse Circuit to the version-code-28 catalog. Their signed release APKs and preview source hashes are pinned in `app/screensavers.json` and `app/previews/sources.json`.

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

The signed version-code-25 APK includes the 17-entry catalog and has SHA-256 `711138abff2cffb706f8e64baebb40fe3e78bf2016afb6f446203fb6c0bbd769`. The developer and ADB runtime checks below used the signed version-code-23 APK with SHA-256 `aad8dde8b78f570f3fbe4d36ee7c89f35eaa8f93fcb6275de45edde87abaf345`. Signature verification and 16 KB alignment passed. Each saver entry is checked against its GitHub release metadata. Preview images are inspected at 384x216; source captures and hashes are recorded in `app/previews/sources.json`.


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
| Fire TV AFTDEC012E, version code 25 | The complete CLI update retained all 44 saved preferences. With general protection Off, the app restored accessibility after an outside disable. Intentional Home Off stayed Off and the Home button opened Amazon; intentional On returned to Fire TV UI. Developer options, ADB, and Wi-Fi ADB remained 1, and deep sleep remained Off. General protection was restored to On. No TV restart was performed. |
| Isolated Android TV emulator, version code 25 | With general protection Off and Home disabled in the system, a profile with no saved Home choice defaulted to saved Home, startup, and override On and restored accessibility. All 37 original preferences were restored afterward. |

Evidence includes the [picker's first rows](../art/screenshots/tv-screensaver-first-tv-1920.png), [lower rows](../art/screenshots/tv-screensaver-lower-tv-1920.png), and [normal Colors dream](../art/screenshots/screensaver-colors-runtime-tv-1920.png). The final catalog's [first entries](../art/screenshots/install-screensavers-tv-1920.png), [last entries](../art/screenshots/install-screensavers-lower-tv-1920.png), and updated [memory display](../art/screenshots/memory-tv-1920.png) were captured on the Android TV emulator. The README contains the remaining interface screenshots.

## Checks still requiring hardware access

A physical TV reboot was initiated after installing the prior nine-entry APK. The TV returned on the network, but its ADB port stopped accepting connections. After the user re-enabled debugging, the prior native Home and protection services and updater VPN were running. Deep sleep was changed to Off and backed up before installing the signed 16-entry version-code-20 APK and rebuilt Home Redirect 1.2.2 helper through the CLI. The device's persistent ADB flag was changed to enabled, but another physical reboot still returned with port 5555 refusing connections. That flag alone did not fix restart persistence.

Version code 21 adds an explicit Fire TV ADB protection choice, global debugging-setting observers, and startup repair before Home setup. Version code 22 makes the app preference authoritative for sleep, defaults missing choices to Off, and reapplies the choice before ADB, Home, preview, and general-protection checks. Factory values cannot replace that preference, and the control service keeps running when general protection is Off. Version code 23 defaults developer/ADB protection to On in code and restores the three global flags independently of general protection. Physical installation and runtime repair were verified after the connection returned. Boot persistence of this correction has not been retested on the physical TV; no further TV reboot was performed. Do not count the reboot request or emulator results as proof of physical Fire TV debugging persistence.

APK updates can briefly restart adbd when enabling Fire TV debugging, and native Home binding can still be settling when the installer checks it. The toolkit now reconnects and retries the read-only helper check after a connection exit, and retries a failed Home restoration once. The complete physical TV update passed with these changes. Three focused local regression cases passed, including preservation of a genuine Home rejection.

Physical Android TV and Google TV devices have not been tested. The Android TV emulator is not a substitute for those vendor implementations. Android 8.1 is the manifest minimum; device checks covered API 30 and 31. Every legacy widget and wallpaper source has not been tested. Play Protect blocked some older mirrored screensaver APKs on the emulator; those restrictions were not bypassed.

The toolkit's 413 automated checks passed on Node 18 and 20 for commit `ce619ea266545749c5e838487ce5265e060c8fe3`. All six existing GitHub checks passed. The SonarCloud quality gate for that exact revision passed with 100% new-code coverage and all security hotspots reviewed. The installer downloads private GitHub assets, strips credentials from redirects, verifies helper upgrades, and preserves existing TV listings permission choices. Fire TV UI 1.0.1 is available as a signed private release. The GUI APK stays in its private repository.
