# Verification

## Fire TV UI 1.4.1, version code 40

The final signed `ca1b8b5a191fbf5211d38fbb152267c793a37a8fe454db8f4a32ec2150e12446` build passed physical first-page Live TV artwork and placement, actual native GuideActivity launch, Back and injected Home returning to Main. A fresh baseline comparison preserved all 46 portable preferences, nine OS settings and byte-exact external backup/import files, with a healthy live guard and VPN. Native schedules were still loading; channel playback is not claimed. The final Main remained on its first page. The current README and all 39 gallery images loaded at 1920x1080 in browser reviews at 1280px and 320px; the current code 40 rows were visually checked without page overflow. The signed build and 30 local tests passed, with six native opt-in checks skipped.

### Preliminary code 39 helper check

The exact signed `153da5a2fe5feae98fb7a9ba71ed0c4107b53912c4648726c72d1d647168d36e` candidate passed physical Fire OS helper checking: app 1.2.6/code 10 is separate from repository v3.9.1, the APK checksum matches the published helper, and Matches checked release is disabled. OwnTV 5.2.1 matched its exact current TV APK. All 46 portable preferences, nine OS values, external backup/import files, the healthy live guard and VPN were retained. The actual Factory UI tile opened Amazon Home before handback. This does not establish a hardware remote Home press.

The signed build and 29 local tests completed: 23 passed and six native opt-in checks were skipped. API 34 accepted the UI upgrade but Play Protect blocked first installation of the existing helper, so that platform did not verify the helper row. No security bypass was used. Its 14 preexisting UI preferences, nine OS values, three saver exports, stopped guard and storage appop were restored. The published private upgrade handoff remains a separate delivery gate. Third-party checker work is deferred at the user's request.

## Version 1.3.3 selected idle verification

Candidate SHA-256 `027c88d2223fa60a48a86e38f67c9d6d7ef3d77b3bc8a93de57fa2b240d56717` passed the fresh API 34 mapped Slot Vision preview and unsupported Aquarium idle observations. Both ran for 95.3 seconds and restored the prior launcher idle switch, guarded selection and five system values.

Physical API 30 verification used two separate gates. Twenty observations established actual binding of the selected Aquarium DreamService, with native screenshots and Back detaching the Dream. The first harness Home check failed after a force-stop left the accessibility service unbound. After explicitly rebinding Home routing, the separate Screensavers-to-Home check returned to the native launcher. All 47 launcher preferences, eight system values, accessibility membership and the original asleep state matched; the guard service was restarted and verified. Future harnesses must rebind an originally enabled Home service after a force-stop, even when its enabled setting remains present.

The Aquarium surface sample was 23.18 FPS and failed its independent renderer performance gate. Dream binding is not evidence that a screensaver meets its rendering budget. Final release APK and catalog changes require their own artifact verification.

## Automated checks

Run `python3 scripts/build.py --unsigned` and `bash scripts/test.sh`. The build verifies the pinned base and Apktool hashes and requires exact patch anchors. The tests cover component matching, duplicate services, stable ordering, missing active apps, package identity, feature availability, settings wiring, and the feature guide.

## Device checks

### Fire TV UI 1.2.0, version code 33

The signed release was installed on the physical Fire TV AFTDEC012E running Fire OS / Android 11. Clicking HDMI 1, HDMI 2, HDMI 3, HDMI 4, and Composite tiles opened the native passthrough player. Android's active TV input sessions matched HW2, HW3, HW4, HW5, and HW6 respectively. HDMI 4 needed several seconds for the system switch to finish. These checks verify port routing; attached-console playback was not available on every port.

A long-press app menu remained visible after Home before the fix. The updated Home handler resets transient views, including composition-local menus. The physical regression now verifies a real 2.2-second Select hold, Change Icon appearing, Home dismissing the menu, and the clear launcher returning. Repeated Settings Back, Menu, Home, and Theme Mode navigation passed as well.

