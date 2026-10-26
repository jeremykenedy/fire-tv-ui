# Screensaver previews

Each screensaver row shows a small sample beside its name. Screenshots captured for the device take priority, followed by bundled samples and the installed app's `android:previewImage` resource. An installed app without any image remains selectable and shows **Preview unavailable**.

The bundled samples cover Amazon screensaver, Aerial Views, AndroSaver, Aquarium 4K, Aquarium Live, Cloud Drift Clock, Colors, Earthbound Orbit, Firefly Grove, Jellyfish Drift, Neon Corridor, Snoozy, Starfield Drift, Twilight Hearth, Nebula Drift, Vortex Spiral, Signal Rain, Retro Flight, Pipeworks Dream, Aqua Surface Drift, Perseid Passage, Split-Flap Drift, Canopy Drift, Contour Flow, Skyburst Nocturne, Pulse Circuit, Rain on Glass, Rainforest Cascade, Blue Meridian, Helios Lightfield, Slot Vision, Hearth Presence, and Skycast Weather. These are examples of the apps' output; the scene or animation can change with the app's configuration.

## Running a preview

The picker distinguishes the sample image from the live preview action. **Preview it** opens a reviewed exported preview activity when the installed app provides one. The target must belong to the selected screensaver's package, be enabled and exported, and require no permission missing from Fire TV UI.

**Preview controls** opens the app's own accessible Dream settings or launcher when its full-screen preview is private or missing. Select the app's Preview or Show aquarium button to continue. Aquarium 4K initially shows its settings panel with Show aquarium focused. This extra step is deliberate; opening settings is not counted as a full-screen preview.

The existing apps use these in-app controls:

| App | Choose inside the app |
| --- | --- |
| AndroSaver | Start Now |
| Aquarium 4K | Show aquarium |
| Aquarium Live | Preview |
| Contour Flow, Nebula Drift, Split-Flap Drift | Open full-screen preview |
| Skyburst Nocturne | Preview animation |
| Snoozy | Preview screensaver |

Contour Flow, Nebula Drift and Split-Flap Drift keep their general settings screens separate from their preview launchers. **Screensaver settings** opens settings; **Preview controls** opens the reviewed launcher containing its preview button. An app still needs usable configured content: an empty slideshow can open correctly while showing No images found.

Direct app previews and preview controls do not change the system's selected screensaver, enabled state or timers. Fire TV UI checks that the legacy system starter is callable before using it as a fallback, and restores any temporary selection on return or launch failure. Some Fire OS versions do not include that starter. If neither a live preview nor accessible controls exist, the action says Preview unavailable.

## Image sources

Amazon screensaver, AndroSaver, Aquarium 4K, Aquarium Live, and Colors were captured from the connected Fire TV on October 8, 2026. AndroSaver's [full screenshot](../art/screenshots/androsaver-tv-1920.png) shows a colorful visualizer after cycling through its animation modes with the remote. They remain the property of their respective app and content owners.

Aerial Views and Snoozy use clear screenshots maintained in the Fire TV CLI project's `docs/screenshots/screensaver-*.jpg` collection. New captures were also checked after 45 seconds. Aerial Views' hardware video remained absent from ADB screenshots. The existing screenshots show recognizable rendered scenes. All app and video content remains the property of its respective owners.

Jellyfish Drift uses the published project's bundled preview, captured from its running Fire TV DreamService. Firefly Grove, Neon Corridor, and Starfield Drift use their published projects' Android TV preview captures. Cloud Drift Clock, Twilight Hearth, Nebula Drift, Vortex Spiral, Signal Rain, Retro Flight, Aqua Surface Drift, Earthbound Orbit, Perseid Passage, Split-Flap Drift, Canopy Drift, Contour Flow, Skyburst Nocturne, and Pulse Circuit use screenshots captured from their running Android TV or Google TV emulator builds. Skyburst Nocturne was captured with its Festival rate, Grand size, Tropical palette, and Bright scene settings. Their source captures and hashes are recorded in `app/previews/sources.json`.

Pipeworks Dream uses an October 22, 2026 capture of its final signed 1.1.0 OpenGL ES renderer running on physical Fire TV AFTDEC012E, Android 11/API 30 at 1920x1080. The independently downloaded release APK is 62,276 bytes with SHA-256 a9642a20efe37fd3b39974d04f59b17eb4940935dbaca60c4b6cbf655eef03d0. The source is [the full television capture](../art/screenshots/pipeworks-fire-tv-preview.png).

