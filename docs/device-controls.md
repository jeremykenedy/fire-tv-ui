# Device controls and backups

## Remote navigation

Back and Menu dismiss the launcher's Settings panel without moving the app grid behind it. Home routing returns to Fire TV UI with its menus closed. Amazon Settings remains reachable from the gear icon, and Back returns from that screen to Fire TV UI.

## Screensavers

Settings > Screensavers opens the picker directly. The current name appears at the top. Select an installed row to make it active, or choose Preview it to run it temporarily. Returning to the picker restores the prior selection and enabled state, including after an interrupted preview.

Screensaver settings opens the installed dream's `settingsActivity` declared in its dream metadata, falling back to its launcher activity when no dedicated settings screen is declared. Twilight Hearth, Nebula Drift, Vortex Spiral, Signal Rain, Cloud Drift Clock, and Earthbound Orbit each open their own settings screen from this control. The app controls which options and permissions it offers. On Fire TV, System timers lets you set or cancel the TV's one-time Sleep Timer directly from the page, alongside the screensaver delay, Deep Sleep toggle, and Deep Sleep timer. The one-time timer uses Fire TV's own sleep timer service and supports its built-in durations from 5 minutes to 4 hours. The Deep Sleep toggle controls Fire TV's Alexa auto-wake behavior (`secure str.auto_wake_up_enabled`), which determines whether Alexa can wake the TV after it enters deep sleep. The Deep Sleep timer controls the idle countdown before Fire TV enters deep sleep (`secure sleep_timeout`); choose Never to disable that automatic timer. Screensaver delay uses `system screen_off_timeout`.

Deep sleep defaults to Off in the app code. This toggle allows Alexa auto-wake; it does not cancel the separately saved Deep Sleep timer. Set that timer to Never to disable automatic idle sleep. Fire TV UI stores the deep sleep toggle and idle timer separately in persistent preferences and includes both in the TV backup. It reapplies the toggle and timer independently at startup and when Fire OS changes them, even if general settings protection is Off. Enabling general protection on the TV first captures the current selected screensaver, enabled state and idle delay. Both CLI backup receiver operations `protection-on` and `protection-off` also capture these values. Saved sleep and deep-sleep choices remain separate. Updates and restored backups retain the saved app choice.

On Fire TV devices that expose network debugging, Startup and protection includes Keep ADB debugging enabled. It defaults to On in the app code and saves that choice in app preferences. The app checks the developer-options, ADB, and Wi-Fi ADB flags at startup and when they change, independently of general settings protection. Turning this control Off releases those flags without disconnecting the current debugging session. Turn it Off before disabling ADB in Amazon Settings. The choice is included in the TV backup; unsupported Android TV devices do not expose or enable this control.

On Fire TV devices that expose `str.auto_wake_up_enabled`, the Deep Sleep toggle controls Alexa availability while the TV is asleep. It appears only when the setting exists and is included in the local settings guard and backup.

Memory usage shows RAM and internal storage with used/free values and percentage bars. App rows use Android StorageStats for installed files plus app data. Cache is included in data. When usage access is unavailable, the row identifies installed APK size and says data/cache size is unavailable.

Install screensavers contains the reviewed catalog in `app/screensavers.json`. Each included release has a pinned package, version, byte count and SHA-256; new source repositories do not become downloadable entries until their final release artifacts are verified. HTTPS downloads verify the pinned SHA-256, byte count, and package identity. Android confirms installation or removal. Removing the current app requires choosing another screensaver; protection drops a removed selection and disables the dream while keeping timer protection active.

## Launcher idle activation

Appearance > Launcher idle screensaver preserves the existing launcher switch. When enabled, it attempts the currently selected TV screensaver after 90 seconds of launcher inactivity. It does not change the TV selection or system timers. Android 8.1 through 13 uses the exported system screensaver starter. Android 14 and later uses an explicitly reviewed exported preview for Slot Vision, Hearth Presence or Rainforest Cascade. Unsupported selections leave Android's own idle handling active. No other screensaver or launcher clock is substituted. An app preview is distinct from a bound system DreamService.

## Startup and protection

Startup and Home routing defaults to On in the app code. Its saved choice is authoritative, included in backups, and reapplied independently of general settings protection. Enabling adds two accessibility services while preserving unrelated services. Disable saves Off, removes them, and disables startup. Fire OS retains its protected Amazon launcher; Home routing opens Fire TV UI when Amazon Home appears and leaves Amazon Settings reachable. Android TV launcher activities are also supported.

The local guard checks saved Home, deep sleep, and supported Fire TV debugging choices when settings change and every 30 seconds, even with general protection Off. General protection additionally checks screensaver selection, enabled state, and the other timers. Boot and APK-update receivers rebind enabled Home services and launch the app according to its saved Home choice. Fire TV UI's DUMP-protected backup receiver can change the saved timer or screensaver preference together with its system value. Raw settings writes can be reverted by the local guard.

