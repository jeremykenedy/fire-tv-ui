# App Manager remote continuity

Build the isolated preview with `python3 native/build.py --debug`, then build this fixture with `python3 native/tests/appmanager_focus/build_native_test.py`. Install both APKs only on an owned, reserved disposable emulator. The fixture publishes sixteen synthetic TV app aliases to make the manager scrollable.

Before executing `com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppManagerFocusInstrumentation`, capture a durable guard using the included `StateGuardInstrumentation` for all three roots: `shared_prefs,files,no_backup`. Pass the resulting case ID and manifest hash as `state_guard_case_id` and `state_guard_manifest_sha256`. The instrumentation refuses an absent or mismatched guard. Stop target activities, restore, verify and resolve that guard afterward, including after failures.

The proof enters the current Settings > UI settings > Apps route, moves through real controls with D-pad input, and holds Select to open the selected app's options. It verifies saved favorite, hide/show and order actions keep the same app settings control and exact scroll position. Hidden apps remain listed in the manager. It then checks Grid options > Back reveals the fallback first app control, followed by Back restoring the Apps settings route.

Native screenshots are saved to the preview's external files directory as `app-manager-before.png`, `app-manager-after.png`, `app-manager-grid-back.png`, and `app-manager-failure.png` when applicable. Copy and inspect each PNG before restoring the guard, then delete only those fixture files. Keep screenshots and guard records in private delivery evidence rather than this directory.

This proof establishes emulator behavior. Physical remote behavior and viewing distance acceptance need physical TV evidence.