The final suite passed 38 screensaver Java checks, 17 physical-port mapping checks, and 10 Python tests, including both physical remote tests. The separate emulator catalog settings-route check was skipped. APK alignment and v2/v3 signatures passed. This private repository has no CI workflow.

All 32 shared TV PNGs matched the caption-free repository images byte-for-byte. Each source hash and 1920x1080 PNG dimension was checked, and the ZIP contains the same PNGs. MediaStore entries were published with `is_pending=0`; the Fire TV UI long-press Change Icon picker displayed the new logos. LTvLauncher's Set Custom Banner action opened the Android picker with the same shared images. HDMI 1's existing Xbox One selection was refreshed to the caption-free image, and its wide tile displayed the full centered logo.

The banner's README picture selected all four correct files in a browser. Sixteen real-time frames per animated theme showed motion across the longest 14-second loop. Two frames per reduced-motion theme stayed identical. The only changed banner pixels were inside the television illustration.

APK SHA-256: `289ef2dd0bea11d25a4d86eb6dbdddc06d47c8bcb3839d8629702785bfdcfdb0`.

Physical Android TV, physical Google TV, other Fire TV television models, and a reboot were not tested for this release.

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

Use `FIRE_TV_UI_TEST_SERIAL=emulator-PORT python3 -m unittest tests.test_settings_routes -v` to verify that each bundled saver opens its declared settings activity. The test refuses non-emulator devices and restores the original screensaver selection and enabled state. Set `FIRE_TV_UI_TEST_PACKAGES` to a comma-separated package list for a focused run; omitting it runs every listed app. Unknown packages fail before any settings are changed.

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


## Screensaver catalog 1.3.0 preparation

The isolated Android TV API 31 build passed 38 screensaver checks, 17 input mapping checks, and eight Python artifact and static checks. Native D-pad Back, repeated Back, Menu, Home, Theme Mode dismissal, and Home dismissal of the long-press app menu passed on emulator-5584. The focused settings-route check opened Pipeworks Dream, the slot app under its earlier Reel Atrium label, and Hearth Presence settings through the native picker, then restored the original Backdrop component and enabled value of 1.

The four formerly omitted originals were downloaded from their current public releases, and each APK matched its sidecar checksum, package, DreamService, signing certificate, version and size before catalog inclusion. Native picker routes opened Rain on Glass, Rainforest Cascade, Blue Meridian and Helios Lightfield settings in a focused 38.6-second run, restoring Backdrop and the enabled value of 1. These pins establish download integrity and completeness, not visual acceptance of their older renderers.

The isolated host candidate with SHA-256 `05fecf76fceb978234d2f78ec6aa2290c74bef2be1aa440efe0e6c04d9fff88b` was installed only on the dedicated emulator. The current build passed both menu checks in 64.1 seconds. The long-press fixture now detects and skips a focused Watch Next card before holding Center on an app. Its earlier one-Down assumption selected content after the new row appeared; the product code was unchanged. Ordinary Back, repeated Back, Menu, Home, Theme Mode dismissal and Home dismissal of the app context menu passed.

Those route checks used the installed candidate screensaver builds. Final release verification still requires the signed app release pins, refreshed final picker and catalog captures, and the final host APK. Physical Fire TV verification of this host release is pending coordination with separate active device work. These emulator results do not establish physical Fire TV Home or boot behavior.

## Advanced diagnostics candidate

The signed 1.3.0 candidate SHA-256 `c3efdc30d77a2deb13c8b99ae382c957ae72afc5322ead6d5fdf113795be1413` passed the focused native diagnostics test on the shared Google TV API 34 host-GPU emulator at 1920x1080 on 2026-10-16. Main > Preferences > Advanced > Diagnostics opened through the actual launcher route. The report populated, a timestamped Downloads report was saved and its text read back, and Back returned through Advanced to the launcher. Five secure screensaver/sleep settings read back unchanged. The selected Slot Vision package component and enabled value of 1 were preserved. No additional runtime, accessibility, storage or system-inspection permission was granted.

