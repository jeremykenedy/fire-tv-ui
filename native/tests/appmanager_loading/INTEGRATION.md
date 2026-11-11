# App Manager discovery state

Build the preview and this fixture with `python3 native/build.py --debug` and `python3 native/tests/appmanager_loading/build_native_test.py`. Use an owned, reserved disposable emulator only. The fixture includes sixteen synthetic TV apps and the existing App Manager navigation regression.

Capture a durable guard for `shared_prefs,files,no_backup` using the fixture's `StateGuardInstrumentation`. Pass its case ID and manifest hash as `state_guard_case_id` and `state_guard_manifest_sha256` to `com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppManagerLoadingInstrumentation`. Restore, verify and resolve the guard after every run, including failures.

The instrumentation enters Settings > UI settings > Apps. It intercepts activity creation and queues a latch on the existing executor before discovery is submitted. It verifies the native loading explanation, then releases discovery and checks the real installed list. A second held discovery verifies current views, focus and scroll remain intact while refresh is pending. Successful completion keeps the established rendering behavior. A synthetic completed-empty presentation verifies the genuine empty explanation remains available. There are no production test hooks.

The fixture writes uniquely named `app-manager-*.png` screenshots to the preview's external files directory. Copy and inspect the exact fixture PNGs privately before guard restoration and delete only those owned files. Keep guard records and screenshots in private delivery evidence.

The included `AppManagerFocusInstrumentation` exercises the previously qualified favorite, visibility, reorder and Back paths against the same candidate. This establishes emulator behavior only.