Hearth Presence uses an unretouched October 16, 2026 capture of the final signed 1.0.0 release running on the physical Fire TV AFTDEC012E, Android 11/API 30 at 1920x1080. The source is [the full modern fireplace capture](../art/screenshots/hearth-presence-fire-tv-modern-1920.png). The catalog pins the independently downloaded release APK 96067a79be86a0f29f034ee4bcc647e9da7195978b690a7d982f28a17b78278f, 69,011,424 bytes. Its licensed fire footage and surround materials are documented in the [Hearth Presence media provenance](https://github.com/jeremykenedy/hearth-presence/blob/main/docs/MEDIA.md).

Slot Vision uses an unretouched October 16, 2026 Google TV API 34 capture of its current classic casino scene. The [full native source](../art/screenshots/slot-vision-casino-native-1920.png) shows the actual three-dimensional cabinet and simulated flying coins. The scene renders at 1280x720 and is composed into the 1920x1080 capture; this is not native 1080 or 4K rendering evidence. Its release pin preserves the existing com.jeremykenedy.reelatrium package and ReelAtriumDreamService component for compatible upgrades. The independently downloaded v1.0.0 APK has SHA-256 0ee21b586f3b306e6da57cacc149bebece35a01e6ef2c7af1512c0ddc51354e4 and is 62,076,478 bytes.

Rainforest Cascade uses the unretouched October 16, 2026 [physical Fire TV garden capture](../art/screenshots/rainforest-cascade-fire-tv-garden-day-1920.png) from its final signed 1.1.0 release. The AFTDEC012E capture is 1920x1080 on Android 11/API 30. Its independently downloaded APK has SHA-256 88602f840deae1e9a7fa8de5b1dda03601c70d0d70399ab11262e1817e7de31f and is 72,053,957 bytes. The existing package, DreamService and release signer are preserved.

`app/previews/index.json` maps each fully qualified dream service to its image. Filenames use SHA-256 of the complete component name so different dream services in the same app have separate samples.

## Capture another installed screensaver

The capture helper supports Android 11 / API 30 Fire TVs and requires ImageMagick on the computer. Open the picker once before copying a device preview so its app directory exists.

```bash
python3 scripts/capture_previews.py \
  --serial YOUR_TV_IP:5555 \
  --component com.example.screensaver/.DreamService \
  --delay 45 \
  --output /tmp/screensaver-previews \
  --device-copy
```

The command briefly selects and starts the dream, captures a 384x216 sample, and restores the TV's original screensaver selection and On/Off setting. Inspect the image before adding it to the package: hardware video surfaces can be missing from ADB screenshots even when the dream is running. Some visualizers also stay faint without audio. Use the app's supported controls to choose a visibly rendered mode and inspect it at thumbnail size. Restore any app preferences changed for the capture. Reopen the picker to load a newly copied image.

For a bundled image, omit `--output` and `--device-copy`, then rebuild the APK. New samples are stored in `app/previews/` along with the component index.

## Hearth release catalog verification

The isolated integration candidate 1.3.0/code 34, SHA-256 e65025fc2199fc3a1107ea0b2bc662401582cca78e184d83e1c83499fc43ffc8, was exercised on Google TV API 34 at 1920x1080. From the installed-screensaver picker, the D-pad focused Install screensavers before Select opened the actual catalog. The final Hearth Presence row showed Installed and measured app files, with unavailable data/cache statistics labelled honestly. The destructive uninstall action was not activated. Back returned to the picker; Home and app stop completed with all five secure selection/enable/timer values unchanged. The [native catalog capture](../art/screenshots/install-hearth-catalog-api34-1920.png) was opened and viewed. This is an emulator route check, not physical launcher or unified UI release acceptance. The original checkout's concurrent launcher changes remain separate.

## Prism Clock published preview

Prism Clock uses the [unretouched physical silver capture](../art/screenshots/prism-clock-fire-tv-silver-1920.png) of v1.0.0 on the Fire TV AFTDEC012E, Android 11/API 30 at 1920x1080. The catalog pins its verified public APK, 127,947 bytes with SHA-256 edbf475c4a2b253ccc069adaee8cac2cbba4d16b5579d5299e2e2d1bdaa21fd5. The 384x216 bundled image is resized from that capture. Its installed settings interface is `content://com.jeremykenedy.prismclock.settings/schema` and `/settings`; the app provides eleven controls, a Settings activity and an explicit Preview activity. Registering it does not change the selected screensaver.

## Cloud Drift Clock 2.0

The Cloud Drift Clock preview now comes from its final physical Fire TV API 28 capture of the real sunset flight, with the clock off. The 384x216 thumbnail is resized from the unretouched [native capture](../art/screenshots/cloud-flight-fire-tv-api28-1920.png). The filmed scene is by anotherview under CC BY 3.0; its attribution and complete license are bundled in **Advanced > Preview media credits**.

The catalog pins the published 2.0.0/code5 APK, 40,297,575 bytes, SHA-256 `abb8fdf3abdd1c94b95106b6bd0cbdf85822ccffd4caec9cdbd6dcb99919fae8`. The package, signer and CloudDreamService remain unchanged. Screensaver settings opens the app's native settings activity, which exposes seven effective controls for playback and the optional clock. The old palette and density values remain stored but are inactive and are not offered as visual effects. Updating the catalog does not change the selected screensaver or its configuration.

## Blue Meridian 1.0.1

Blue Meridian uses its unretouched [physical Fire TV API 30 Earth capture](../art/screenshots/blue-meridian-earth-fire-tv-api30-1920.png), resized to 384x216 for the picker. There is no NASA word or watermark painted on the scene. Its archival day, cloud and night-light imagery is attributed in the offline preview credits. The globe is not current weather; camera movement is gentle screen drift rather than orbital flight.

The catalog pins the independently downloaded 1.0.1/code 2 APK, 2,569,417 bytes, SHA-256 `7f3b575f45d7afa5ca68ff8510d84f5b42c6ac6c3c21903628898f6a5dff5ec2`. Its existing package, production signer and seven stored settings remain unchanged.

## Skycast Weather

The bundled image is the published 1.0.0/code 4 [clouds-only physical API 28 Dream capture](../art/screenshots/skycast-weather-clouds-only-fire-tv-api28-1920.png), resized only to 384x216. The silent sunset flight is by anotherview under CC BY3.0; the full notice and license are available in the offline Preview media credits. The edited ambient loop does not depict the configured city or current conditions.

Skycast Weather offers units, one/three/five/seven days, motion speed and Show weather, plus explicit city setup in its native Settings. Show weather Off removes all scene text and stops scene weather requests. No owner location or forecast is bundled into Fire TV UI. Weather/geocoding provider terms remain separate from the app's code license.
