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
- Settings routes: Appearance > Icons and focus; Home layout > Apps grid; Home layout > Your cards and widgets.
- A real local clock widget with hide/show and shared slot-order storage. Other widgets and channel producers are not yet integrated.
- A per-app icon picker with curated licensed artwork, image import and factory reset. Hold an app tile to open its actions.
- Original topic-based Help with a real focus-settings link and Back navigation.
- Separate app storage and no production permission requests.

## Not a replacement yet

Production Home/key routing, channel cards, the full widget catalog, themes, folders, screensaver administration, private account data, imports, device controls and the existing update flows are not integrated. The full feature and migration inventory is tracked in `working-files/execution-2026-11-01/source-independent-ui.md` in the Fire TV workspace. Do not publish or deploy this review shell as the production UI.

The original release build remains unchanged. Historical attribution and third-party notices remain in the repository.

## Focused verification

```sh
python3 native/tests/focus/run.py
python3 native/tests/layout/run.py
python3 native/tests/appgrid/run.py
```

Native instrumentation is emulator-only and uses separate test packages signed by the preview key. Each stateful check snapshots and restores only the preview preferences it changes. Coordinate one emulator operator at a time; do not run these checks on a production TV.
