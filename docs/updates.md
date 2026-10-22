# Published app updates

Open **Settings > Advanced > Updates**, or **Screensavers > Install screensavers > Check installed-app updates**. Select **Check for updates** to request stable published releases. The screen includes Fire TV UI and installed catalog screensavers, launchers and IPTV apps. Other discovered DreamServices show an unavailable publisher source rather than an invented update status.

Each successful check shows the installed version, published tag, check time, download size and SHA-256. Verified metadata is reused for six hours to limit GitHub requests. A cached check is labelled with its original time. Network errors, missing published checksums, inaccessible repositories and rate limits remain unavailable. Matching a checked release is a version comparison, not a guarantee that a newer release has not appeared since that check.

Choose **Install update** to download and verify the APK. Fire TV UI checks its byte count, SHA-256, expected package, installed signing certificate and higher Android version code before opening the normal Android installation confirmation. Replacement installs retain the existing app's data. A cancelled or rejected install does not remove the current app. The TV may require you to allow installation from Fire TV UI through its normal settings screen.

## Private Fire TV UI releases

The UI repository and APKs stay private. No GitHub token is stored on the TV. When no recent authenticated handoff exists, its row says **Computer sign-in required**.

On a computer, sign in to GitHub with access to the private repository and use Toolkit's **Check for updates** action or `firetv-updates --ip TV_IP --check`. Choose the checked `fire-tv-ui` update. Toolkit downloads and checksum-verifies the published APK, reads its compiled package/version metadata, and sends only the APK and reviewed release metadata to fixed files in TV Downloads. The protected receiver verifies the current signer and higher version code before acknowledging the handoff. Open the TV's Updates screen again and confirm installation there.

The metadata expires after six hours. APK bytes are checked again immediately before installation. The handoff does not contain credentials, change settings, grant permissions or silently install the UI.

## Platform limits

An upstream app without a published checksum cannot receive an automatically verified update through this screen. Its installed version remains visible and its check explains the missing verification. Unknown version names require APK verification; they are not classified as current. Android may reject an otherwise valid APK for device compatibility or policy reasons.

Fire OS can interrupt accessibility Home routing during an APK update. The package-replacement receiver and computer installer attempt to rebind an already enabled Home service without changing the saved preference. The final self-update and Home restoration behavior still requires physical testing for this release.
