# Breathing break widget

Provider: `widget.breathing.BreathingWidgetProvider`. Stable slot: `widget:breathing-break`. Size: 360 by 280dp. It inherits hidden default visibility. No manifest entry, external data, account, audio, alarm or power change is needed.

The optional exercise starts only through the native action menu. Default cadence is inhale four seconds, no hold, exhale six seconds, no rest, for two minutes. Native configuration bounds inhale/exhale to 2 through 12 seconds, holds to 0 through 8 seconds and duration to 30 through 1800 seconds. Saving cadence resets the exercise and leaves it paused. Zero length holds are skipped. Start, pause, resume, reset and start again operate on elapsed realtime.

The source Canvas view uses a muted teal circle and smooth cosine interpolation. Reduced motion keeps the circle still and updates phase text. It follows the same phase timing. There is no sound output. No medical benefit is claimed.

When Home pauses or its slot leaves the active viewport, the provider pauses progress, removes callbacks, unregisters its preference listener and saves progress. Returning does not resume automatically. Repeated onStart calls reload settings without interrupting a running exercise. Process restart ignores a saved running flag from another process and stays paused at the last saved transition. Private storage is `native_breathing_break`; no background session or cross-device running-state portability is claimed.

`python3 native/tests/breathing/run.py` passes focused cadence/phase-boundary, zero-hold, pause/resume, completion, reset and bounds checks, and compiles all native source plus prepared instrumentation against Android 36 in a temporary directory.

Native proof is prepared, unexecuted by this worker. After the coordinator's combined build, run `python3 native/tests/breathing/build_native_test.py`, install `breathing-proof.apk` on the exact reserved emulator and run `com.jeremykenedy.firetv.ui.breathingproof/com.jeremykenedy.firetv.ui.nativeapp.widget.breathing.BreathingInstrumentation`. The suite uses the actual Home slot and native dialogs, checks lifecycle pause by opening native Settings, confirms paused return and explicit resume/reset, then saves reduced motion. It snapshots/restores both exercise and layout preferences. Inspect the restored flag.

The native operator still needs to observe the animation and reduced-motion behavior, D-pad form navigation, focus restoration, text readability and no clipping in the mixed row. Native runtime execution and owner visual acceptance remain open. This worker did not install, operate devices, write shared builds, commit or release.
