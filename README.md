<p align="center">
  <a href="https://github.com/jeremykenedy/fire-tv-ui#gh-light-mode-only"><picture><source media="(max-width: 600px) and (prefers-reduced-motion: reduce)" srcset="art/banner-mobile-still-light.98d256baacad.svg"><source media="(prefers-reduced-motion: reduce)" srcset="art/banner-still-light.cc4cdf921b24.svg"><source media="(max-width: 600px)" srcset="art/banner-mobile-light.7cf87e68fb8d.svg"><img src="art/banner-light.e99322de878a.svg#gh-light-mode-only" alt="Fire TV UI" width="800"></picture></a>
  <a href="https://github.com/jeremykenedy/fire-tv-ui#gh-dark-mode-only"><picture><source media="(max-width: 600px) and (prefers-reduced-motion: reduce)" srcset="art/banner-mobile-still-dark.a324d51d0cd9.svg"><source media="(prefers-reduced-motion: reduce)" srcset="art/banner-still-dark.c72c1431c05d.svg"><source media="(max-width: 600px)" srcset="art/banner-mobile-dark.6cfcbd32ad7d.svg"><img src="art/banner-dark.bf78d582be0e.svg#gh-dark-mode-only" alt="Fire TV UI" width="800"></picture></a>
</p>

<p align="center">A customizable home screen for Fire TV, with screensavers, persistent backups, local device protection, and every feature included.</p>

<p align="center">
    <a href="#building"><img src="https://img.shields.io/badge/Fire_TV_UI-v1.3.0-blue" alt="Build version"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License"></a>
