# Independent native review target

This target builds from maintained Java and Android resources. It does not read the parent project's supplied launcher APK, decoded resources, Smali adapters or patch scripts.

The application ID is `com.jeremykenedy.firetv.ui.preview`. It is an isolated review app, not a production replacement. It does not register as Home, enable accessibility, change TV settings or import production preferences. No physical TV deployment is part of this foundation.

## Build

Use JDK 21+, Python 3, Android SDK platform 36 and build-tools 36.0.0. Point `ANDROID_HOME` or `ANDROID_SDK_ROOT` to the SDK if it is not at the standard macOS location.

```sh
python3 native/build.py --debug
```

The debug build uses a separate local review key with Android's conventional public debug password. It never reads the production signing key. Omit `--debug` for an unsigned artifact. Outputs are in `native/dist/`; `build-manifest.json` lists every native source/resource input and its digest.

To demonstrate independence, copy only the `native` directory into an empty temporary directory, exclude `.build`, `dist` and `__pycache__`, then run its `build.py`. No parent repository or vendor directory is needed.

## Implemented review surface

- Native app grid using installed Leanback metadata, contained TV banner artwork and stable component launch targets. Favorites, remembered order, hide/show, custom columns and optional horizontal scrolling use preview-local preferences.
- Settings routes: Appearance > Icons and focus; Cards; Home layout > Apps grid; Home layout > Your cards and widgets; Device > Device screens.
- A real local clock and read-only TV recommendation feeds share saved order and visibility. Each channel occupies one card with internal title rotation, global/per-channel timers and individual item hiding/restoration.
- TV listings access is requested only through the Channel Cards access action. Empty, denied and unavailable states remain distinct. Artwork loads asynchronously with size/time limits; title links are restricted to the publishing app and remain distinct from opening that app.
- A per-app icon picker with curated licensed artwork, image import and factory reset. Hold an app tile to open its actions.
- Original topic-based Help with app, focus, layout and Cards links, an original remote diagram, artwork notices and Back/focus restoration.
- Independent focus fill color/opacity, border-only rendering and custom growth retain the existing defaults.
- Capability-checked Factory UI, native Settings and Live TV handoffs. Availability and successful dispatch do not establish physical-device behavior.
- Separate app storage. The Home service and migration foundation compile but are not registered or applied; production settings remain separate.

## Not a replacement yet

Production Home/key routing, real-service playback, series hiding, the full widget catalog, themes, folders, screensaver administration, private account data, complete imports/device controls and existing update flows remain incomplete. Typed migration code is a preservation/recovery foundation, not a proven production upgrade. The full feature and migration inventory is tracked in `working-files/execution-2026-11-01/source-independent-ui.md` in the Fire TV workspace. Do not publish or deploy this review shell as the production UI.

The original release build remains unchanged. Historical attribution and third-party notices remain in the repository.

## Focused verification

```sh
python3 native/tests/focus/run.py
python3 native/tests/layout/run.py
python3 native/tests/appgrid/run.py
```

Native instrumentation is emulator-only and uses separate test packages signed by the preview key. Each stateful check snapshots and restores only the preview preferences it changes. Coordinate one emulator operator at a time; do not run these checks on a production TV.
