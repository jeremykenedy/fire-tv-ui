# Screenshots

## Fire TV UI 1.4.1, code 40

Current physical Fire OS output from the exact signed `ca1b8b5a` release candidate. Live TV is visible beside OwnTV on the first page. Opening it reached the native GuideActivity; the guide frame shows schedules loading and does not establish channel playback. Back and injected Home returned to Main. A hardware remote Home check remains separate.

<table>
<tr>
<td><img src="../art/screenshots/home-ui-1.4.1-code40-fire-tv-1920.png" alt="Current Fire TV UI 1.4.1 code 40 first page with original Live TV artwork beside OwnTV"><br>First-page Live TV</td>
<td><img src="../art/screenshots/native-guide-ui-1.4.1-code40-fire-tv-1920.png" alt="Native Amazon channel guide opened by the code 40 Live TV tile, with schedules loading"><br>Native guide</td>
<td><img src="../art/screenshots/home-after-guide-ui-1.4.1-code40-fire-tv-1920.png" alt="Fire TV UI Main first page restored after leaving the native guide and pressing injected Home"><br>Back and Home</td>
</tr>
</table>

All 46 current portable UI preferences, nine OS values and both external backup/import files were preserved. The live settings guard and VPN remained active. The comparison used the fresh current layout after the deliberate Live TV placement change, rather than restoring its old position.

## Preliminary 1.4.1 code 39 helper verification

Earlier physical Fire OS output from preliminary code 39, exact signed `153da5a2`. The final release is code 40; these captures establish the unchanged helper-check interface and are not final-build screenshots. The helper row verifies the Android app version separately from its repository tag and disables replacement for the already installed version. The launcher capture is scrolled to its native TV tile row. The OwnTV capture was taken before Check for updates and correctly says not checked; it is not a current-version claim. A separate completed native check verified OwnTV 5.2.1 against its published TV APK.

<table>
<tr>
<td><img src="../art/screenshots/updates-helper-ui-1.4.1-fire-tv-1920.png" alt="Fire TV UI 1.4.1 verified Home Redirect app 1.2.6 code 10 separately from Toolkit tag v3.9.1"><br>Verified helper version</td>
<td><img src="../art/screenshots/home-ui-1.4.1-fire-tv-1920.png" alt="Fire TV UI 1.4.1 preserved five-column native Factory UI and Live TV tile row"><br>Native TV tiles</td>
<td><img src="../art/screenshots/updates-owntv-ui-1.4.1-fire-tv-1920.png" alt="Fire TV UI 1.4.1 installed OwnTV 5.2.1 and Fire IPTV 1.1.1 before an explicit update check"><br>Before checking updates</td>
</tr>
</table>


## Previous Fire TV UI 1.4.0

These unretouched 1920x1080 captures show signed candidate `44057a7d` on physical Fire TV AFTDEC012E, Android 11/API 30. The repeated source build has all 631 app payload entries identical. The update check found the exact published OwnTV 5.2.1 TV APK and checksum. The native catalog installed accepted Prism Clock 1.0.0 through Android confirmation; installed APK bytes and signer were independently verified. Its picker row shows the bundled physical screenshot. The row capture shows the UI, while the separate settled Dream capture and binder record establish actual Prism rendering.

<table>
<tr>
<td><img src="../art/screenshots/home-ui-1.4.0-fire-tv-1920.png" alt="Physical Fire TV UI 1.4.0 five-column launcher with preserved original app order"><br>Home</td>
<td><img src="../art/screenshots/updates-ui-1.4.0-fire-tv-1920.png" alt="OwnTV 5.2.1 checked exact TV APK and matching published checksum"><br>Updates</td>
<td><img src="../art/screenshots/prism-picker-ui-1.4.0-fire-tv-1920.png" alt="Installed Prism Clock row and bundled native screenshot in UI 1.4.0"><br>Prism picker</td>
</tr>
<tr>
<td><img src="../art/screenshots/native-tiles-ui-1.4.0-fire-tv-1920.png" alt="Current UI 1.4.0 native Factory UI and Live TV tiles on physical Fire TV"><br>TV tiles</td>
<td><img src="../art/screenshots/prism-dream-ui-1.4.0-fire-tv-1920.png" alt="Actual Prism Clock Dream launched through the UI Preview it action"><br>Prism Dream</td>
<td><img src="../art/screenshots/updates-ui-1.4.0-api34-1920.png" alt="UI 1.4.0 emulator update check distinguishes private sign-in and newer unpublished test builds"><br>API 34 check</td>
</tr>
</table>

Back restored the selected Aquarium Dream. All 46 UI preferences and eight recorded OS settings read back unchanged; the external backup, live guard and VPN were preserved. Home from the settings route returned to the UI. A single Factory tile tap then opened actual Amazon Home for the physical-remote follow-up. That hardware Home check remains unverified; no shell-injected key is treated as equivalent proof. Unpublished emulator builds remain separate from downloadable stable catalog releases.

## Historical selected-idle candidate checks

These unretouched captures show the recovered Fire TV UI 1.3.3 candidate on Google TV API 34 at 1920x1080. Slot Vision is its separately released 1.0.0 APK. An activity preview does not establish a system DreamService binding.

