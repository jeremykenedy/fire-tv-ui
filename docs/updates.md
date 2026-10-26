# Published app updates

Open **Settings > Advanced > Updates**, or **Screensavers > Install screensavers > Check installed-app updates**. Select **Check for updates** to request stable published releases. The screen includes Fire TV UI and installed catalog screensavers, launchers and IPTV apps. Other discovered DreamServices show an unavailable publisher source rather than an invented update status.

Each successful check shows the installed version, published tag, check time, download size and SHA-256. Verified metadata is reused for six hours to limit GitHub requests. A cached check is labelled with its original time. Network errors, missing published checksums, inaccessible repositories and rate limits remain unavailable. Matching a checked release is a version comparison, not a guarantee that a newer release has not appeared since that check.

OwnTV releases use the exact TV artifact name `OwnTV-{tag}.apk`, with the checked numeric release tag substituted. Mobile APKs, a lone APK for an older tag and ambiguous alternatives are rejected. Cached metadata must match that exact filename too.

Choose **Install update** to download and verify the APK. Fire TV UI checks its byte count, SHA-256, expected package, installed signing certificate and higher Android version code before opening the normal Android installation confirmation. Replacement installs retain the existing app's data. A cancelled or rejected install does not remove the current app. The TV may require you to allow installation from Fire TV UI through its normal settings screen.

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

Fire OS can interrupt accessibility Home routing during an APK update. The package-replacement receiver and computer installer attempt to rebind an already enabled Home service without changing the saved preference. Earlier signed in-place updates preserved the current layout and protection state. The current release's public self-check is verified separately from completing a normal Android replacement confirmation.