</p>

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Features](#features)
- [Input icons](#input-icons)
- [Configuration](#configuration)
- [Building](#building)
- [Testing](#testing)
- [Documentation](#documentation)
- [Screensaver Skill](#screensaver-skill)
- [Screenshots](#screenshots)
- [License](#license)

## Requirements

- Fire TV or Android TV running Android 8.1 / API 27 or newer.
- ADB access for installation and the one-time screensaver permission grant.
- Python 3.10+, JDK 21, Android SDK platform 36, and build-tools 36.0.0 to build locally.

## Installation

This repository and its APK are private. Users with repository access can install the signed APK from the private releases, or build it locally. Physical Fire TV, Android TV, and Google TV checks are still welcome; see the [device verification matrix](docs/verification.md).

The [Fire TV Toolkit](https://github.com/jeremykenedy/fire-tv-toolkit) can install a local signed APK and its published checksum:

```bash
firetv-ui --install --apk=/path/to/fire-tv-ui-1.3.0.apk --sha256=PUBLISHED_SHA256
```

For a local build, run `python3 scripts/build.py`, then install it and grant screensaver, backup, storage, and package-install access:

```bash
python3 scripts/install.py --serial YOUR_TV_IP:5555
```

Fire TV UI has its own package identity. It can be installed alongside an existing launcher. Subsequent updates must use the same signing key; `adb install -r` keeps Fire TV UI's settings.

For wireless ADB, Home-button setup, and transferring settings from an existing launcher, see [Installation and updates](docs/installation.md).

## Quick Start

Open **Fire TV UI**, accept the accessibility disclosure if you want remote shortcuts, and use the preferences icon in the top bar to open Settings.

Press **Back** or **Menu** to dismiss the Settings panel. **Home** returns to the launcher with the panel closed when Home routing is enabled.

Choose **Settings > Screensavers** to select an installed screensaver. Each row has a sample image, installed storage usage, a circular selector, and a **Preview it** button. The top controls open the current screensaver's options, system timers, installation catalog, and memory usage. Previewing another app restores your prior selection afterward.

Choose **Startup and protection** in Settings to enable or disable Fire TV UI as the startup and Home app, protect selected settings from reversion, or block Amazon update traffic locally. Streaming, weather, CDNs, and screensaver downloads remain available. No analytics, tracking, rating, or project prompts are included.

## Features

- Custom home layout, favorites, folders, app ordering, visibility, and per-app icons.
- Light and dark themes, gradients, images, image URLs, slideshows, and video wallpapers.
- Weather, clocks, calendar, year progress, RSS, podcasts, search, app shortcuts, and other widgets.
- Icon packs, featured cards, Watch Next, and channel management.
- Remote shortcuts, launcher clock screensaver, and backup and restore.
- A near-full-screen screensaver card with sample images, per-app storage usage, individual previews, and access to installed apps' settings.
- Separate TV Sleep Timer, screensaver delay, Deep Sleep toggle, and Deep Sleep timer controls.
- Verified on-TV downloads and Android-confirmed removal of the curated screensaver catalog.
- Read-only Advanced diagnostics with device status, explicit measurement limits, and local report saving.
- Local protection for Home routing, screensaver selection, enabled state, and timers, including boot and APK-update handling.
- Package-scoped Amazon update blocking, with an optional Appstore block and no remote VPN server.
- Persistent TV Downloads backups, CLI import/export, and a saved simple layout.
- All customization features enabled from the first launch.

## Input icons

The [32 HD icons](docs/icons.md) cover consoles, media players, VR, and computers. Each tile contains a centered logo at 1920x1080, with its original proportions and no extra caption. The installer copies them into the TV's shared `Pictures/Icons` folder and makes them visible to image pickers.

Long press the app or input tile, choose **Change Icon**, then select an image. For LTvLauncher, use **Set Custom Banner** on an app. See the [icon guide](docs/icons.md) for the full catalog and standalone installation.

## Configuration

| Setting | Behavior |
| --- | --- |
| Screensavers | Select, preview, configure, install, or remove screensavers; manage timers and memory. |
| Theme Mode | Choose wallpapers, gradients, icon packs, and text contrast. |
| Appearance | Configure widgets, status bar, favorites, cards, and the launcher clock screensaver. |
| Controls | Configure remote actions and app rows. |
| Backup/Restore | Save or restore the persistent TV backup, or restore a backup sent by the CLI. |
| Startup and protection | Enable or disable startup and Home routing; protect settings and block Amazon updates. |
| About | View the app name, version, credits, and privacy information. |
| Help | Read the feature guide and screensaver setup instructions. |

The launcher clock screensaver starts disabled on a fresh installation so the TV's selected screensaver can run. It remains available in Appearance. Existing clock-screen preferences are preserved on update.

## Building

I'm looking for someone to live-test Fire TV UI on physical **Android TV** and **Google TV** devices. Testing so far covers Fire TV hardware and an Android TV emulator. Home routing, screensavers, timers, and backup behavior still need confirmation on those physical devices.

```bash
python3 scripts/build.py
```

The build verifies its base APK and tool checksums, applies the maintained launcher changes, compiles the added Android code, aligns the APK, signs it, and verifies the signature. Output and SHA-256 files are written to `dist/`.

The signing key is kept outside the repository at `~/.android/keys/fire-tv-ui.jks`. Back up this key and its `.pass` file for future updates. See [Build and source layout](docs/building.md) for unsigned review builds and custom signing keys.

## Testing

```bash
python3 scripts/build.py --unsigned
bash scripts/test.sh
```

The local checks cover screensaver component matching, sorting, duplicate services, missing selections, patch safety, package identity, the settings entry, included features, and removal of purchase messaging from Help. They also verify release hashes, preview provenance, and each new app's settings route on an Android TV emulator. This private repository does not run CI.

Device testing is described in [Verification](docs/verification.md).

## Documentation

- [HD input and app icons](docs/icons.md)
- [Installation and updates](docs/installation.md)
- [Build and source layout](docs/building.md)
- [Verification](docs/verification.md)
- [Screensaver previews](docs/screensaver-previews.md)
- [Device controls and backups](docs/device-controls.md)
- [Fire TV UI 1.3.0 release notes and upgrade guide](docs/releases/v1.3.0.md)
- [Fire TV UI 1.2.0 release notes and upgrade guide](docs/releases/v1.2.0.md)
- [Fire TV UI 1.1.3 release notes and upgrade guide](docs/releases/v1.1.3.md)
- [Fire TV UI 1.1.2 release notes and upgrade guide](docs/releases/v1.1.2.md)
- [Fire TV UI 1.1.1 release notes and upgrade guide](docs/releases/v1.1.1.md)

## Screensaver Skill

[Add a Fire TV screensaver](skills/add-fire-tv-screensaver/SKILL.md) describes how to add new screensaver repos, include a useful image in their APKs, verify them in Fire TV UI, and register them with the Fire TV CLI installer.

Copy `skills/add-fire-tv-screensaver/` into `~/.codex/skills/` to install it locally. Invoke it with `$add-fire-tv-screensaver` and provide the screensaver repo. The CLI registry remains in the separate Fire TV CLI project.

## Screenshots

Captured on Fire TV hardware and documented TV emulators. Existing launcher and icon captures retain their documented versions. Advanced and Diagnostics show the current 1.3.0 candidate on Google TV API 34 at 1920x1080.

<table>
  <tr>
    <td><img src="art/screenshots/icon-picker-tv-1920.png" alt="HD console and media logos in the Fire TV UI image picker"><br>Icon picker</td>
    <td><img src="art/screenshots/input-icon-tv-1920.png" alt="Centered Xbox One logo assigned to HDMI 1"><br>Custom input icon</td>
    <td><img src="art/screenshots/app-menu-tv-1920.png" alt="Long-press app menu with Change Icon"><br>App menu</td>
  </tr>
  <tr>
    <td><img src="art/screenshots/home-tv-1920.png" alt="Fire TV UI home screen on Fire TV with Settings dismissed"><br>Home</td>
    <td><img src="art/screenshots/settings-tv-1920.png" alt="Dismissible Settings menu with the TV screensaver entry"><br>Settings</td>
    <td><img src="art/screenshots/tv-screensaver-first-tv-1920.png" alt="Installed screensavers with screenshot previews"><br>TV screensaver</td>
  </tr>
  <tr>
    <td><img src="art/screenshots/theme-tv-1920.png" alt="Wallpaper and theme choices"><br>Theme Mode</td>
    <td><img src="art/screenshots/appearance-tv-1920.png" alt="Appearance options with all customization features available"><br>Appearance</td>
    <td><img src="art/screenshots/controls-tv-1920.png" alt="Remote controls and app rows"><br>Controls</td>
  </tr>
  <tr>
    <td><img src="art/screenshots/backup-tv-1920.png" alt="Launcher backup and restore actions"><br>Backup/Restore</td>
    <td><img src="art/screenshots/about-tv-1920.png" alt="Fire TV UI app name, version, and privacy information"><br>About</td>
    <td><img src="art/screenshots/help-tv-1920.png" alt="Feature guide without purchase or upgrade messaging"><br>Help</td>
  </tr>
  <tr>
    <td><img src="art/screenshots/timers-tv-1920.png" alt="TV Sleep Timer duration controls, screensaver delay, Deep Sleep timer and Deep Sleep toggle"><br>System timers</td>
    <td><img src="art/screenshots/memory-tv-1920.png" alt="Used and free RAM and TV storage with percentage bars"><br>Memory usage</td>
    <td><img src="art/screenshots/protection-tv-1920.png" alt="Startup, Home routing, and Amazon update protection controls"><br>Startup and protection</td>
  </tr>
  <tr>
    <td><img src="art/screenshots/install-screensavers-tv-1920.png" alt="Verified screensaver installation and removal catalog"><br>Install screensavers</td>
    <td><img src="art/screenshots/install-hearth-catalog-api34-1920.png" alt="Current native catalog showing Hearth Presence installed and honest storage availability on Google TV API 34"><br>Hearth catalog</td>
    <td><img src="art/screenshots/persistent-backups-tv-1920.png" alt="Persistent TV backup and CLI import restoration"><br>TV backups</td>
  </tr>
  <tr>
    <td><img src="art/screenshots/aquarium-idle-fire-tv-1920.png" alt="Aquarium 4K running automatically after inactivity on Fire TV"><br>Aquarium 4K</td>
    <td><img src="art/screenshots/aquarium-motion-fire-tv-1920.png" alt="A later Aquarium 4K frame showing swimming fish"><br>Aquarium animation</td>
    <td><img src="art/screenshots/tv-screensaver-lower-tv-1920.png" alt="More installed screensavers on Fire TV"><br>More installed screensavers</td>
  </tr>
  <tr>
    <td><img src="art/screenshots/advanced-tv-1920.png" alt="Advanced page with a focused read-only Diagnostics action"><br>Advanced</td>
    <td><img src="art/screenshots/diagnostics-tv-1920.png" alt="Diagnostics showing selected screensaver and unavailable privileged DreamService inspection"><br>Diagnostics</td>
    <td><img src="art/screenshots/diagnostics-report-tv-1920.png" alt="Diagnostic report distinguishing display mode, UI content size, memory and local app exits"><br>Diagnostic report</td>
  </tr>
</table>

These are bundled previews of the animated screensavers available in the on-TV catalog.

<table>
  <tr>
    <td><img src="app/previews/c5ab28b8e4ab7e528b914f82d4075dba660cad7301c7a3de7df549a408df0563.jpg" alt="Perseid Passage with meteors crossing a dark starfield"><br>Perseid Passage</td>
    <td><img src="app/previews/982f942f0f840c97179a8be9447f316c6c2e87123d456cf65468af4a98921c76.jpg" alt="Split-Flap Drift with an illuminated floating flip clock"><br>Split-Flap Drift</td>
    <td><img src="app/previews/1cb8c639b6468d88e3da29d58d85c6f052be0feaf61f8a10ced85ef75750f130.jpg" alt="Canopy Drift with a green treetop canopy and drifting leaves"><br>Canopy Drift</td>
  </tr>
  <tr>
    <td><img src="app/previews/38e1682f25341c01af673c7e41b6779490496d775c67b0ddd736da05478116ab.jpg" alt="Contour Flow with animated flowing contour lines"><br>Contour Flow</td>
    <td><img src="app/previews/64fdc74c5e5d23a9397cc81faaa331404405ccde7e8f01264ee04e91c38daa80.jpg" alt="Skyburst Nocturne with bright colorful fireworks over a skyline"><br>Skyburst Nocturne</td>
    <td><img src="app/previews/d752d5739810907e8d0434f3941070bf2d83adbc902011f827187fe4afd909b6.jpg" alt="Pulse Circuit with animated neon geometric forms"><br>Pulse Circuit</td>
  </tr>
  <tr>
    <td><img src="app/previews/f480c9f49b2b6f05b3a0351e076eb89840fe1a3b400130b0f2e53d9c06270f71.jpg" alt="Hearth Presence modern linear fire captured from its final release on physical Fire TV"><br>Hearth Presence</td>
    <td><img src="app/previews/d4c99da3be34c14e061e1968439f9e7c2b1bd6ffa1e00578a29ec423a37a1bab.jpg" alt="Slot Vision native three-dimensional casino cabinet and simulated flying coins"><br>Slot Vision</td>
  </tr>
</table>

## License

This package is open-sourced software licensed under the [MIT license](LICENSE).
