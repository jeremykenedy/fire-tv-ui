# Build and source layout

This repository maintains a reproducible modification of the supplied launcher APK. The original Gradle, Kotlin, and Compose source project was not provided. The existing launcher implementation is reconstructed as Smali during the build; the added screensaver screen and device controls are maintained as Java source.

| Path | Purpose |
| --- | --- |
| `vendor/launcher-base.apk` | Unmodified supplied launcher 1.2, pinned by SHA-256. |
| `build.json` | App identity, version, and build-tool checksums. |
| `scripts/patch_launcher.py` | Checked changes to package identity, settings, Help, links, and default feature state. |
| `app/src/` | Screensaver discovery, device settings, picker, and Fire TV Home redirection. |
| `app/smali/` | Small adapters between the existing Compose settings and the new picker. |
| `app/previews/` | Screensaver samples, component index, and source records. |
| `art/` | New icon, TV banner, and verified screenshots. |
| `tests/` | JVM rules and build checks. |
| `skills/add-fire-tv-screensaver/` | Reusable workflow for future screensaver repos and CLI registration. |

## Build requirements

Install JDK 21, Python 3.10 or newer, Android SDK platform 36, and build-tools 36.0.0. Point `ANDROID_HOME` or `ANDROID_SDK_ROOT` at your Android SDK. The default macOS SDK location is `~/Library/Android/sdk`.

```bash
sdkmanager 'platforms;android-36' 'build-tools;36.0.0'
python3 scripts/build.py
```

The build downloads Apktool 3.0.3 from its official release and verifies its SHA-256 before executing it. The base APK is also verified before decoding. Patch anchors must match exactly; an unknown base fails instead of producing a partially patched package.

The original resource IDs stay defined in the decoded resource table. Apktool rebuilds their XML and assets with the new branding. The added Java code is packaged in `classes2.dex`; the original launcher classes and small bridge are in `classes.dex`. Obsolete compiled dex profiles are removed.

## Signing

The first local signed build creates `~/.android/keys/fire-tv-ui.jks` and `~/.android/keys/fire-tv-ui.pass`, both excluded from version control. Keep these files backed up. Future updates must use the same key.

To use an existing key:

```bash
FIRE_TV_UI_KEYSTORE=/path/to/signing.jks \
FIRE_TV_UI_PASSWORD_FILE=/path/to/signing.pass \
FIRE_TV_UI_KEY_ALIAS=your-alias \
python3 scripts/build.py
```

The password file contains the keystore password on one line. An explicitly selected missing key fails the build.

For unsigned review builds:

```bash
python3 scripts/build.py --unsigned
```

Unsigned APKs must be signed before installation. The release signing key stays outside the repository. Verification runs locally; this repository has no CI workflow.

## Icon bundle

Build output also includes `fire-tv-ui-icons-VERSION.zip` and its SHA-256 sidecar. The ZIP contains the 32 pre-rendered PNGs, source catalog, and attribution notice. The APK installer copies and publishes these images to shared TV storage. See [HD app and input icons](icons.md) for standalone installation and optional vector regeneration.

## Source attribution

The supplied base is the original AT4K launcher 1.2. Its file SHA-256 is `ac34c0abf1ceee5d5133dc3e99bde9173a00c5a16132852507ed23c0fbd41341`. Original component credits remain in the app's Help section. This project's MIT license covers its maintained changes; bundled third-party components retain their respective licenses.
