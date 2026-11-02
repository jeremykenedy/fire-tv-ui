# World clocks integration

Provider: `com.jeremykenedy.firetv.ui.nativeapp.widget.worldclock.WorldClockWidgetProvider`.
Slot: `HomeSlots.WORLD_CLOCKS`, key `widget:world-clocks`. Layout owner registered it and keeps new providers hidden until explicitly shown. No original local clock behavior changes.

Owned source lives in `nativeapp/widget/worldclock`. Preferences use `native_world_clocks`, key `clocks`, versioned bounded string. Include this preference file in the coordinator's backup/saved-setup mapping if those mappings enumerate files. It contains only zone IDs and chosen labels, no accounts or external data. Fresh state has no sample clocks. Invalid stored configuration reports UNAVAILABLE and requires an explicit Clear clocks action in configuration.

Native settings use region and zone lists from the platform's timezone database, followed by an editable label. Each clock offers label editing, timezone changes, remove, move earlier/later. At most six unique exact zone IDs, labels 1 to 40 characters. Timezone aliases supplied by the platform are accepted, including IANA links; invalid IDs never fall back to GMT. System locale and 12/24-hour preference choose display patterns. Each row shows the zone's own date across rollover. The platform timezone database supplies daylight-saving rules.

Refresh registers only system time/minute/date/timezone/locale/configuration broadcasts while started. Start reloads saved configuration and is idempotent; stop and view detach unregister. No network, asynchronous worker or delayed callbacks.

## Completed local checks

- `python3 native/tests/worldclock/run.py`: passed spring and autumn Los Angeles DST transitions, date rollover, fractional offset, 12-hour formatting, persisted order/rename/removal, size/label/zone/duplicate/corruption bounds.
- `python3 native/tests/worldclock/verify_sdk.py`: provider, settings, view, preferences, model and instrumentation compile against Android SDK 36 in a temporary directory. It uses the actual WidgetProvider/LayoutItem contract and a minimal HomeSlots constant to avoid compiling other workers' unfinished sources. This is a module compilation check, not a combined APK build.
- Diff whitespace check passed. No shared build, device input, install, commit or publication performed in this lane.

## Prepared native proof, not executed

After the coordinator builds the combined preview, run `python3 native/tests/worldclock/build_native_test.py`. Under an exclusive emulator lease, install that test APK using the explicit emulator serial and execute:

```
adb -s SERIAL shell am instrument -w com.jeremykenedy.firetv.ui.worldclockproof/com.jeremykenedy.firetv.ui.nativeapp.widget.worldclock.WorldClockInstrumentation
```

The instrumentation checks explicit EMPTY/READY/UNAVAILABLE states, real rendered labels/zones, saved label/order reloading, repeat start/stop, and corrupt-zone handling. It restores and reads back only its owned clock preference key. It does not clear layout state or replace the Home screen. Capture exact combined APK hash and test result under the coordinator's evidence directory.

Native route and visual acceptance remain required: open Widgets through the real Settings route, configure World clocks, add two zones with a native remote, rename/reorder/remove, Show explicitly, return Home, inspect full labels/times/date layout and focus, then Back and reopen after activity recreation. Check system 12/24-hour changes, locale layout, six rows and long labels. Preserve and restore relevant owner settings. Host tests prove deterministic DST math; do not change a physical TV's wall clock to force a DST test. No emulator or physical-TV behavior is claimed yet.
