# Installation and updates

Stable Fire TV UI 1.17.0 and Source Preview 2.0.0-alpha.2 are available from [published releases](https://github.com/jeremykenedy/fire-tv-ui/releases). The preview has a separate package and settings; see the [preview guide](source-preview.md).

The repository installer is currently unavailable. The source-native production candidate is not a verified upgrade, and its unsigned build must not be installed. Keep the existing app and its data while migration and device acceptance are pending. For the previously published 1.17.0 instructions, use the [1.17.0 release notes](releases/v1.17.0.md) and the installer from that release's tagged source.

The remaining controls described below document the published 1.x app and require the device capabilities and permissions indicated in their guides.

Select **Settings > Screensavers**. Each row includes a sample image to help identify the screensaver. Selecting an app makes it the active TV screensaver and enables it. The On/Off control preserves the selected app while changing the enabled state.

The permission grant is needed once after a fresh installation. If it is missing, the picker shows setup instructions and keeps the previous TV settings. The TV's existing timers are preserved until changed explicitly.

New screensaver APKs can provide their own preview image through dream metadata. See [Screensaver previews](screensaver-previews.md) for bundled samples and device-specific captures.

## Home button on Fire TV

Fire OS can keep its protected Amazon launcher as the Home target. Fire TV UI includes a Home-button accessibility service that opens Fire TV UI when Amazon Home appears. It waits briefly during transitions to Amazon Settings so those settings remain reachable.

Use **Settings > Startup and protection** to enable or disable startup and Home routing after WRITE_SECURE_SETTINGS is granted. This preserves unrelated accessibility services and rebinds the launcher services after updates and boot. Check Home and Amazon Settings on the actual device. See [Device controls and backups](device-controls.md) for protection and update blocking.

## Transfer existing launcher settings

Export settings from the existing launcher before changing anything. Fire TV UI has a separate app-data directory. Share the exported text or JSON backup to Fire TV UI to import it, or transfer a reviewed Fire TV UI backup with the ADB steps in [Persistent TV backups](device-controls.md#persistent-tv-backups). These backups are separate from other launchers' export formats.

Manage TV backups saves `/sdcard/Download/fire-tv-ui-backup.txt`, which survives uninstall/reinstall. Images, videos, and selected wallpaper files must remain accessible on the TV after importing settings.

## Updating

Save a backup before any upgrade. The source-native production candidate has not yet passed its migration and device acceptance gates; do not replace the published app with it. Installing an APK signed with a different key requires uninstalling the existing installation, which deletes its app data.

## Removing Fire TV UI

Remove its accessibility services from the enabled list before uninstalling it. Uninstalling the launcher does not uninstall or delete any screensaver apps. The TV's selected screensaver and enabled state are system settings and remain in place.

After a successful preference restore, the existing Home controls are notified to reload their cached choices. No notification is sent for invalid data or a failed commit. This is separate from verifying the TV's hardware Home behavior.

## Shared icon pack

The standalone icon script can copy the [HD icon pack](icons.md) to shared `Pictures/Icons` storage and publish it to image pickers. APK-only installations can recover missing bundled icons through Settings > Updates after storage access is granted. The separate icon ZIP and `python3 scripts/install_icons.py --serial YOUR_TV_IP:5555` remain available. Long press an app or input tile and choose **Change Icon**.
