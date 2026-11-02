# Sun and moon integration

Provider: `com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy.AstronomyWidgetProvider`, public `ITEM` identity `widget:sun-moon`. It inherits default-hidden behavior. Register through the existing layout provider registry only after module compilation passes. No original local clock behavior changes.

Preferences: file `native_astronomy`, key `location`, versioned tab-delimited manual latitude/longitude/IANA timezone/label. Missing means SETUP_REQUIRED; invalid data and unsupported device date mean UNAVAILABLE. The native form saves only after validation and offers explicit Clear location. These preferences contain manually entered location information. The coordinator must deliberately account for that when mapping backup/saved setups and privacy descriptions.

READY does not imply every date has a sunrise and sunset. The rendered solar result separately distinguishes two events, polar day, polar night and polar transition with unavailable events. Source calculations, license details, sample sources and bounded validation results are documented in `PROVENANCE.md`.

## Local checks completed

- `python3 native/tests/astronomy/run.py`: published Schlyter solar/lunar numeric examples, finite/range/zone/version validation, DST 23/25-hour days, six stored USNO cases. No network is used by this runner.
- `python3 native/tests/astronomy/verify_sdk.py`: all owned source plus native instrumentation compile against SDK 36 in an isolated temporary directory using the actual WidgetProvider/LayoutItem contracts.
- No shared APK build, device action, installation, commit or publication occurred in this lane.

## Prepared native proof, not executed

After the combined preview is built, `python3 native/tests/astronomy/build_native_test.py` builds the owned test APK against the coordinator's classes. Under an exclusive emulator lease, install it using the explicit emulator serial, then run:

```
adb -s SERIAL shell am instrument -w com.jeremykenedy.firetv.ui.astronomyproof/com.jeremykenedy.firetv.ui.nativeapp.widget.astronomy.AstronomyInstrumentation
```

The test checks setup/ready/unavailable, real solar/lunar text, saved location reloading, idempotent start/stop, and read-back restoration of its owned preference key. It does not manipulate the device clock or location permissions. Record the combined preview APK hash and test output with the coordinator's evidence.

Native acceptance still required through real routes: Settings > Widgets > Sun and moon > configure; manually enter sample coordinates/label/timezone, Save, explicitly Show, return Home and inspect values, focus, long labels and legibility. Exercise invalid coordinates/zone, Cancel, Clear, Back, resume and activity recreation. Check system 12/24-hour changes and locale formatting. Preserve and restore relevant preferences. Deterministic host fixtures establish polar and DST math without changing any physical TV's wall clock. No emulator route, rendered screenshot or physical-TV behavior is claimed yet.
