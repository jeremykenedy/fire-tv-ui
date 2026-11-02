<p align="center">
  <a href="https://github.com/jeremykenedy/fire-tv-ui#gh-light-mode-only"><picture><source media="(max-width: 600px) and (prefers-reduced-motion: reduce)" srcset="art/banner-mobile-still-light.98d256baacad.svg"><source media="(prefers-reduced-motion: reduce)" srcset="art/banner-still-light.cc4cdf921b24.svg"><source media="(max-width: 600px)" srcset="art/banner-mobile-light.7cf87e68fb8d.svg"><img src="art/banner-light.e99322de878a.svg#gh-light-mode-only" alt="Fire TV UI" width="800"></picture></a>
  <a href="https://github.com/jeremykenedy/fire-tv-ui#gh-dark-mode-only"><picture><source media="(max-width: 600px) and (prefers-reduced-motion: reduce)" srcset="art/banner-mobile-still-dark.a324d51d0cd9.svg"><source media="(prefers-reduced-motion: reduce)" srcset="art/banner-still-dark.c72c1431c05d.svg"><source media="(max-width: 600px)" srcset="art/banner-mobile-dark.6cfcbd32ad7d.svg"><img src="art/banner-dark.bf78d582be0e.svg#gh-dark-mode-only" alt="Fire TV UI" width="800"></picture></a>
</p>

<p align="center">A customizable Fire TV home screen, with a separately installed source preview.</p>

<p align="center">
  <a href="https://github.com/jeremykenedy/fire-tv-ui/releases"><img src="https://img.shields.io/github/downloads/jeremykenedy/fire-tv-ui/total" alt="Total downloads"></a>
  <a href="https://github.com/jeremykenedy/fire-tv-ui/releases/latest"><img src="https://img.shields.io/github/v/release/jeremykenedy/fire-tv-ui" alt="Latest stable version"></a>
  <a href="https://github.com/jeremykenedy/fire-tv-ui/actions/workflows/ci.yml"><img src="https://github.com/jeremykenedy/fire-tv-ui/actions/workflows/ci.yml/badge.svg" alt="Native build and checks"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License"></a>
</p>

<p align="center">
  <a href="https://github.com/jeremykenedy"><img src="https://img.shields.io/github/followers/jeremykenedy?label=Follow&amp;style=social" alt="Follow"></a>
  <a href="https://github.com/jeremykenedy/fire-tv-ui"><img src="https://img.shields.io/github/stars/jeremykenedy/fire-tv-ui?label=Star&amp;style=social" alt="Star"></a>
  <a href="https://github.com/sponsors/jeremykenedy"><img src="https://img.shields.io/badge/Sponsor-EA4AAA?logo=githubsponsors&amp;logoColor=white" alt="Sponsor"></a>
</p>

## Choose a version

