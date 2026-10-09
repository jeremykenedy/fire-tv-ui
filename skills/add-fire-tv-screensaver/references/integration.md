# Integration details

## Screensaver APK

A typical declaration uses the app's actual service class:

```xml
<service
    android:name=".DreamService"
    android:exported="true"
    android:permission="android.permission.BIND_DREAM_SERVICE">
    <intent-filter>
        <action android:name="android.service.dreams.DreamService" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
    <meta-data
        android:name="android.service.dream"
        android:resource="@xml/dream" />
</service>
```

Put a landscape preview in `res/drawable-nodpi/screensaver_preview.jpg` and add `res/xml/dream.xml`:

```xml
<dream xmlns:android="http://schemas.android.com/apk/res/android"
    android:previewImage="@drawable/screensaver_preview" />
```

Use a compile SDK that defines `android:previewImage`; compiling against Android SDK 36 works in this collection. Fire TV UI reads the attribute from the installed app's XML, including on the connected Android 11 Fire TV. Keep the project's actual minimum SDK requirements. Add `android:settingsActivity` only if the app has a real configuration activity.

The preview should be a 384x216 JPEG or PNG with clearly visible subjects. Keep an original capture for review. Preserve aspect ratio when resizing; avoid screenshots of menus, setup instructions, unrelated apps, or black video surfaces.

## Fire TV UI

Relevant maintained files:

- `app/src/com/jeremykenedy/firetv/ui/ScreensaverActivity.java`: installed dream discovery and remote navigation.
- `app/src/com/jeremykenedy/firetv/ui/ScreensaverPreviews.java`: device capture, bundled image, and app metadata loading, in that order.
- `app/src/com/jeremykenedy/firetv/ui/Screensavers.java`: component normalization and preview filenames.
- `app/previews/index.json`: fully qualified component to bundled JPEG mapping.
- `app/screensavers.json`: the on-TV download catalog, with pinned release URL, SHA-256, and exact APK bytes.
- `app/src/com/jeremykenedy/firetv/ui/ScreensaverDownload.java`: verified HTTPS downloads and package identity checks.
- `app/src/com/jeremykenedy/firetv/ui/PackageStorage.java`: installed app plus data storage; cache is already included in data.
- `docs/screensaver-previews.md`: capture commands and source attribution.

Short and full component forms must identify the same dream: `com.example/.DreamService` equals `com.example/com.example.DreamService`. Preview filenames are the SHA-256 of the full component, followed by `.jpg`. Use `scripts/capture_previews.py`'s `preview_file` helper rather than inventing a new naming rule.

On the current API 30 Fire TV, capture a new bundled sample with:

```bash
python3 scripts/capture_previews.py \
  --serial YOUR_TV_IP:5555 \
  --component com.example/.DreamService \
  --delay 45
```

The helper temporarily selects the dream because Fire OS restricts preview activities to the selected app, starts it, waits, captures it, and restores the prior system settings. `--output` captures for review outside the repo. `--device-copy` places a sample in Fire TV UI's external app-files directory; the picker must have been opened first. Reopen it to load the new image. The helper supports API 30 and uses that platform's dream binder transaction order; do not blindly use those transactions on other Android versions.

If the frame is still black, check loading and the active dream. Some hardware video surfaces render on the TV but are missing from ADB screenshots. Check existing verified captures, the app's renderer options, or the video's supplied preview. Restore any temporary renderer option. Avoid repeated captures of the same blank result.

Fire TV UI's launcher core is maintained from a pinned base APK rather than an original Gradle source tree. Leave its resource IDs and patch anchors intact. Its Smali classes on macOS must be identified by class descriptor, since case-colliding decoded filenames can differ from their class names.

For the on-TV catalog, use this entry shape in `app/screensavers.json`:

```json
{
  "id": "example",
  "name": "Example screensaver",
  "package": "com.jeremykenedy.example",
  "dream": "com.jeremykenedy.example/.DreamService",
  "repo": "jeremykenedy/example",
  "tag": "v1.0.0",
  "url": "https://github.com/jeremykenedy/example/releases/download/v1.0.0/example.apk",
  "sha256": "64 hexadecimal characters from the final signed APK",
  "bytes": 123456
}
```

Read the current schema before using this example. Installation is user initiated, downloads are checked against the pinned hash and package identity, and Android confirms installation and removal. Keep the default screensaver and the user's current selection intact.

The local settings guard restores raw ADB changes. Toolkit timer, screensaver, and Alexa commands use Fire TV UI's DUMP-protected receiver to update both the system setting and its desired value. For direct device work, use the receiver's `setting` operation with `namespace`, `key`, and `value` string extras. Its allowed keys are `secure sleep_timeout`, `system screen_off_timeout`, `secure screensaver_components`, `secure screensaver_enabled`, and `secure str.auto_wake_up_enabled` on supported Fire OS devices. Save those values before testing and restore them through the same entry point afterward.

For native cards, navigate with the D-pad and confirm the focused label before pressing Center. Mixing a touch tap with Center can restore the default focus and activate the wrong control. Home-button checks need the launcher transition to finish before UI automation reconnects; on this Fire TV a cold Amazon Home can take several seconds. Read the resumed activity after it settles.

When Home Redirect is installed beside Fire TV UI, use helper 1.2.2 or newer. It delegates Home, selection and timers to the native guard so older snapshots cannot fight intentional GUI changes. Keep another worker's device session separate and restore the original selection through the protected setting entry point.

## Fire TV CLI

The current checkout is usually `~/sites/fire-tv`. Inspect these files before edits:

- `src/screensaver-registry.js`: `SCREENSAVERS`, built-in entries, and selected default.
- `src/apply/screensavers.js`: GitHub release lookup, trusted asset URLs and redirects, checksum parsing, and installation.
- `src/steps/screensavers.js` and `src/steps/set-screensaver.js`: menu and selection flows.
- `test/screensavers-coverage.test.js` and `test/set-screensaver.test.js`: integration coverage.
- `docs/SCREENSAVERS.md`: maintained installation guide.

A registry entry currently has this shape:

```js
{
  id: 'example',
  name: 'Example screensaver',
  pkg: 'com.jeremykenedy.example',
  dreamComponent: 'com.jeremykenedy.example/.DreamService',
  repo: 'jeremykenedy/example',
  blurb: 'An accurate description of the displayed scene.',
}
```

Optional `asset` selects an exact APK in a release with multiple APKs; optional `tag` pins a release. The download helper checks GitHub asset locations and expected redirects. Keep those checks rather than replacing them with an arbitrary URL downloader.

When authorized to prepare a release, provide a signed APK and its `<apk filename>.sha256` file. The existing parser accepts a release-note line `SHA-256: <64 hex characters>`, or `SHA-256 (<asset filename>): <64 hex characters>` for a release with multiple APKs. Generate hashes from the actual final signed artifact. Do not paste a hash from an earlier build or from the unsigned CI artifact.

Run relevant Node tests and the package's required checks after integrating a new entry. Test install, updating the same package with the same signer, choosing the new dream, and returning to the previous dream. Keep unrelated CLI changes and existing screensaver choices intact.
