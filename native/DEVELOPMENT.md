# Independent native review target

This document records the source-preview build and native test workflow. See the [source preview guide](../docs/source-preview.md) for the current alpha scope. Every release requires a verified primary-branch build and publication qualification.

This target builds from maintained Java and Android resources. It does not read the parent project's supplied launcher APK, decoded resources, Smali adapters or patch scripts.

The application ID is `com.jeremykenedy.firetv.ui.preview`. It is an isolated review app, not a production replacement. It does not register as Home, enable accessibility or import production preferences. Device pages expose only explicit capability-dependent actions. No physical TV deployment is part of this foundation.

## Build

Use JDK 21+, Python 3, Android SDK platform 36 and build-tools 36.0.0. Point `ANDROID_HOME` or `ANDROID_SDK_ROOT` to the SDK if it is not at the standard macOS location.

```sh
python3 native/build.py --debug
```

The debug build uses a separate local review key with Android's conventional public debug password. It never reads the production signing key. Omit `--debug` for an unsigned artifact. Outputs are in `native/dist/`; `build-manifest.json` lists every native source/resource input and its digest.

To demonstrate independence, copy only the `native` directory into an empty temporary directory, exclude `.build`, `dist` and `__pycache__`, then run its `build.py`. No parent repository or vendor directory is needed.

## Implemented preview surface

The [source preview guide](../docs/source-preview.md#what-is-included) defines the current alpha feature scope, including app organization, inputs, cards, local widgets, appearance controls, saved setups, portable backup, device pages and original Help. Preview data remains separate from production. New widgets start hidden, private connections require explicit setup and platform capability failures stay visible.

The preview registers no production Home or accessibility service and applies no production preference migration. Supported app/title handoffs do not establish entitlement, exact playback or physical-device behavior.

## Not a replacement yet

The production candidate implements Home/key routing, but complete 1.x parity, production upgrade and recovery behavior, real-service playback and physical-device acceptance remain open. The alpha includes folders, reviewed appearance controls and screensaver administration, as described in the preview guide. Exact-alpha card controls pass, including D-pad custom-timer Save; the tested TV provider supplies item identity without series identity. Typed migration interruption/recovery passes at three write boundaries using an isolated synthetic fixture; this does not prove a production upgrade or corrective-build recovery. The full production parity and migration inventory is maintained in private delivery records. Public preview scope and limits are documented in the [source preview guide](../docs/source-preview.md). Do not publish or deploy this review shell as the production UI.

The default build now produces a source-native production candidate. Use `python3 scripts/build.py --unsigned --output-dir /tmp/fire-tv-production` for an unsigned, nondeployable candidate. Production signing and native acceptance remain separate gates. Historical attribution and third-party notices remain in the repository.

## Focused verification

```sh
python3 native/tests/focus/run.py
python3 native/tests/layout/run.py
python3 native/tests/appgrid/run.py
```

Native instrumentation is emulator-only and uses separate test packages signed by the preview key. Stateful checks require durable capture, restore, independent verification and resolution of their declared app-state roots, plus separate restoration of system and permission state. Coordinate one emulator operator at a time; do not run these checks on a production TV.

## Guarded native timer checks

Run mutating native tests only on an exclusively reserved disposable preview target with no owner accounts, private credentials or Android Keystore aliases. Verify the exact installed APK hash, package, version and signer first. StateGuard refuses nonempty Keystore state; do not clear an owner's accounts or aliases to bypass that refusal. Device/global settings, permissions, URI grants, installed fixtures and foreground restoration require separate operator records and restoration.

The timer builder reads `native/.build/classes` and signs with `native/.build/preview.jks`. `PREVIEW_KEYSTORE` must resolve to that same reviewed local test key; both proof APK certificates must match the installed preview. These are private developer-lab inputs, not distributed release key material. Do not generate a replacement key to bypass a mismatch.

The coordinator must prepare matching candidate classes and preview-signed proof APKs. Set `TARGET_SERIAL` to the reserved disposable target, `GUARD_CASE` to a unique reviewed case ID and `PREVIEW_KEYSTORE` to the reviewed private preview-test keystore path. Never use a production signer. The helper builder requires that keystore explicitly:

```sh
python3 native/tests/state_guard/build_native_test.py --keystore "$PREVIEW_KEYSTORE"
python3 native/tests/timers/build_native_test.py
adb -s "$TARGET_SERIAL" install native/tests/state_guard/.build/state-guard-proof.apk
adb -s "$TARGET_SERIAL" install native/tests/timers/.build/timer-proof.apk
```

Stop the preview and quiesce all other writers before capture. Check every helper result for `status=VERIFIED` and `matches=true`; an ADB exit code alone is insufficient.

```sh
adb -s "$TARGET_SERIAL" shell am force-stop com.jeremykenedy.firetv.ui.preview
adb -s "$TARGET_SERIAL" shell am instrument -w \
  -e operation capture -e case_id "$GUARD_CASE" \
  -e roots shared_prefs,files,no_backup -e quiescent true \
  com.jeremykenedy.firetv.ui.stateguardproof/com.jeremykenedy.firetv.ui.nativeapp.stateguard.StateGuardInstrumentation
```

Set `GUARD_SHA` to the exact 64-character `manifest_sha256` returned by that capture. Keep the before-image private in the target's app storage; do not publish or export its contents. Timer-v4 refuses fixture writes without the completed matching durable guard:

```sh
adb -s "$TARGET_SERIAL" shell am instrument -w \
  -e state_guard_case_id "$GUARD_CASE" \
  -e state_guard_manifest_sha256 "$GUARD_SHA" \
  com.jeremykenedy.firetv.ui.timerproof/com.jeremykenedy.firetv.ui.nativeapp.widget.timers.TimerInstrumentation
```

After success, failure or interruption, stop the target again. Run restore, then an independent stopped verify, then resolve using the same case, roots and manifest hash. Review each result before proceeding; do not resolve a differing or interrupted guard. The suite's in-process `restored=true` is not a substitute for this sequence.

```sh
guard_operation() {
  adb -s "$TARGET_SERIAL" shell am force-stop com.jeremykenedy.firetv.ui.preview
  adb -s "$TARGET_SERIAL" shell am instrument -w \
    -e operation "$1" -e case_id "$GUARD_CASE" \
    -e manifest_sha256 "$GUARD_SHA" \
    -e roots shared_prefs,files,no_backup -e quiescent true \
    com.jeremykenedy.firetv.ui.stateguardproof/com.jeremykenedy.firetv.ui.nativeapp.stateguard.StateGuardInstrumentation
}
guard_operation restore
```

Inspect the restore result, then verify in a separate stopped helper process:

```sh
guard_operation verify
```

Only after independent verification reports `VERIFIED` and `matches=true`, resolve:

```sh
guard_operation resolve
```

If restoration or verification fails, retain the unresolved guard and stop testing. Resolve only after exact file bytes, empty directories and prior absence have been restored. Remove only the test fixtures/packages owned by this run, separately restore system/permission state, and record the final foreground. Never run this recipe against an owner's populated installation.