Capture review found and corrected an initial layout timing problem: dimensions are now sampled on the UI thread after a valid layout. The final report showed 1792x984 UI-card content within the 1920x1080 emulator display. Missing enabled state is separately tested and never becomes disabled by default. The final native test passed in 17.399 seconds, including assertions rejecting raw enabled values and an unlaid-out 0x0 card. Three current captures are in `art/screenshots/advanced-tv-1920.png`, `diagnostics-tv-1920.png` and `diagnostics-report-tv-1920.png`.

The unprivileged emulator correctly showed active Dream inspection as unavailable. Actual screensaver surface buffers and rendered FPS were not measured or inferred. Process-exit data was identified as Fire TV UI only. Physical Fire TV, physical Android TV and physical Google TV checks for Diagnostics remain pending.

## Final 1.3.0 catalog and diagnostics release evidence

The final catalog has 30 pinned downloadable entries. Six previously missing entries now include Rain on Glass, Rainforest Cascade, Blue Meridian, Helios Lightfield, Hearth Presence and Slot Vision. Each published artifact was downloaded and checked against its checksum, package and version. Hearth Presence and Slot Vision use their new physically reviewed 1.0.0 releases; the four older entries establish available download integrity and do not imply acceptance of their older artwork. Pipeworks Dream retains its existing published pin until its separate replacement release passes its gates.

Signed 1.3.0/code 34 candidate 99792b260b83ca632c53fd88289357ba444fd6985dd908b1590e6d51582fcdf0 passed the final actual Google TV API 34 catalog route at 1920x1080. After fresh D-pad focus inspection, Select opened Install screensavers. Scrolling reached Slot Vision and Hearth Presence marked Installed with measured app files and explicit unavailable data/cache statistics. Uninstall was never activated. Back returned to the picker, apps stopped and Home restored; all five current secure selection/enable/activation/timeout values remained unchanged. The [final catalog capture](../art/screenshots/install-slot-hearth-catalog-api34-1920.png) was opened and viewed.

The complete local build suite passes 38 screensaver checks, 17 input checks, diagnostics parsing checks and 14 Python tests, with four native device tests skipped unless their explicit serial environment variables are supplied. These skips are not successes. Earlier focused native diagnostics and settings/navigation evidence is retained above with its exact candidate. The final catalog changes affect data/preview assets, not those verified native Java routes. The only configured remote provider is GitGuardian, which passed each integration commit; no GitHub Actions build is claimed for this private repository.

The public-facing README gallery was viewed through authenticated GitHub at desktop 1280 and mobile 320. Current physical Hearth and native Slot previews load from their recorded source files. The larger launcher gallery still permits internal horizontal scrolling on a narrow phone, while the page itself remains 320 pixels wide. Media overrides were cleared and viewport overrides reset. Physical host 1.3.0 installation and native Fire TV remote behavior are reserved for the coordinated parent device window. Newer standalone Prism, Skycast and Horology apps remain outside this release until their own release gates pass.

## Storage statistics correction in 1.3.2

Physical Fire TV AFTDEC012E, Android 11/API 30, returned an impossible 4.5 KB app size for the independently verified 69,011,424-byte installed Hearth Presence APK. Candidate SHA-256 `6282803c546f71c2127a6e5ff12a5b1b8af1047ff5439b78bfc24b7f5f9f0593` rejects that figure using measured installed base/split APK files. The [actual corrected catalog capture](../art/screenshots/catalog-storage-fire-tv-1.3.2-1920.png), opened and viewed at 1920x1080, shows App files 65.8 MB with unavailable data/cache. Slot Vision retains its valid platform figures. The actual remote route Main > Preferences > Screensavers > Install screensavers and Back/Home passed. All five saved system values and the original asleep state were restored.

