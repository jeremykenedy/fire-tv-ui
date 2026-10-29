# Published app updates

Open **Settings > Updates** (also available through Advanced), or **Screensavers > Install screensavers > Check for updates to installed apps**. Select **Check for updates** to request stable published releases. The screen includes Fire TV UI and installed catalog screensavers, launchers and IPTV apps. Other discovered DreamServices show an unavailable publisher source rather than an invented update status.

Rows keep their position while checks run, show progress and offer a per-app retry after failure. Each successful check shows the installed and available versions, published tag, check time and download size. Open **Release details** for the source and SHA-256. Verified metadata is reused for six hours to limit GitHub requests. A cached check is labelled with its original time. Network errors, missing published checksums, inaccessible repositories and rate limits remain unavailable. Matching a checked release is a version comparison, not a guarantee that a newer release has not appeared since that check.

OwnTV releases use the exact TV artifact name `OwnTV-{tag}.apk`, with the checked numeric release tag substituted. Mobile APKs, a lone APK for an older tag and ambiguous alternatives are rejected. Cached metadata must match that exact filename too.

Choose **Install update** to download and verify the APK. Fire TV UI checks its byte count, SHA-256, expected package, installed signing certificate and higher Android version code before opening the normal Android installation confirmation. Replacement installs retain the existing app's data. A cancelled or rejected install does not remove the current app. The TV may require you to allow installation from Fire TV UI through its normal settings screen.

The APK bundles the 32 shared icon images. The Updates screen reports icon access and missing files, and offers Add missing icon files. After storage access is granted, installation and retry copy only missing files and index them for image pickers. Existing files, including custom replacements, are preserved. The release ZIP and repository installer remain available; see [icons](icons.md).

## Home Redirect releases

Home Redirect shares the Toolkit repository, whose newest desktop release may not contain an Android helper. Its check searches up to 100 release-history entries for the exact `firetv-home-redirect.apk` in a published stable release, choosing the newest publication time. Other APKs are never substituted.

The small helper APK is downloaded and verified against its published size and SHA-256, expected package and currently installed signing certificate. Its compiled Android version name and code appear separately from the repository tag. For example, Toolkit tag `v3.9.1` contains Home Redirect app `1.2.6` (code 10). Installing that version already means it matches the checked release; the Toolkit tag is not a newer Home Redirect version. Read-only inspection removes its temporary APK and does not install anything.

## Fire TV UI releases

The TV checks the public UI release directly, selects its exact versioned APK and verifies the compiled Android version, package and current production signer. A desktop or other APK is never substituted for a missing UI asset. No GitHub token is stored on the TV.

If repository access is required or the published release is inaccessible, an authenticated computer handoff remains available. The row explains that the check is unavailable rather than claiming the installed version is current.

On a computer, sign in to GitHub with access to the repository and use Toolkit's **Check for updates** action or `firetv-updates --ip TV_IP --check`. Choose the checked `fire-tv-ui` update. Toolkit downloads and checksum-verifies the published APK, reads its compiled package/version metadata, and sends only the APK and reviewed release metadata to fixed files in TV Downloads. The protected receiver verifies the current signer and higher version code before acknowledging the handoff. Open the TV's Updates screen again and confirm installation there.

The metadata expires after six hours. APK bytes are checked again immediately before installation. The handoff does not contain credentials, change settings, grant permissions or silently install the UI.

## Platform limits

An upstream app without a published checksum cannot receive an automatically verified update through this screen. Its installed version remains visible and its check explains the missing verification. Unknown version names require APK verification; they are not classified as current. Android may reject an otherwise valid APK for device compatibility or policy reasons.

Fire OS can interrupt accessibility Home routing during an APK update. From 1.11.0/code 69, the package-replacement receiver and settings repair rebind enabled Home routing while preserving the values and presence of all four saved Home choices: enabled preference, startup, Home override and improved detection. Earlier builds could normalize the latter three choices during repair. Explicitly enabling or disabling Home still applies the ordinary startup and routing choices. Earlier signed in-place updates preserved the current layout and protection state. The current release's public self-check is verified separately from completing a normal Android replacement confirmation.

## Computer Home transactions

Code 69 adds a narrow, DUMP-protected receiver contract for computer clients. The
`home-preference-state` operation returns exactly `home-preference=unset`,
`home-preference=on` or `home-preference=off`. The `home-preference-on` and
`home-preference-off` operations write only the enabled preference;
`home-preference-clear` restores its absence. These operations do not change
accessibility routing or the other three saved Home choices.

A client can set this preference before changing the OS Home route, verify that
route, then use `home-on` or `home-off` to commit the ordinary explicit Home
behavior. If routing fails, restore the prior enabled preference, including an
absent key, and the prior OS route. A failed full native Home operation restores
all four prior preference values and key presence before reporting the error.
Clients requiring this preservation contract must require code 69 or newer.
