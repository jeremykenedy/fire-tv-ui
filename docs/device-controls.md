# Device controls and backups

## Screensavers

Settings > Screensavers opens the picker directly. The current name appears at the top. Select an installed row to make it active, or choose Preview it to run it temporarily. Returning to the picker restores the prior selection and enabled state, including after an interrupted preview.

Screensaver settings opens the installed dream's `settingsActivity` declared in its dream metadata, falling back to its launcher activity when no dedicated settings screen is declared. Twilight Hearth, Nebula Drift, Vortex Spiral, Signal Rain, Cloud Drift Clock, and Earthbound Orbit each open their own settings screen from this control. The app controls which options and permissions it offers. On Fire TV, System timers lets you set or cancel the TV's one-time Sleep Timer directly from the page, alongside the screensaver delay, Deep Sleep toggle, and Deep Sleep timer. The one-time timer uses Fire TV's own sleep timer service and supports its built-in durations from 5 minutes to 4 hours. The Deep Sleep toggle controls Fire TV's Alexa auto-wake behavior (`secure str.auto_wake_up_enabled`), which determines whether Alexa can wake the TV after it enters deep sleep. The Deep Sleep timer controls the idle countdown before Fire TV enters deep sleep (`secure sleep_timeout`); choose Never to disable that automatic timer. Screensaver delay uses `system screen_off_timeout`.

Deep sleep defaults to Off in the app code. Fire TV UI stores the deep sleep toggle and idle timer separately in persistent preferences and includes both in the TV backup. It reapplies the toggle and timer independently at startup and when Fire OS changes them, even if general settings protection is Off. Re-enabling general protection does not capture changed system values. Updates and restored backups retain the saved app choice.

On Fire TV devices that expose network debugging, Startup and protection includes Keep ADB debugging enabled. It defaults to On in the app code and saves that choice in app preferences. The app checks the developer-options, ADB, and Wi-Fi ADB flags at startup and when they change, independently of general settings protection. Turning this control Off releases those flags without disconnecting the current debugging session. Turn it Off before disabling ADB in Amazon Settings. The choice is included in the TV backup; unsupported Android TV devices do not expose or enable this control.

On Fire TV devices that expose `str.auto_wake_up_enabled`, the Deep Sleep toggle controls Alexa availability while the TV is asleep. It appears only when the setting exists and is included in the local settings guard and backup.

Memory usage shows RAM and internal storage with used/free values and percentage bars. App rows use Android StorageStats for installed files plus app data. Cache is included in data. When usage access is unavailable, the row identifies installed APK size and says data/cache size is unavailable.

Install screensavers contains the curated Aerial Views, AndroSaver, Snoozy, Aquarium Live, Aquarium 4K, Jellyfish Drift, Firefly Grove, Neon Corridor, Starfield Drift, Twilight Hearth, Nebula Drift, Vortex Spiral, Signal Rain, Retro Flight, Pipeworks Dream, Aqua Surface Drift, Cloud Drift Clock, and Earthbound Orbit releases. HTTPS downloads verify the pinned SHA-256, byte count, and package identity. Android confirms installation or removal. Removing the current app requires choosing another screensaver; protection drops a removed selection and disables the dream while keeping timer protection active.

## Startup and protection

Startup and Home routing defaults to On in the app code. Its saved choice is authoritative, included in backups, and reapplied independently of general settings protection. Enabling adds two accessibility services while preserving unrelated services. Disable saves Off, removes them, and disables startup. Fire OS retains its protected Amazon launcher; Home routing opens Fire TV UI when Amazon Home appears and leaves Amazon Settings reachable. Android TV launcher activities are also supported.

The local guard checks saved Home, deep sleep, and supported Fire TV debugging choices when settings change and every 30 seconds, even with general protection Off. General protection additionally checks screensaver selection, enabled state, and the other timers. Boot and APK-update receivers rebind enabled Home services and launch the app according to its saved Home choice. Toolkit timer and screensaver commands change both the desired preference and the system value through a receiver protected by Android's DUMP permission.

Home Redirect's on-TV screensaver picker sends `com.jeremykenedy.firetv.ui.SELECT_SCREENSAVER` to the `ScreensaverSelectionReceiver`, which requires `WRITE_SECURE_SETTINGS` and updates the active dream and Fire TV UI's protected preference together. If Fire TV UI is not installed, the picker writes the system setting directly and checks the result. The toolkit's screensaver unlock command leaves selection free while keeping the other settings protected; the picker labels that state. Toolkit updater package restrictions are separate from the GUI VPN and are undone with `guard --off`.

Amazon auto-update blocking needs the TV's VPN permission. The tunnel includes only installed known Amazon updater packages and discards their traffic without a server or packet recording. It is registered as always-on when the device allows that setting. An empty updater list is rejected so it cannot route all apps. Streaming, weather, CDNs, and Fire TV UI downloads use their ordinary connection.

The optional Amazon Appstore toggle also blocks manual Appstore downloads. Turn it off when you want Appstore access. Android allows one active VPN; change another always-on VPN before enabling this blocker. The page reports actual active state. Firmware can restrict these controls or add updater packages; future Amazon firmware may need additional support.

## Persistent TV backups

Open Settings > Backup/Restore > Manage TV backups. Save writes `/sdcard/Download/fire-tv-ui-backup.txt` atomically. Restore validates every value before replacing preferences. This Downloads file survives uninstall/reinstall. External wallpaper images and videos must remain accessible separately.

The CLI sends files to `/sdcard/Download/fire-tv-ui-import.txt`. Restore backup sent from computer applies that file without overwriting the canonical backup. The CLI retrieves fresh backups and saves settings before update or uninstall. Removing the app keeps the backup by default.

```bash
firetv-ui --export=/path/to/new-backup.txt
firetv-ui --file=/path/to/backup.txt
firetv-ui --file=/path/to/backup.txt --restore
firetv-ui --install --setup=tv --home=fire-tv-ui
firetv-ui --uninstall --backup=keep
```

The simple preset supplies the maintained blue layout and app organization. It excludes private weather locations and widget contents. `--setup=keep` preserves the current layout; `--setup=tv` or `--setup=import` restores the user's settings.

## Privacy

Selected online features and user-initiated downloads retain network access. The app has no analytics, tracking, rating, or project prompts. Home routing and protection operate locally. Backup transfer occurs only when requested.
