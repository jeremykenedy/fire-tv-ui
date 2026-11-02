# Device routing integration

Original source module and native test preparation only. No device, emulator, shared APK build, application manifest, Main or Settings changes were performed for this lane.

## Native destinations

Use `DeviceRoutes.resolve(context, Destination.FACTORY_UI | SYSTEM_SETTINGS | LIVE_TV)` to decide whether a tile/action is available. Resolve off the UI thread when adding tiles to inventory. Launch using `DeviceRoutes.open(context, destination)` so explicit handoff suppression is armed before Android can display an intermediate Home window. Treat STARTED as an intent dispatch result, never as visible native proof. UNAVAILABLE and FAILED need an actionable user-visible message.

Factory UI selects an enabled, exported, permitted system Home activity, preferring Android's resolved activity when it is a real listed candidate. It excludes this app and never opens a resolver placeholder. Settings uses the resolved system ACTION_SETTINGS activity. Live TV checks the owned legacy public Amazon guide URI and then system TV packages com.android.tv/com.google.android.tv, with unsupported state when none resolve. These candidates are capabilities, not claims of universal support. No privileged TV-guide activity is invoked.

Main must call `DeviceRoutes.launcherVisibility(true)` in onResume and false in onPause. This keeps repeated Home from opening a duplicate visible activity. The service additionally observes Main window visibility. Keep service and activities in the same process so handoff state is shared.

## Preview

Keep the current preview manifest free of HOME and accessibility declarations. Source compiles without service registration. Even if accidentally registered, Home routing is hard-disabled for every package except com.jeremykenedy.firetv.ui. Native destination handoffs can be reviewed without taking Home.

## Device settings screen

Register `.nativeapp.device.DeviceSettingsActivity` as non-exported and route Settings -> Device -> Device screens to it. Capability checks load asynchronously. All three actions remain remote-focusable; unavailable actions explain their state. The screen retains its selected action across return/recreation. No Home setup action or service registration is included.

`DeviceInstrumentation` is prepared for the exclusive native operator. After the combined source build, run `python3 native/tests/device/build_native_test.py`, install its device-proof.apk and execute the instrumentation. It enters Settings -> Device -> Device screens by remote Select, opens System settings, checks the actual active external package through UiAutomation, presses Back and checks the retained action/opener focus. It checks all three availability states and explains unavailable destinations. It has been SDK-compiled but has not been run on a native target by this worker.

## Production integration, after migration and native validation gates

HOLD: do not integrate or register the production window fallback in this batch. Its process-local handoff exemption does not survive service/process restart. This lane deliberately leaves the service dormant until restart-safe behavior and native proof are complete.

Preserve the historical component identity with newly authored source at `com.jeremykenedy.firetv.ui.Hra` extending `com.jeremykenedy.firetv.ui.nativeapp.device.HomeKeyService`. This thin compatibility class is still needed; it was deliberately not created outside this lane's ownership. Do not reuse inherited bytecode. Keep the service in the default app process.

The production-only manifest service needs android:name=".Hra", android:exported="true", android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE", an android.accessibilityservice.AccessibilityService intent filter, and android.accessibilityservice metadata referencing an original XML resource. That XML needs accessibilityEventTypes="typeWindowStateChanged", accessibilityFeedbackType="feedbackGeneric", accessibilityFlags="flagRequestFilterKeyEvents", canRequestFilterKeyEvents="true", canRetrieveWindowContent="false" and a readable description string. Do not request gesture/window-content capabilities.

Add explicit user settings for launcher_prefs booleans source_home_keys_enabled and source_home_window_fallback_enabled, absent meaning false. Existing legacy preferences must be deliberately mapped by the migration owner, preserving explicit choices; these new keys do not claim legacy-setting parity. Never write secure accessibility/default-Home settings or enable the service automatically. Provide an ordinary resolved ACTION_ACCESSIBILITY_SETTINGS handoff only from an explicit setup action, with the user's OS consent flow.

Production Main needs singleTask or another verified single-instance Home lifecycle, handling incoming Home in onNewIntent without recreating the UI, and the production-only ACTION_MAIN/CATEGORY_HOME/CATEGORY_DEFAULT filter when the production migration is authorized. These are integration requirements, not changes made by this module. The service uses an explicit Main intent with NEW_TASK/CLEAR_TOP/SINGLE_TOP. Verify Main's lifecycle and transient menus before production use.

Package visibility queries must cover ACTION_MAIN/CATEGORY_HOME, ACTION_SETTINGS, ACTION_VIEW with scheme livetv and package com.amazon.tv.livetv; include system TV packages com.android.tv and com.google.android.tv for the fallback launch checks. Do not request QUERY_ALL_PACKAGES for this feature. Missing visibility produces UNAVAILABLE safely.

## Behavior and limits

Only Home is handled. D-pad, Back, voice, power, volume and branded app keys pass through. Successful Home launches consume matching down/up, including repeats, and suppress duplicate requests for 750ms. Missing or failed launches pass Home through. A 600ms window fallback is separately opt-in and reacts only to the exactly resolved system Home component; unsupported OEM subclasses remain unsupported rather than guessed.

Explicit handoffs remain exempt until a new Home press or Main becomes visible. This prevents a late Settings/Factory UI window from being stolen after a short arbitrary timeout. Service/process restart loses this process-local exemption; persistence/restart behavior needs native validation before enabling the production fallback. No boot receiver or service auto-enablement exists here.

Run `python3 native/tests/device/run.py`: account-free policy checks plus Android SDK 36 compilation into a temporary directory. This proves preview/opt-in gating, handoff persistence, repeated-Home suppression, failed-handoff recovery and independently compiled original Java. It does not prove resolver output, key delivery, native Settings/Live TV appearance, Home from Dream or physical remote behavior. Reserve an exclusive native target for those integration checks later; never reboot.

Official API references: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService and https://developer.android.com/reference/android/provider/Settings .
