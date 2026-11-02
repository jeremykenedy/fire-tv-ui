# Build and source layout

The default build creates a source-native production-package candidate from the maintained Java and Android resources in `native/`. It does not decode, patch, or package the supplied launcher APK. The candidate is not a released production upgrade: migration, parity, signing, and device acceptance remain separate gates. Stable Fire TV UI 1.17.0 and Source Preview 2.0.0-alpha.2 remain the published builds described in the README and [preview guide](source-preview.md).

| Path | Purpose |
| --- | --- |
| `scripts/build.py` | Entry point for the source-native production candidate. |
| `native/src/`, `native/res/` | Maintained launcher source and resources shared with the preview. |
| `native/production-src/`, `native/production-res/` | Production package compatibility components and resources. |
| `native/production-build.py`, `native/production-release.json` | Candidate build, identity and signing checks. |
| `native/tests/` | Source-native host and device test harnesses. |
| `app/`, `build.json` | Historical owned compatibility source and build records. The inherited APK and patch entrypoints are retained only in Git history. |

## Build requirements

Install JDK 21, Python 3.10 or newer, Android SDK platform 36, and build-tools 36.0.0. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` to the SDK; the default macOS location is `~/Library/Android/sdk`.

```sh
sdkmanager 'platforms;android-36' 'build-tools;36.0.0'
python3 scripts/build.py --unsigned --output-dir /tmp/fire-tv-production
```

The output directory must be empty. The unsigned APK is a nondeployable candidate, not an installer or verified release. `production-build-manifest.json` records the source/resource and toolchain hashes, compiled classes and APK digest. The build compiles the native resources with `aapt2`, Java with `javac`, and classes with `d8`; it packages and aligns the resulting APK without the inherited launcher binary.

The default invocation without `--unsigned` requires the existing production key and verifies the resulting signer against the configured production identity. It does not generate a key. The default key and password-file paths are `~/.android/keys/fire-tv-ui.jks` and `~/.android/keys/fire-tv-ui.pass`; `FIRE_TV_UI_KEYSTORE`, `FIRE_TV_UI_PASSWORD_FILE`, and `FIRE_TV_UI_KEY_ALIAS` can select existing credentials. Never commit these files. A signed build alone does not establish upgrade safety or authorize deployment.

The isolated preview has a separate package and signing workflow; see [Independent native review target](../native/DEVELOPMENT.md). CI runs the source build and host checks, while native and physical-device behavior require their own verification.

## Historical attribution

The prior stable 1.x build was a modification of the supplied AT4K launcher 1.2, whose APK SHA-256 is `ac34c0abf1ceee5d5133dc3e99bde9173a00c5a16132852507ed23c0fbd41341`. Its original Gradle, Kotlin and Compose source was not supplied; the historical build reconstructed the binary with Apktool and Smali adapters. Those facts, the former inherited APK and its patching tools remain in Git history. The source-native build has its own maintained implementation. The project MIT license covers its applicable original work; third-party artwork and other components retain their documented terms. See `native/icons/NOTICE.txt` and `native/icons/provenance.json` for the curated icon catalog.