<table>
<tr>
<td><img src="../art/screenshots/appearance-selected-idle-api34-1920.png" alt="Fire TV UI 1.3.3 Appearance with the launcher idle option"><br>Idle</td>
<td><img src="../art/screenshots/launcher-unsupported-idle-api34-1920.png" alt="Fire TV UI 1.3.3 remaining in the launcher after 95 seconds for an unsupported Aquarium preview"><br>No app preview</td>
<td><img src="../art/screenshots/slot-selected-idle-api34-1920.png" alt="Selected Slot Vision preview opened by Fire TV UI 1.3.3 after 95 seconds"><br>Slot preview</td>
</tr>
</table>

## Earlier launcher, catalog and preview captures

Captured on Fire TV hardware and documented TV emulators. Existing launcher and icon captures retain their documented versions. The corrected storage catalog shows 1.3.2 on physical Fire TV; the Rainforest catalog shows 1.3.1 and Advanced and Diagnostics show the 1.3.0 build on Google TV API 34 at 1920x1080.

<table>
  <tr>
    <td><img src="../art/screenshots/icon-picker-tv-1920.png" alt="HD console and media logos in the Fire TV UI image picker"><br>Icons</td>
    <td><img src="../art/screenshots/input-icon-tv-1920.png" alt="Centered Xbox One logo assigned to HDMI 1"><br>Input icon</td>
    <td><img src="../art/screenshots/app-menu-tv-1920.png" alt="Long-press app menu with Change Icon"><br>App menu</td>
  </tr>
  <tr>
    <td><img src="../art/screenshots/home-tv-1920.png" alt="Fire TV UI home screen on Fire TV with Settings dismissed"><br>Home</td>
    <td><img src="../art/screenshots/settings-tv-1920.png" alt="Dismissible Settings menu with the TV screensaver entry"><br>Settings</td>
    <td><img src="../art/screenshots/tv-screensaver-first-tv-1920.png" alt="Installed screensavers with screenshot previews"><br>Savers</td>
  </tr>
  <tr>
    <td><img src="../art/screenshots/theme-tv-1920.png" alt="Wallpaper and theme choices"><br>Theme</td>
    <td><img src="../art/screenshots/appearance-tv-1920.png" alt="Earlier Appearance menu before the launcher idle label correction"><br>Old layout</td>
    <td><img src="../art/screenshots/controls-tv-1920.png" alt="Remote controls and app rows"><br>Controls</td>
  </tr>
  <tr>
    <td><img src="../art/screenshots/backup-tv-1920.png" alt="Launcher backup and restore actions"><br>Backups</td>
    <td><img src="../art/screenshots/about-tv-1920.png" alt="Fire TV UI app name, version, and privacy information"><br>About</td>
    <td><img src="../art/screenshots/help-tv-1920.png" alt="Feature guide without purchase or upgrade messaging"><br>Help</td>
  </tr>
  <tr>
    <td><img src="../art/screenshots/timers-tv-1920.png" alt="TV Sleep Timer duration controls, screensaver delay, Deep Sleep timer and Deep Sleep toggle"><br>Timers</td>
    <td><img src="../art/screenshots/memory-tv-1920.png" alt="Used and free RAM and TV storage with percentage bars"><br>Memory</td>
    <td><img src="../art/screenshots/protection-tv-1920.png" alt="Startup, Home routing, and Amazon update protection controls"><br>Guard</td>
  </tr>
  <tr>
    <td><img src="../art/screenshots/catalog-storage-fire-tv-1.3.2-1920.png" alt="Fire TV UI 1.3.2 physical catalog rejecting impossible Hearth storage figures and showing measured app files"><br>Storage</td>
    <td><img src="../art/screenshots/install-rainforest-catalog-api34-1920.png" alt="Fire TV UI 1.3.1 native catalog showing Rainforest Cascade installed on Google TV API 34"><br>Catalog</td>
    <td><img src="../art/screenshots/persistent-backups-tv-1920.png" alt="Persistent TV backup and CLI import restoration"><br>Backup</td>
  </tr>
  <tr>
    <td><img src="../art/screenshots/aquarium-idle-fire-tv-1920.png" alt="Aquarium 4K running automatically after inactivity on Fire TV"><br>Aqua</td>
    <td><img src="../art/screenshots/aquarium-motion-fire-tv-1920.png" alt="A later Aquarium 4K frame showing swimming fish"><br>Motion</td>
    <td><img src="../art/screenshots/tv-screensaver-lower-tv-1920.png" alt="More installed screensavers on Fire TV"><br>More savers</td>
  </tr>
  <tr>
    <td><img src="../art/screenshots/advanced-tv-1920.png" alt="Advanced page with a focused read-only Diagnostics action"><br>Tools</td>
    <td><img src="../art/screenshots/diagnostics-tv-1920.png" alt="Diagnostics showing selected screensaver and unavailable privileged DreamService inspection"><br>Report</td>
    <td><img src="../art/screenshots/diagnostics-report-tv-1920.png" alt="Diagnostic report distinguishing display mode, UI content size, memory and local app exits"><br>Details</td>
  </tr>
</table>

## Existing catalog previews

Earlier catalog images and their release provenance remain recorded in [screensaver previews](screensaver-previews.md). Listing an existing stable release is not a claim that its visual design has received new acceptance.