The pure helper rejects unknown APK floors, undersized app counters, negative data/cache, cache greater than data, and overflowing totals. Valid totals count app plus data once. Focused JaCoCo coverage is 2/2 executable lines and 14/14 branches; framework storage/file inspection is outside this host boundary and was verified on the affected physical TV.

## Native published Fire IPTV update on Fire OS 30

On October 22, 2026, the 1.4.0/code 38 UI candidate `2c1e7b74f7ba9b9069c9dbc7002c941e03b74815e8844c8e9a5f76ea4fb2bc36` opened Settings > Advanced > Updates on the physical Fire TV. Check for updates found the published Fire IPTV v1.1.1 release. Its download reached Android's normal replacement-install confirmation; confirming there returned to the UI with “Update installed and version verified.” This used the native UI downloader and installer, not an ADB replacement install.

The independently pulled installed Fire IPTV APK matched published SHA-256 `66605a6dd20a1e5039a1115542626a6027701dd800a0e2d991bcbcf86f7e0294`, 87,759,849 bytes, version 1.1.1/code 2 and the existing production signer. The relaunched app retained 1,323 live channels and 226 sports channels. Its existing Picture-in-Picture setting remained off across a force-stop and relaunch. Home returned to Fire TV UI Main with no pinned task. Fire IPTV's retained media session was inactive with playback state NONE(0); the session itself was still present. No playback was started during this check.

All 46 UI preference values and eight saved system settings compared equal afterward. The prior external backup was restored, the settings guard remained running and the UI update-blocking VPN remained present. This does not establish byte-for-byte retention of every Fire IPTV profile or source setting: the observable catalog and PiP state were checked. The private UI row correctly required computer sign-in instead of reporting current without authenticated metadata.

This check also found OwnTV's version-specific multi-APK asset ambiguity. The subsequent fix uses the exact reviewed `OwnTV-{tag}.apk` TV filename, validates the numeric tag, and rejects a mobile decoy in a future-version regression. Its local regression passes; its new native check remains pending. Later source changes add sanitized guard inspection and Prism Clock's published catalog entry and require their own native check before final release.

## 1.4.0 source and gallery preparation

The signed repeat build `d6af9fa086afe3b37469ea61755ea2cabf135da01e09c2c9fd8272d8ce43f7b1` has all 631 non-META-INF APK entries byte-identical to candidate `44057a7d0f67e0aa945e2eebfbef64f1ec52295f454d8e52deb7cf03711bc498`. This includes both DEX payloads, manifest, resources and assets. Archive metadata and signing-container bytes differ, so whole-APK byte reproducibility is not claimed. The production signer remains unchanged. The repeated local suite passed 23 Python tests with six explicit native-environment skips, along with the Java behavior checks.

The private GitHub README and full gallery were reviewed in authenticated Chrome at 1280 and 320 pixels. All three README images and all 30 gallery images loaded at their native 1920-pixel width. The current row was viewed at both widths, and the earlier native gallery was inspected at desktop width. Historical captions that caused narrow-table clipping were shortened; every image then remained within the 320-pixel page. The viewport was reset and the owned tab closed. No account or media preferences changed. Current captures identify the 44057 update check, earlier 2c1 physical native tiles, and accepted Prism physical catalog sample separately. These documentation results do not establish the still-pending hardware Home, OwnTV, guard-dump or Prism catalog-row gates.

## Final 1.4.0 physical functional check

The independently pulled installed UI APK on the new physical Fire TV matches frozen candidate `44057a7d0f67e0aa945e2eebfbef64f1ec52295f454d8e52deb7cf03711bc498`, 8,773,076 bytes and the existing production signer. Its native Updates route freshly checked OwnTV v5.2.1, selected the exact TV artifact and displayed the reviewed `dbc991358d09b6e0e7a40683d63346528f9702efe4358c7f9ae66af266e4ee3c` hash with Matches checked release. No OwnTV data or provider was changed.

