---
name: add-fire-tv-screensaver
description: Add an Android screensaver repository to Fire TV UI with a useful preview and its Fire TV CLI installer entry. Use for new or existing DreamService projects joining this screensaver collection.
---

# Add a Fire TV screensaver

Add the requested screensaver without changing the identity, defaults, or features of other apps in the collection.

## Find the three projects

Identify the screensaver's source repository, the Fire TV UI checkout, and the CLI checkout. Jeremy's usual locations are `~/sites/fire-tv-ui` and `~/sites/fire-tv`; verify their remotes and current files before using those paths. Read applicable project instructions. Check working-tree changes because the CLI may be under active development in another session. Keep edits within the requested integration.

Record the actual app label, Android package, full dream-service component, GitHub repository, minimum SDK, version, and signed APK asset name. Read these from source and the built APK. Existing package names and signing keys must stay stable for in-place updates.

## Add the app and its preview

Fire TV UI discovers installed services protected by `android.permission.BIND_DREAM_SERVICE`. It does not need a hardcoded registration for every new app. Add the proper dream declaration and an app-provided preview to the screensaver itself so the UI can discover both after the CLI installs it. See [references/integration.md](references/integration.md) for the manifest and preview format.

Use a real, representative screenshot of the screensaver. Wait for video loading, fade-in, or animation to settle; 40 to 60 seconds may be needed. Confirm the expected dream is running, then inspect the image at thumbnail size. Choose a frame with visible subjects and color. A black loading frame, faint idle visualizer, generic icon, or generated illustration does not satisfy the preview requirement.

Waiting alone may not improve an audio visualizer when no audio is playing. Use the app's supported remote controls to find a visible animation mode. Preserve its configuration and restore any preferences changed for the capture.

Reuse a clear, verified screenshot of the same app from the user's existing project when useful. A video provider's supplied preview can be used when hardware video is absent from ADB capture, with its source recorded. Keep content attribution. Never brighten or invent a scene to disguise a failed capture.

Prefer shipping the preview inside each screensaver APK. Update Fire TV UI's bundled preview index when the user wants that app covered by a Fire TV UI release too. Preserve its package identity, existing feature availability, and current Settings and Help wording. Open Settings > Screensavers directly; keep a circular selector and a Preview it action for each row. Read installed app storage from Android rather than presenting the APK download size as its total usage.

## Integrate with the installer

Read the CLI's current registry, download code, tests, and release requirements before adding an entry. Use the current schema rather than rewriting the install flow. The integration reference describes the known entry fields and checksum format; recheck them because the CLI is being developed concurrently.

Supply the exact package and dream component, a unique ID, repository, readable label, and an accurate short description. Add `asset` when a release has more than one APK. A screensaver without a published, signed APK is not ready for the existing release installer. Preserve the selected default unless changing it was requested. Do not claim an app has no ads, analytics, trackers, or network access without checking the app's implementation and observed behavior.

When adding the app to the on-TV Install screensavers page, add a verified entry to `app/screensavers.json` as well as the CLI registry. Pin the published APK URL, version tag, SHA-256, and exact byte count. These values must describe the same signed artifact. Existing discovery alone makes an installed app selectable; it does not put an absent app into the download catalog.

Creating integration files does not authorize publishing releases, pushing unrelated changes, or modifying another active checkout's work. Complete the authorized local work and report any publication or integration that remains outside scope.

## Verify the result

Build and inspect the new APK, including its dream permission, metadata resource, package identity, and signing certificate. Keep the same signing key for future updates. Run the affected project's tests and the CLI registry/install checks when its files changed.

On a connected device, use an explicit ADB serial and save the original screensaver component and enabled state before testing. Coordinate with other active device work. Install the app, open Fire TV UI through its Settings entry, and check the app label, image, focus, checked state, scrolling, and selection. Confirm the component saved by the picker and verify the dream actually renders. Check On and Off separately, and restore temporary settings after testing. A manifest or saved setting alone is not proof of runtime behavior.

For Fire TV UI changes, rebuild with `python3 scripts/build.py` and run `bash scripts/test.sh`. Its installer grants the one-time `WRITE_SECURE_SETTINGS` permission and app operations for storage statistics, persistent backups, and package installation before launching the app. Keep the missing-permission path functional for TVs that have not completed setup. Use toolkit settings commands or the protected native receiver for temporary changes while local protection is on; raw ADB changes are deliberately reverted. Preserve online content access while keeping analytics and tracking absent. Do not claim Home-button or boot behavior without the corresponding functional check.

Finish with the APK path and hash, changed project paths, preview provenance, tests and device results, and any remaining publication step. Distinguish completed UI integration from CLI work that was deliberately left to the user's active session.
