# Screensaver previews

Each screensaver row shows a small sample beside its name. Screenshots captured for the device take priority, followed by bundled samples and the installed app's `android:previewImage` resource. An installed app without any image remains selectable and shows **Preview unavailable**.

The bundled samples cover Amazon screensaver, Aerial Views, AndroSaver, Aquarium 4K, Aquarium Live, Cloud Drift Clock, Colors, Earthbound Orbit, Firefly Grove, Jellyfish Drift, Neon Corridor, Snoozy, Starfield Drift, Twilight Hearth, Nebula Drift, Vortex Spiral, Signal Rain, Retro Flight, Pipeworks Dream, Aqua Surface Drift, Perseid Passage, Split-Flap Drift, Canopy Drift, Contour Flow, Skyburst Nocturne, Pulse Circuit, Rain on Glass, Rainforest Cascade, Blue Meridian, Helios Lightfield, Slot Vision, and Hearth Presence. These are examples of the apps' output; the scene or animation can change with the app's configuration.

## Image sources

Amazon screensaver, AndroSaver, Aquarium 4K, Aquarium Live, and Colors were captured from the connected Fire TV on October 8, 2026. AndroSaver's [full screenshot](../art/screenshots/androsaver-tv-1920.png) shows a colorful visualizer after cycling through its animation modes with the remote. They remain the property of their respective app and content owners.

Aerial Views and Snoozy use clear screenshots maintained in the Fire TV CLI project's `docs/screenshots/screensaver-*.jpg` collection. New captures were also checked after 45 seconds. Aerial Views' hardware video remained absent from ADB screenshots. The existing screenshots show recognizable rendered scenes. All app and video content remains the property of its respective owners.

Jellyfish Drift uses the published project's bundled preview, captured from its running Fire TV DreamService. Firefly Grove, Neon Corridor, and Starfield Drift use their published projects' Android TV preview captures. Cloud Drift Clock, Twilight Hearth, Nebula Drift, Vortex Spiral, Signal Rain, Retro Flight, Aqua Surface Drift, Earthbound Orbit, Perseid Passage, Split-Flap Drift, Canopy Drift, Contour Flow, Skyburst Nocturne, and Pulse Circuit use screenshots captured from their running Android TV or Google TV emulator builds. Skyburst Nocturne was captured with its Festival rate, Grand size, Tropical palette, and Bright scene settings. Their source captures and hashes are recorded in `app/previews/sources.json`.

Pipeworks Dream uses a new October 15, 2026 capture of its OpenGL ES renderer running on the physical Fire TV AFTDEC012E. The source is [the full television capture](../art/screenshots/pipeworks-fire-tv-preview.png).

Hearth Presence uses an unretouched October 16, 2026 capture of the final signed 1.0.0 release running on the physical Fire TV AFTDEC012E, Android 11/API 30 at 1920x1080. The source is [the full modern fireplace capture](../art/screenshots/hearth-presence-fire-tv-modern-1920.png). The catalog pins the independently downloaded release APK 96067a79be86a0f29f034ee4bcc647e9da7195978b690a7d982f28a17b78278f, 69,011,424 bytes. Its licensed fire footage and surround materials are documented in the [Hearth Presence media provenance](https://github.com/jeremykenedy/hearth-presence/blob/main/docs/MEDIA.md).

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