The running ProtectionService framework dump reported protection on, no pending preview, matching desired/current Aquarium components and no guard failure. The 31-entry native catalog showed Prism Clock v1.0.0 and installed its public APK through normal Android confirmation. The independently pulled Prism bytes matched `edbf475c4a2b253ccc069adaee8cac2cbba4d16b5579d5299e2e2d1bdaa21fd5`, 127,947 bytes and production signer `193b1650f1e3924088d6084932a4ec5fffe36ae95f6a8cb943bfa10c4bde4788`. Its installed picker thumbnail was viewed. Preview it bound the actual PrismDreamService; the settled physical frame was viewed separately from the initial transition capture.

Back restored Aquarium with preview_pending=false. All 46 exported UI preferences and eight OS settings compared exactly equal afterward. The previous external backup was restored; the live guard and VPN remained present. Normal Home reached Main. A final single Factory UI tile tap opened actual AmazonHome_vNext for the physical-remote follow-up, and the device lease was returned. That hardware-key check remains unverified. The frozen APK is the release artifact; a verified primary rebuild must match all 631 runtime payload entries before its tag is published. This result does not approve rejected screensaver visuals or establish every remaining screensaver requirement as complete.

## 1.9.0 authorized profiles and URI artwork

Signed code56 `f32cbc7a` passed the local functional suite (45 tests, six device-only skips). The new URI artwork loader compiles against API28. On the physical Android9 TV, ordinary READ_TV_LISTINGS consent was accepted during card setup; actual Tubi HTTPS and Netflix same-producer content artwork then appeared. The Netflix card dispatched its original action to the installed Netflix app; this is not a Netflix title-playback claim.

The original Fire TV retained its approved owner connection and added one explicitly authorized unprotected managed profile. Both scopes returned 12 Continue Watching items with no overlap. The native picker and separate profile feed were verified. The incoming card URI did not automatically open the exact episode after profile selection: Plex returned Home, then the matching Continue Watching item and its playback were selected manually. X03 automatic card-to-item handoff is not passed. An owner-authorized server session query confirmed the exact item under the managed profile, playing at 364284ms. No owner PIN was reused and no unrelated profile settings changed. External Resume requests still need separate handling of the installed Plex app's household picker; a picker or generic app launch is not accepted as automatic resume proof.

Original-TV cleanup retained all 51 current portable preferences and 12 OS values exactly, restored external backup/import bytes and left Main with the live protection service. Requested encrypted Plex connections and feed choices were intentionally retained. The API34 documentation fixture used safe sample names and verified offline preview credits scrolling/Back. Its dummy encrypted connection and package were removed, UI14/OS11/external files/storage appop/guardStopped were restored. The fixture did not establish the AES alias's prior presence; the alias was preserved rather than deleting a potentially preexisting key.

Final code 57 `be47742d` checked the public UI release through the native Updates screen. It verified 1.8.0/code 53 from its published checksum and current signer, showed the actual Android version separately from the repository tag, and disabled replacing the newer installed 1.9.0 build. No installer was opened. UI14/OS11, external backup/import bytes and existence, storage appop default, guardStopped and prior Home were restored.

Final code 57 `be47742d` also passed the physical Android 9/API 28 smoke: Main was visible without a Dream overlay, Tubi and Spotify artwork rendered, and 49 current portable preferences, 10 recorded OS values and external backup/import bytes were retained exactly. The Netflix URI loader is unchanged from the separately verified code 56 physical check.


## 1.9.1 artwork rotation regression

Published 1.9.0 reproduced an actual native edge case: Manual mode held its artwork for 35 seconds, but changing to Every 5 seconds through the native Featured cards picker and returning with Back did not restart the slideshow within 12 seconds. The selected source and six available sample cards were unchanged.

