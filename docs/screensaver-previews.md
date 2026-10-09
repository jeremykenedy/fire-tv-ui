# Screensaver previews

Each screensaver row shows a small sample beside its name. Screenshots captured for the device take priority, followed by bundled samples and the installed app's `android:previewImage` resource. An installed app without any image remains selectable and shows **Preview unavailable**.

The bundled samples cover Amazon screensaver, Aerial Views, AndroSaver, Aquarium 4K, Aquarium Live, Colors, Firefly Grove, Jellyfish Drift, Neon Corridor, Snoozy, Starfield Drift, Twilight Hearth, Nebula Drift, Vortex Spiral, Signal Rain, Retro Flight, and Pipeworks Dream. These are examples of the apps' output; the scene or animation can change with the app's configuration.

## Image sources

Amazon screensaver, AndroSaver, Aquarium 4K, Aquarium Live, and Colors were captured from the connected Fire TV on October 8, 2026. AndroSaver's [full screenshot](../art/screenshots/androsaver-tv-1920.png) shows a colorful visualizer after cycling through its animation modes with the remote. They remain the property of their respective app and content owners.

Aerial Views and Snoozy use clear screenshots maintained in the Fire TV CLI project's `docs/screenshots/screensaver-*.jpg` collection. New captures were also checked after 45 seconds. Aerial Views' hardware video remained absent from ADB screenshots. The existing screenshots show recognizable rendered scenes. All app and video content remains the property of its respective owners.

Jellyfish Drift uses the published project's bundled preview, captured from its running Fire TV DreamService. Firefly Grove, Neon Corridor, and Starfield Drift use their published projects' Android TV preview captures. Twilight Hearth, Nebula Drift, Vortex Spiral, Signal Rain, Retro Flight, and Pipeworks Dream use screenshots captured from their running Android TV emulator builds. Their source captures and hashes are recorded in `app/previews/sources.json`.

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