Home Redirect's on-TV screensaver picker sends `com.jeremykenedy.firetv.ui.SELECT_SCREENSAVER` to the `ScreensaverSelectionReceiver`, which requires `WRITE_SECURE_SETTINGS` and updates the active dream and Fire TV UI's protected preference together. If Fire TV UI is not installed, the picker writes the system setting directly and checks the result. Fire TV UI's screensaver unlock option releases its saved selection while retaining the other protected settings; the picker labels that state. Toolkit updater package restrictions are separate from the GUI VPN and are undone with `guard --off`.

Amazon auto-update blocking needs the TV's VPN permission. The tunnel includes only installed known Amazon updater packages and discards their traffic without a server or packet recording. It is registered as always-on when the device allows that setting. An empty updater list is rejected so it cannot route all apps. Streaming, weather, CDNs, and Fire TV UI downloads use their ordinary connection.

The optional Amazon Appstore toggle also blocks manual Appstore downloads. Turn it off when you want Appstore access. Android allows one active VPN; change another always-on VPN before enabling this blocker. The page reports actual active state. Firmware can restrict these controls or add updater packages; future Amazon firmware may need additional support.

## Advanced diagnostics

Open Settings > Advanced > Diagnostics. Refresh report reads the selected screensaver and enabled setting, visible installed DreamService package versions, Android-reported display modes, TV RAM and internal storage, and recent Fire TV UI process exits on Android API 30 or later. The report never repairs, resets or reapplies a setting. Missing values and permission/API restrictions appear as unavailable.

The selected component does not establish which DreamService is currently bound or drawing. Active Dream inspection is available only if Android already grants the app system inspection access; this feature does not request or grant that permission. A current system Dream record is identified separately and does not prove rendered frames. Use Fire TV Toolkit's ADB diagnostics for broader system inspection.

The UI content dimensions describe this app's laid-out card. They do not measure another screensaver's actual surface buffer. Display refresh rate is not rendering FPS, and Android UI frame statistics do not establish OpenGL, Filament or video playback FPS. Those screensaver measurements are explicitly unavailable in this report. Process-exit history is labeled Fire TV UI only and is not a screensaver crash check.

Save report on TV writes a new timestamped `fire-tv-ui-diagnostics-*.txt` file in TV Downloads. It uses the platform's local storage APIs and reports an unavailable destination without changing access permissions. Nothing is uploaded or sent to another app. Reports contain package/version and device-status details; review a saved report before choosing to share it yourself.

## Persistent TV backups

Open Settings > Backup/Restore > Manage TV backups. Save writes `/sdcard/Download/fire-tv-ui-backup.txt` atomically. Restore validates every value before replacing preferences. This Downloads file survives uninstall/reinstall. External wallpaper images and videos must remain accessible separately.

ADB can export a fresh backup or transfer a reviewed Fire TV UI backup. These commands require the app's existing storage setup and Android's shell access; they do not grant new permissions. Every broadcast must finish with `result=0` and `data="ok"` before continuing.

Export from the source TV:

```bash
adb -s SOURCE_TV_IP:5555 shell am broadcast --include-stopped-packages -n com.jeremykenedy.firetv.ui/.BackupReceiver --es operation save
adb -s SOURCE_TV_IP:5555 pull /sdcard/Download/fire-tv-ui-backup.txt /private/path/fire-tv-ui-backup.txt
```

After reviewing the backup, import it on the destination TV:

```bash
adb -s DESTINATION_TV_IP:5555 push /private/path/fire-tv-ui-backup.txt /sdcard/Download/fire-tv-ui-import.txt
adb -s DESTINATION_TV_IP:5555 shell am broadcast --include-stopped-packages -n com.jeremykenedy.firetv.ui/.BackupReceiver --es operation restore --es source import
```

Restore replaces validated launcher preferences. The import file remains separate from the canonical backup. Backups can contain private widget, weather or other user-entered values; keep transferred files private. Android permissions, VPN consent, external media and other apps' settings are not included. Recheck the selected screensaver, timers and Home routing on the destination. Do not uninstall or overwrite a source backup until the destination is verified.

## Privacy

Selected online features and user-initiated downloads retain network access. The app has no analytics, tracking, rating, or project prompts. Home routing and protection operate locally. Backup transfer occurs only when requested.

## Settings guard inspection

With existing Android shell diagnostic access, inspect the running service using:

```bash
adb -s TV_IP:5555 shell dumpsys activity service com.jeremykenedy.firetv.ui/.ProtectionService
```

The service contributes only the protection flag, preview-pending flag, previous/desired/current Dream components and the last local settings-repair result. A successful repair clears a prior failure. Permission failures and other repair failures use fixed descriptions; exception messages and full preferences are never included. VPN update-blocker status is separate. This read-only framework dump does not start a stopped guard, repair settings or grant permissions. Android may add its own service/process metadata around the app's report.
