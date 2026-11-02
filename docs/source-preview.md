# Fire TV UI Source Preview

Version 2.0.0-alpha.1 is a separately installed preview of the new native interface. Stable [Fire TV UI 1.17.0](https://github.com/jeremykenedy/fire-tv-ui/releases/tag/v1.17.0) remains the latest stable release.

Release preparation is in progress. The final signed alpha, qualification results and screenshots are pending. This guide describes the reviewed first-cut feature scope; it is not a publication announcement.

## What is included

| Feature | Preview scope |
| --- | --- |
| Apps and inputs | App ordering, favorites, hide/show, folders, launcher-scoped locks, input names and per-tile artwork. |
| Cards and widgets | Shared order, visibility and card size; Android TV published recommendations, rotation controls and item/series hide/restore where verified identity is available. |
| Local widgets | Clock, world clocks, countdowns, timers, notes, reminders, selected photos, quick actions, breathing and configured sun/moon information. New widgets start hidden. |
| Appearance | Focus speed, growth, border/fill choices, curated and selected personal icons, supported icon packs, backgrounds, overlays and the included holiday scenes. |
| Saved setups | Named preview presentation snapshots with optional icon restoration and retained local images. Missing media produces an error without deleting the setup. |
| Portable backup | Explicit document export/import of reviewed preview settings. Credentials, device controls and lock secrets remain local; personal media is not embedded. Notes/reminders require their separate local export consent and an explicit choice for that export. |
| Device pages and Help | Screensaver administration, network/status pages, capability-dependent system-setting handoffs and original Help. Platform restrictions remain visible. |

These are alpha interfaces, not a statement of complete 1.x parity. A launcher lock controls actions inside this app; it does not secure Android or prevent another app from launching a target.

## Installation

The declared minimum is Android 8.1 / API 27. Sideloading requires ADB access. Runtime qualification is recorded separately from this minimum; no physical Fire TV, Google TV, voice, CEC or receiver acceptance is claimed for this alpha.

When the alpha release is published, download its APK and matching checksum from the repository's [release list](https://github.com/jeremykenedy/fire-tv-ui/releases). Use the source-preview assets, not the stable 1.x installer or APK.

```bash
shasum -a 256 -c fire-tv-ui-source-preview-2.0.0-alpha.1.apk.sha256
adb devices -l
adb -s YOUR_TV_SERIAL install fire-tv-ui-source-preview-2.0.0-alpha.1.apk
```

Open **Fire TV UI Source Preview** from the TV's app list. Its package is `com.jeremykenedy.firetv.ui.preview`; stable Fire TV UI and its settings remain separate. Do not uninstall stable Fire TV UI or change Home routing to try this preview. The preview is not registered as a replacement Home or accessibility service.

The approved maintained preview certificate SHA-256 is `c64ca640cab78e75fd52a6bb43bcbf145aa1b951f0c719b471df241bf70669c6`. The release retains that existing preview identity; it does not require a new incompatible signer. A same-certificate preview can use `adb -s YOUR_TV_SERIAL install -r fire-tv-ui-source-preview-2.0.0-alpha.1.apk` to retain its app data, subject to the final upgrade qualification.

For a preview signed with another key, Android will refuse an in-place update. Preserve any needed preview data before deciding whether to remove that app; there is no automatic migration across signers. Production 1.x data is not imported by this installation.

## First use

Open Settings and choose **Home layout > Apps grid** or **Your cards and widgets**. New local widgets remain hidden until selected. Configure private content and selected files explicitly; permission denial or an unavailable document provider may prevent a feature from working.

Use **Appearance** for focus, backgrounds and icon packs. Long-press an app/input tile for its available actions. Use **Cards > Recommendation sources** to choose supported sources. Android TV recommendations depend on what installed apps publish and what the platform permits. Plex setup requires your own authorized connection. Opening an app does not prove exact-title playback, entitlement or access to a service's personal watch history.

Use **Updates and backup > Saved setups** for local presentation snapshots, or **Backup and import** for a selected portable document. A preview backup is not a production migration tool. Unsupported legacy formats are retained and rejected for application until their mapping is verified. Notes, reminders, photos, connection credentials and device controls have separate privacy and persistence boundaries; a saved appearance is not a household/account backup.

The Updates screen has no configured compatible public preview feed in this first cut. It does not offer the legacy production APK as a preview update.

## Data and rollback

The alpha keeps its own app data. It does not open another package's private settings, copy production accounts or silently adopt a production Home configuration. Imports validate supported settings and preserve local credentials, lock secrets and device controls. Private notes/reminders are excluded from export by default; imported content does not grant future export permission or enable detail display.

To stop using the preview, close it and open the stable Fire TV UI or the TV's existing Home. No production downgrade is needed. Uninstalling the preview deletes its local data. Export the supported preview data you want to keep before removing it, and retain selected personal source files separately. Restoring a preview document does not restore Android URI grants or account credentials.

## Limits of this first alpha

- Production 1.x migration, Home takeover, Alexa/receiver/CEC behavior and full feature parity are not delivered by this preview.
- A separate package cannot read the existing production app's private data. Unsupported legacy setups/backups are not silently converted or reset.
- Public streaming-service personal history and exact playback are not universally available. Provider permissions, published data and authorized service connections determine available cards/actions.
- General cleartext HTTP is disabled. The included loopback exception does not establish support for arbitrary HTTP Plex/NAS servers.
- Personal media and sensitive widget content are not automatically portable. A missing source remains an explicit unavailable state.
- API 27 runtime coverage, physical-TV performance and visual acceptance remain qualification gates. Build success or an emulator result does not establish those results.
- Later optional work, including dashboard scheduling, calendar, weather/radar, Now Playing and ambient audio, is outside this first-cut scope.

Final alpha screenshots will be added from the exact qualified artifact. The [existing gallery](screenshots.md) is labeled by stable release and must not be used as evidence of the alpha interface.

## Source and licenses

The preview build uses the reviewed `native/` Java/resources and documented Android toolchain. Its artifact does not fetch, decode, patch or package the inherited launcher. This statement applies to the preview build inputs; the repository retains its legacy implementation, truthful Git history and applicable attribution.

Owned project material remains under the [MIT license](../LICENSE). Bundled artwork and other third-party material retain their notices, including the [native icon notice](../native/icons/NOTICE.txt). Do not infer one license for every historical asset from the repository's MIT license.

Final release evidence will accompany the qualified alpha release. The [documentation index](index.md) retains the separately labeled 1.x guides.