Candidate 1.9.1/code 59 `6109bc40` refreshes a model-owned Compose rotation setting on Main resume and uses it as an effect key. This cancels the old suspended Manual timer and starts the selected interval without recreating Main or resetting source, card index, order or focus. The same native picker/Back path then resumed at 5.004 seconds. Manual remained unchanged for 35 seconds. Steady observed intervals were 5.007, 8.014, 14.996 and 30.005 seconds for the 5/8/15/30 choices.

These are API 34 emulator observations of the actual production slideshow index, sampled every 25ms, with owned local sample cards and a temporary in-memory source descriptor. They are not physical-TV timing proof. The fixture's provider IDs, artwork and ownership record were removed; all 14 portable preferences, 11 OS values, external backup bytes, storage appop default and stopped guard were restored, and the emulator remained alive at its original Home. An unrelated preexisting fixture package was preserved.


Final integrated UI 1.9.1/code 59 `2ac87445` passed API 34 native catalog and picker checks with the published Cloud 2.0 and Blue 1.0.1 assets. Both current preview images appeared while Slot Vision remained selected. The offline license reader opened, scrolled with the D-pad and returned with Back. Temporary Cloud installation was removed to restore its original absence. UI14/OS11/external backup bytes/storage appop/actual accessibility state/guardStopped all matched the fresh baseline; prior Google TV Home and the live emulator were retained. The slideshow/base DEX is byte-identical to the separately native-tested rotation-fix candidate. No physical timing, automatic Plex resume or new-episode subtitle claim is added.

## 1.9.2 Fire OS preview routing

The actual API 28 Fire TV SystemUI APK has no Somnambulator activity. The previous host action attempted that nonexistent component after selecting the requested Dream, then restored the original selection when launch failed. The correction uses reviewed callable app previews before any system selection write. It checks package ownership, enabled/exported state and required activity permissions.

The final code 62 APK is `3130bef033b6f076968be955a6c78801012f9cc7bc1dd6812d0b82a433d588f2`. Its controls routes passed actual remote navigation for all eight apps with private or missing previews: AndroSaver, Aquarium 4K, Aquarium Live, Contour Flow, Nebula Drift, Skyburst Nocturne, Snoozy and Split-Flap Drift. Their actual in-app preview controls opened and Back returned to the picker. Contour, Nebula and Split-Flap explicitly open launchers containing their full-screen Preview button; their separate settings screens do not contain that button. The native device check reported no restoration errors for UI preferences, OS values, storage appops or Home/accessibility health.

The unchanged direct route visibly rendered Cloud Drift Clock and Blue Meridian for 40 seconds each on code 61, then returned with Black Screen still selected and the enabled value unchanged. Aerial TestActivity opened and returned, but its captured video area was black with only its clock/caption visible. This is routing proof, not visible video playback proof. AndroSaver's configured slideshow reported No images found. Media/source configuration and separate visual defects are not silently counted as successful content playback. The final code 62 sweep opened all 25 reviewed direct routes and returned with state preserved. Black Screen is intentionally black. Aerial and Jellyfish Drift V2 black captures are not counted as visible video playback proof.

The signed build passes the Java functional checks and 47 Python tests, with six native-dependent tests skipped. The controls-vs-settings regression tests exercise explicit launcher overrides, permission and cross-package rejection, zero secure-setting writes on app routes, and restoration/retry after system fallback failure. No remote GitHub Actions workflow is configured in the current repository; third-party checker work remains deferred.

The layout-only final code 63 APK is `f6e859da0ae7484c6590c87c49ea57cb377767c4c9b956f9af1fcda32e5a3c70`. Its wider, single-line action buttons passed the physical picker check. Direct Blue and corrected Contour controls/full-screen Preview/Back passed again, preserving UI preferences, OS values, storage appops and actual Home/accessibility health. The current [picker and scene captures](screenshots.md#fire-tv-ui-192-code-63) were inspected after capture. All route mappings and other runtime classes are unchanged from the completed code 62 sweep.
