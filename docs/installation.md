# Installation and updates

Enable ADB debugging in the TV's developer settings, then connect from your computer:

```bash
adb connect YOUR_TV_IP:5555
python3 scripts/install.py --serial YOUR_TV_IP:5555
```

The installer grants WRITE_SECURE_SETTINGS and the storage, usage and package-install access supported by the TV's Android version. It copies the shared icons, rebinds an already enabled Home service and opens Fire TV UI. Pass `--apk /path/to/fire-tv-ui-1.4.0.apk` to install a downloaded signed release instead of the locally built APK. Verify the release checksum first.

Select **Settings > Screensavers**. Each row includes a sample image to help identify the screensaver. Selecting an app makes it the active TV screensaver and enables it. The On/Off control preserves the selected app while changing the enabled state.

The permission grant is needed once after a fresh installation. If it is missing, the picker shows setup instructions and keeps the previous TV settings. The TV's existing timers are preserved until changed explicitly.

New screensaver APKs can provide their own preview image through dream metadata. See [Screensaver previews](screensaver-previews.md) for bundled samples and device-specific captures.

## Home button on Fire TV

Fire OS can keep its protected Amazon launcher as the Home target. Fire TV UI includes a Home-button accessibility service that opens Fire TV UI when Amazon Home appears. It waits briefly during transitions to Amazon Settings so those settings remain reachable.

Use **Settings > Startup and protection** to enable or disable startup and Home routing after the installer grants WRITE_SECURE_SETTINGS. This preserves unrelated accessibility services and rebinds the launcher services after updates and boot. Check Home, Amazon Settings, and reboot on the actual device. See [Device controls and backups](device-controls.md) for protection and update blocking.

## Transfer existing launcher settings

Export settings from the existing launcher before changing anything. Fire TV UI has a separate app-data directory. Share the exported text or JSON backup to Fire TV UI to import it, or transfer a reviewed Fire TV UI backup with the ADB steps in [Persistent TV backups](device-controls.md#persistent-tv-backups). These backups are separate from other launchers' export formats.

Manage TV backups saves `/sdcard/Download/fire-tv-ui-backup.txt`, which survives uninstall/reinstall. Images, videos, and selected wallpaper files must remain accessible on the TV after importing settings.

## Updating

Save a backup first, then run the repository installer with the new signed APK. It uses `adb install -r` to preserve app data and explicitly rebinds previously enabled Home routing. A raw APK installation or force-stop can leave an accessibility service unbound even while its enabled setting remains present; use the installer and verify Home afterward. Installing an APK signed with a different key requires uninstalling the existing Fire TV UI installation, which deletes its app data.

## Removing Fire TV UI

Remove its accessibility services from the enabled list before uninstalling it. Uninstalling the launcher does not uninstall or delete any screensaver apps. The TV's selected screensaver and enabled state are system settings and remain in place.

## Shared icon pack

The repository installer also copies the [HD icon pack](icons.md) to shared `Pictures/Icons` storage and publishes it to image pickers. APK-only installations need the separate icon ZIP or `python3 scripts/install_icons.py --serial YOUR_TV_IP:5555`. Long press an app or input tile and choose **Change Icon**.