| Version | Use it for | Start here |
| --- | --- | --- |
| Stable 1.17.0 | The existing Fire TV UI installation and supported 1.x updates. | [Stable download](https://github.com/jeremykenedy/fire-tv-ui/releases/tag/v1.17.0) |
| Source Preview 2.0.0-alpha.1 | Testing the new native interface in a separate alpha app. | [Source preview guide](docs/source-preview.md) |

Stable 1.17.0 remains the latest stable release. The alpha preview uses a separate package and separate data; it does not upgrade or migrate a 1.x installation. The alpha is an early preview with the scope and limits described below. See the [source preview guide](docs/source-preview.md) for scope and limits.

The installation, feature guides and screenshots below describe stable 1.17 unless explicitly labeled as source preview.

## Table of Contents

- [Choose a version](#choose-a-version)
- [Source preview](#source-preview)
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

## Source preview

The separately installed alpha provides a native Home interface, app organization, cards/widgets, appearance controls and local presentation setups. It keeps production Home routing and production migration outside the preview contract. Provider content and system actions depend on platform support and explicit permissions.

Use the [source preview guide](docs/source-preview.md) for installation, included features, data boundaries and known limits. Optional work beyond the frozen first cut is excluded.


## Requirements

- Fire TV or Android TV running Android 8.1 / API 27 or newer.
- ADB access for installation and the one-time screensaver permission grant.
- Python 3.10+, JDK 21, Android SDK platform 36, and build-tools 36.0.0 to build locally.

## Installation

Stable Fire TV UI 1.17.0 and the isolated Source Preview 2.0.0-alpha.1 are available from [published releases](https://github.com/jeremykenedy/fire-tv-ui/releases). The preview uses a separate package and settings; see the [preview guide](docs/source-preview.md).

The repository installer is currently unavailable. The source-native production build is a candidate and is not a verified upgrade; keep the existing installation and its data. See [Installation and updates](docs/installation.md) for the current status and backup guidance.

## Quick Start

Open **Fire TV UI**, accept the accessibility disclosure if you want remote shortcuts, and use the preferences icon in the top bar to open Settings.

Press **Back** or **Menu** to dismiss the Settings panel. **Home** returns to the launcher with the panel closed when Home routing is enabled.

Choose **Settings > Screensavers** to select an installed screensaver. Each saver has its own **Configure**, **Preview** and **Set active** actions. Configure and Preview leave the selected saver unchanged; returning restores your position in the list. Timers, the installation catalog and memory usage remain available.

Choose **Startup and protection** in Settings to enable or disable Fire TV UI as the startup and Home app, protect selected settings from reversion, or block Amazon update traffic locally. Streaming, weather, CDNs, and screensaver downloads remain available. No analytics, tracking, rating, or project prompts are included.

## Features

| Feature | What it does | Guide |
| --- | --- | --- |
| Home layout | Choose custom columns, optional horizontal rows and readable focus styles. | [Configuration](docs/configuration.md) |
| Featured cards | Keep feeds fixed, set global or per-channel timers, and order or hide individual items. | [Featured cards](docs/featured-cards.md) |
| Backgrounds and overlays | Add independent animation over your wallpaper, with still choices and holiday palettes. | [Backgrounds and overlays](docs/backgrounds-and-overlays.md) |
| Saved setups | Save named appearances and layouts, then switch back with optional icon restoration. | [Saved setups](docs/saved-setups.md) |
| Themes and widgets | Choose wallpapers, clocks, weather and other widgets. | [Configuration](docs/configuration.md) |
| Icon styles | Choose built-in Clean line or Muted solid artwork treatments, or keep an existing pack. | [Icon styles](docs/configuration.md#icon-styles) |
| Settings motion | Choose Default, None, Fade or Short slide for native settings pages. | [Settings transitions](docs/configuration.md#settings-transitions) |
| Screensavers | Select, preview, configure and install reviewed screensaver releases. | [Screensavers](docs/device-controls.md#screensavers) |
| Launcher idle | Follow the selected TV screensaver when the launcher is idle. | [Platform limits](docs/device-controls.md#launcher-idle-activation) |
| Timers and Home routing | Manage TV timers and the startup and Home app. | [Device controls](docs/device-controls.md) |
| Local protection | Preserve saved choices and optionally block known Amazon updater traffic. | [Protection](docs/device-controls.md#startup-and-protection) |
| App updates | Check stable releases and confirm verified replacement installs. | [Updates](docs/updates.md) |
| Native TV tiles | Open Amazon Home or the built-in Live TV guide on supported Fire TVs. | [Native tiles](docs/factory-home.md) |
| Network and IP | View local connection details and open the TV's IP configuration. | [Network settings](docs/device-controls.md#network-and-ip) |
| Advanced diagnostics | Inspect device status and save a local report with explicit measurement limits. | [Diagnostics](docs/device-controls.md#advanced-diagnostics) |
| Persistent backups | Save launcher preferences on the TV or transfer a backup from the CLI. | [Backups](docs/device-controls.md#persistent-tv-backups) |

## Input icons

The [48 HD icons](docs/icons.md) cover audio brands, consoles, media players, VR, and computers. Each tile contains a centered logo at 1920x1080, with its original proportions and no extra caption. The standalone icon script copies them into the TV's shared `Pictures/Icons` folder and makes them visible to image pickers.

The APK also recovers missing shared icons when storage access is available. Open **Settings > Updates** for access status and retry; existing custom files are preserved. The release icon ZIP and standalone icon script remain available; see the [icon guide](docs/icons.md).

Long press the app or input tile, choose **Change Icon**, then select an image. For LTvLauncher, use **Set Custom Banner** on an app. See the [icon guide](docs/icons.md) for the full catalog and standalone installation.

## Configuration

Use the preferences icon to open the remote-friendly Settings menu. [Configuration](docs/configuration.md) describes each entry; [device controls](docs/device-controls.md) covers screensavers, timers, protection and backups.


## Building

Build an unsigned production-package candidate from the maintained native source:

```bash
python3 scripts/build.py --unsigned --output-dir /tmp/fire-tv-production
```

The output is unsigned and cannot be installed as a production update. Signing, migration, parity and device acceptance remain separate gates. See [Build and source layout](docs/building.md) for toolchain and signing requirements.

## Testing

```bash
bash scripts/test.sh
```

The script builds an isolated unsigned preview and runs the source-native host and production contract checks. Native device checks and physical-TV acceptance require separate verification; see [Verification](docs/verification.md).

## Documentation

- [Source Preview 2.0.0-alpha.1 guide](docs/source-preview.md)
- [Documentation index](docs/index.md)
- [Configuration](docs/configuration.md)
- [Full screenshot gallery](docs/screenshots.md)
- [HD input and app icons](docs/icons.md)
- [Installation and updates](docs/installation.md)
- [Build and source layout](docs/building.md)
- [Verification](docs/verification.md)
- [Screensaver previews](docs/screensaver-previews.md)
- [Device controls and backups](docs/device-controls.md)
- [Featured cards and personal Plex](docs/featured-cards.md)
- [Animated menu backgrounds](docs/animated-backgrounds.md)
- [Backgrounds, overlays and holiday themes](docs/backgrounds-and-overlays.md)
- [Saved UI setups](docs/saved-setups.md)
- [Fire TV UI 1.17.0 release notes and upgrade guide](docs/releases/v1.17.0.md)
- [Fire TV UI 1.13.0 release notes and upgrade guide](docs/releases/v1.13.0.md)
- [Fire TV UI 1.11.1 release notes and upgrade guide](docs/releases/v1.11.1.md)
- [Fire TV UI 1.11.0 release notes and upgrade guide](docs/releases/v1.11.0.md)
- [Fire TV UI 1.10.1 release notes and upgrade guide](docs/releases/v1.10.1.md)
- [Fire TV UI 1.10.0 release notes and upgrade guide](docs/releases/v1.10.0.md)
- [Fire TV UI 1.9.2 release notes and upgrade guide](docs/releases/v1.9.2.md)
- [Fire TV UI 1.9.1 release notes and upgrade guide](docs/releases/v1.9.1.md)
- [Fire TV UI 1.6.0 release notes and upgrade guide](docs/releases/v1.6.0.md)
- [Fire TV UI 1.4.1 release notes and upgrade guide](docs/releases/v1.4.1.md)
- [Fire TV UI 1.3.2 release notes and upgrade guide](docs/releases/v1.3.2.md)
- [Fire TV UI 1.3.1 release notes and upgrade guide](docs/releases/v1.3.1.md)
- [Fire TV UI 1.3.0 release notes and upgrade guide](docs/releases/v1.3.0.md)
- [Fire TV UI 1.2.0 release notes and upgrade guide](docs/releases/v1.2.0.md)
- [Fire TV UI 1.1.3 release notes and upgrade guide](docs/releases/v1.1.3.md)
- [Fire TV UI 1.1.2 release notes and upgrade guide](docs/releases/v1.1.2.md)
- [Fire TV UI 1.1.1 release notes and upgrade guide](docs/releases/v1.1.1.md)

## Screensaver Skill

[Add a Fire TV screensaver](skills/add-fire-tv-screensaver/SKILL.md) describes how to add new screensaver repos, include a useful image in their APKs, verify them in Fire TV UI, and register them with the Fire TV CLI installer.

Copy `skills/add-fire-tv-screensaver/` into `~/.codex/skills/` to install it locally. Invoke it with `$add-fire-tv-screensaver` and provide the screensaver repo. The CLI registry remains in the separate Fire TV CLI project.

## Screenshots

Fire TV UI 1.17.0, the current stable release, running on an Android TV emulator. This example shows stock apps without personal accounts; your installed apps, inputs, widgets and background determine your Home layout.

<p align="center"><a href="docs/screenshots.md"><img src="art/screenshots/home-ui117-code78-api34.png" alt="Fire TV UI 1.17 Home with a purple gradient, status bar and app tiles" width="900"></a></p>

See the [current Home and Settings captures](docs/screenshots.md#current-stable-interface) or the separately labeled [early source preview](docs/source-preview.md#screenshots).

## License

This package is open-sourced software licensed under the [MIT license](LICENSE).
