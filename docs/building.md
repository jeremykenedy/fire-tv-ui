# Build and source layout

The default build creates a source-native production-package candidate from the maintained Java and Android resources in `native/`. It does not decode, patch, or package the supplied launcher APK. The candidate is not a released production upgrade: migration, parity, signing, and device acceptance remain separate gates. Stable Fire TV UI 1.18.0/code 80 is the latest stable release. The separate independent production candidate reserves code 81 and is not the 1.18.0 maintenance build. The current preview configuration targets 2.0.0-alpha.3; see the [preview guide](source-preview.md) for release availability and scope.

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

## Source preview release build

`native/preview-release.json` defines alpha.3, version code 4, package `com.jeremykenedy.firetv.ui.preview`, minimum API 27 and target API 36. Its maintained signing certificate is `c64ca640cab78e75fd52a6bb43bcbf145aa1b951f0c719b471df241bf70669c6`. The preview does not use the production key. Do not change manifest version fields; the preview builder applies the configured version.

Run these checks from a clean isolated checkout so existing local compiled classes are not replaced:

```sh
python3 native/build.py --release-config native/preview-release.json \
  --unsigned --output-dir "$PREVIEW_BUILD_OUT/unsigned"
python3 native/tests/run_host_checks.py \
  --classes-dir "$PREVIEW_BUILD_OUT/unsigned/.build/classes"
```

Set `PREVIEW_BUILD_OUT` to an isolated output directory before running the commands. For a signed release candidate, supply the existing maintained preview keystore path through `PREVIEW_RELEASE_KEYSTORE` and its password through `PREVIEW_RELEASE_PASSWORD` in the environment. Do not print or commit credential values. If the key needs a separate password, supply its environment-variable name with `--key-pass-env`.

```sh
python3 native/build.py --release-config native/preview-release.json \
  --output-dir "$PREVIEW_BUILD_OUT/signed" \
  --keystore "$PREVIEW_RELEASE_KEYSTORE" \
  --ks-pass-env PREVIEW_RELEASE_PASSWORD
```

The builder verifies the actual signing certificate and APK manifest. It produces the versioned APK, its `.apk.sha256` checksum and `release-artifact.json`, which records the source inputs, commit, toolchain and artifact identity. A default `--debug` build uses a lab key and is not a substitute for this release build.

Publish only from the verified remote `main` tip with a matching semantic version tag. Both CI workflows, final native checks, current screenshots and the declared installation/update path must be reviewed before publication. Record any unqualified upgrade path explicitly. The [alpha.3 release notes](releases/v2.0.0-alpha.3.md) describe the published preview and its verification limits. A signed production-package candidate remains a separate, unqualified release path.

## Stable maintenance recipes

The 1.18.0 and historical 1.17.1 maintenance recipes use the retained stable implementation. It is separate from the default native build and does not complete the independent replacement.

With Python 3.12+, JDK 21 and the Android tools listed above, fetch the public Git history and reconstruct the pinned 1.17.0 source plus the reviewed repair:

```sh
python3 maintenance/build_repair.py --output-dir /tmp/fire-tv-ui-1.18.0

# Reproduce the published historical repair explicitly.
python3 maintenance/build_repair.py --version 1.17.1 --output-dir /tmp/fire-tv-ui-1.17.1
```

The recipe defaults to 1.18.0, version code 80; `--version 1.17.1` selects the historical code 79 recipe. The 1.18.0 metadata pins the reviewed stable source and repair patch. Its unsigned artifact is `fire-tv-ui-1.18.0-unsigned.apk`. Native controls and a direct production-signed 1.17.1 upgrade were verified on Android TV API 34; see the [release verification](releases/v1.18.0.md#verification-and-limits). Neither recipe is the independent source-native replacement.

Choose a new output directory outside the checkout. The recipe verifies the base commit, patch, complete source tree and launcher base, builds the unsigned APK, then runs the maintenance checks. It records source, toolchain and artifact hashes in `build-provenance.json`. Signing requires the existing production certificate. The recipe never signs or installs an APK. The default source-native builder remains unchanged.

## Historical attribution

The prior stable 1.x build was a modification of the supplied AT4K launcher 1.2, whose APK SHA-256 is `ac34c0abf1ceee5d5133dc3e99bde9173a00c5a16132852507ed23c0fbd41341`. Its original Gradle, Kotlin and Compose source was not supplied; the historical build reconstructed the binary with Apktool and Smali adapters. Those facts, the former inherited APK and its patching tools remain in Git history. The source-native build has its own maintained implementation. The project MIT license covers its applicable original work; third-party artwork and other components retain their documented terms. See `native/icons/NOTICE.txt` and `native/icons/provenance.json` for the curated icon catalog.
