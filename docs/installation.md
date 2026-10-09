# Installation and updates

Enable ADB debugging in the TV's developer settings, then connect from your computer:

```bash
adb connect YOUR_TV_IP:5555
python3 scripts/install.py --serial YOUR_TV_IP:5555
```

The installer grants WRITE_SECURE_SETTINGS and app operations for persistent backups, usage statistics, and screensaver installation before opening Fire TV UI. To install manually:

```bash
adb -s YOUR_TV_IP:5555 install -r dist/fire-tv-ui-1.0.1.apk
adb -s YOUR_TV_IP:5555 shell pm grant com.jeremykenedy.firetv.ui android.permission.WRITE_SECURE_SETTINGS
adb -s YOUR_TV_IP:5555 shell appops set --uid com.jeremykenedy.firetv.ui MANAGE_EXTERNAL_STORAGE allow
adb -s YOUR_TV_IP:5555 shell appops set com.jeremykenedy.firetv.ui GET_USAGE_STATS allow
adb -s YOUR_TV_IP:5555 shell appops set com.jeremykenedy.firetv.ui REQUEST_INSTALL_PACKAGES allow
adb -s YOUR_TV_IP:5555 shell am start -n com.jeremykenedy.firetv.ui/.MainActivity
```

Select **Settings > Screensavers**. Each row includes a sample image to help identify the screensaver. Selecting an app makes it the active TV screensaver and enables it. The On/Off control preserves the selected app while changing the enabled state.

The permission grant is needed once after a fresh installation. If it is missing, the picker shows setup instructions and keeps the previous TV settings. The TV's existing timers are preserved until changed explicitly.

New screensaver APKs can provide their own preview image through dream metadata. See [Screensaver previews](screensaver-previews.md) for bundled samples and device-specific captures.

## Home button on Fire TV

Fire OS can keep its protected Amazon launcher as the Home target. Fire TV UI includes a Home-button accessibility service that opens Fire TV UI when Amazon Home appears. It waits briefly during transitions to Amazon Settings so those settings remain reachable.

Use **Settings > Startup and protection** to enable or disable startup and Home routing after the installer grants WRITE_SECURE_SETTINGS. This preserves unrelated accessibility services and rebinds the launcher services after updates and boot. Check Home, Amazon Settings, and reboot on the actual device. See [Device controls and backups](device-controls.md) for protection and update blocking.

## Transfer existing launcher settings

Export settings from the existing launcher before changing anything. Fire TV UI has a separate app-data directory. Share the exported text or JSON backup to Fire TV UI to import it, or send the file with `firetv-ui --file=/path/to/backup.txt` and use Manage TV backups > Restore backup sent from computer if the file is accessible in Downloads.

Manage TV backups saves `/sdcard/Download/fire-tv-ui-backup.txt`, which survives uninstall/reinstall. Images, videos, and selected wallpaper files must remain accessible on the TV after importing settings.

## Updating

Build with the same signing key, then run `adb install -r` with the new APK. This preserves app data. Installing an APK signed with a different key requires uninstalling the existing Fire TV UI installation, which deletes its app data.

## Removing Fire TV UI

Remove its accessibility services from the enabled list before uninstalling it. Uninstalling the launcher does not uninstall or delete any screensaver apps. The TV's selected screensaver and enabled state are system settings and remain in place.
