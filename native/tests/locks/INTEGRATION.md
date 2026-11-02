# Launcher lock integration

This is original source. Maintained legacy Java contains no app/folder lock implementation to port. The reviewed legacy contract is `folder_lock_pin` exclusion from saved setups. The migration owner retains that unverified legacy key and other lock data in private recovery and reports it as unmapped. Do not infer that an old lock or PIN migrated, silently reset it, or ship a production upgrade until that gap is resolved.

Register `com.jeremykenedy.firetv.ui.nativeapp.locks.LockSettingsActivity` as non-exported and expose it from the ordinary Home layout settings category. Main app dispatch calls `LauncherLocks.guard(activity, app.key(), actualLaunchRunnable)`. Long-press controls call `LauncherLocks.showControls(activity, app.key(), app.label, refreshRunnable)`. FolderActivity already uses both interfaces. Folder identities are `folder:` plus the stable folder ID; components retain their exact flattened identity. The guard must cover every actual launcher app dispatch, including future recommendation or shortcut paths intended to honor app locks. The module cannot intercept another app, system Settings, another launcher, deep links dispatched elsewhere, or OS controls.

`LockStore` keeps its versioned bounded state under `noBackupFilesDir/launcher-locks/locks-v1.bin`, outside presentation preferences. Never add this directory or verifier data to portable backups or saved setups. It holds only a random salt, PBKDF2-HMAC-SHA256 verifier (120,000 iterations), target identities and retry counters. PINs are never serialized, logged or included in saved activity state/autofill. Mutable in-memory PIN arrays and derivation passwords are cleared after checks. Android editable inputs and the IME necessarily see user input during entry.

PIN creation is explicit and confirmed twice, with 4 through 12 numeric digits. Changing the PIN or changing/removing locks requires the current PIN. Every locked launch requires a new PIN check; success does not remove the lock or create a persistent unlock session. Five failed attempts introduce a 30-second delay, increasing to a five-minute cap. Counters survive process restarts; backward clock corrections are normalized to avoid indefinite delays. Device owners can change time, clear application data or use another launcher. These controls are launcher-only conveniences, not system security or parental controls. Corrupt/unreadable existing storage blocks guard callbacks rather than behaving as an empty lock list.

## Verification

Run `python3 native/tests/locks/run.py`. It runs isolated host behavior/security regressions with strict host Java lint, then compiles the complete module and native instrumentation against Android API 36, Java source level 8. It accesses no devices, owner data or shared build outputs. Host cases cover absent enrollment, wrong/correct PIN, persisted throttle, bounded delay and clock rollback, changed PIN, independent app/folder locks, salted non-plaintext storage, truncation and invalid identities.

Native proof is prepared, not executed by this worker. After the coordinator integrates and builds the preview, run `python3 native/tests/locks/build_native_test.py`, then on the exclusively leased emulator install `native/tests/locks/.build/locks-proof.apk` and run:

```
adb -s emulator-5582 shell am instrument -w com.jeremykenedy.firetv.ui.locksproof/com.jeremykenedy.firetv.ui.nativeapp.locks.LockInstrumentation
```

The suite requires absent `launcher-locks` storage and refuses to touch existing owner locks. It creates a synthetic PIN through native masked inputs, locks a fixture component through the native control, checks wrong PIN and Back deny the callback, checks correct PIN grants once while retaining the lock, and recreates the activity. Finally it removes only its newly created lock directory and reports restoration. It opens the actual settings destination directly; this does not prove the enclosing Settings route, app/folder long-press route, real target launch or D-pad readability. The coordinator must verify those actual routes, inspect screenshots without a PIN visible, return to preview Home, and remove the proof package. Secure PIN windows intentionally prevent screenshots during input. Physical-TV behavior remains unverified.

No shared APK build, installation, device input, production change, commit, push or release was performed by this lane.
