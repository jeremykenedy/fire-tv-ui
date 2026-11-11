# Configuration

The 1.18.0 additions below are a working release draft. Final native verification and publication are pending.

| Setting | Behavior |
| --- | --- |
| Screensavers | Configure or preview any installed saver, choose the active saver, and manage timers and installations. |
| UI settings | Configure layout and focus, widgets, status bar, favorites and cards. |
| Theme and wallpaper | Choose wallpapers, backgrounds, transparent overlays, holiday palettes and icon packs, or save and restore a named UI setup. |
| Remote controls | Configure remote actions and accessibility. |
| Updates | Check installed apps, compare versions, install verified updates and recover missing shared icons. |
| Advanced | Open diagnostics, updates and preview media credits. |
| Backup/Restore | Save or restore the persistent TV backup, or restore a backup sent by the CLI. |
| Startup and protection | Enable or disable startup and Home routing; protect settings and block Amazon updates. |
| About | View the app name, version, credits, and privacy information. |
| Help | Read the feature guide and screensaver setup instructions. |

## Layout and focus

Open **UI settings > Layout and focus**. Apps per row offers presets and a custom whole number from 3 to 12. Auto/default uses five columns. Existing saved values remain unchanged until you choose a new value. Changing columns preserves app order. Vertical scrolling remains the default. Choose Horizontal to scroll apps sideways, then select Auto (one row) or a maximum of one to six rows. In horizontal mode, Visible apps per row controls tile size; additional apps remain reachable by scrolling. Large tiles can also require vertical scrolling; remote focus brings the selected app into view. Changing direction or maximum rows rebuilds Home once when returning from settings. Your app order, favorites and existing All apps button choice are preserved.

Focus style offers Default, Border only, and Color and border. Choose a preset color or enter a six-digit RGB hex color for the fill and border. Default preserves the existing focus behavior while correcting a low-contrast focused background behind transparent icon-pack artwork. The artwork itself is not recolored. Border only leaves the resting tile background visible.

### App focus growth and shadow

In **UI settings > Layout and focus**, **App focus growth** offers Default (18%), Off, 6%, 12%, 24% and 30%. Off removes enlargement while keeping the other focus styling. **App focus shadow** offers Off, Default (18 dp), Large (28 dp) and Extra large (38 dp). Existing installations keep their current choices; without an override, growth and shadow retain the original 18% and 18 dp behavior. A saved disabled shadow stays disabled until you change it.

Growth and shadow changes apply on return with Back, including when focusing the same app tile again; a process restart is not required. The native Settings route still returns Home focus to the first app, including visits where no setting changed. Preserving the previously focused app through that route remains unresolved.

These choices are validated preferences included in launcher backups and saved setups. No new permissions or migration are required.

## Settings transitions

In **UI settings > Layout and focus**, Settings transitions offers Default, None, Fade and Short slide. These options control native settings pages opening and closing. Default preserves existing behavior; None removes those page transitions. Fade and Short slide finish quickly and respect disabled system animations. Choosing Default removes the override. This control does not replace Home tile focus animation or the upstream pop-up menu animations.

## Icon styles

Open **Theme and wallpaper > Icon pack** to select Clean line or Muted solid. Both use installed apps' recognizable original artwork: Clean line adds a thin frame, while Muted solid adds a subdued plate and lowers saturation. These built-in choices need no additional app or download. None (original artwork), Transparent and installed external packs remain available. Explicit custom artwork is preserved when switching packs. Focus fill and border controls remain independent.

## Cards

Open **UI settings > Cards**. Select a Home card or press Menu for **Open**, **Hide this title**, and **Hide all cards from &lt;app&gt;**. The menu names the current title. Open keeps the original provider action; title hiding uses the exact selected item identity. The app action hides every feed published by that app while keeping its app tile.

Turning cards Off removes the reserved card band when widgets are also disabled. Enabled widgets retain their row. **Channel Cards** manages channel visibility and individual items. **Card timers** sets the global title interval and optional individual channel timers, including Manual and custom whole seconds. Each channel can return to the global timer independently.

## Backgrounds and saved setups

Open **Theme and wallpaper > Backgrounds and overlays** to select a full background, a transparent overlay, or a holiday palette. Each layer has its own motion and intensity controls; still and off modes are available. See [backgrounds and overlays](backgrounds-and-overlays.md).

Open **Theme and wallpaper > Saved setups** to save the current presentation and layout under a name, then switch between saved setups. Applying a setup can keep the current icons. TV power controls and account connections remain unchanged. See [saved setups](saved-setups.md) for included settings and local storage limits.

## Screensavers

Use the dedicated Screensavers section. Configure and Preview belong to each installed saver and do not select it as active. Set active is a separate action; the active saver is labeled. Returning from a saver restores the list position and remote focus. The TV's selected screensaver, enabled state and configured delay remain authoritative. There is no second launcher mode or fixed 90-second option.

See [device controls](device-controls.md) for timers, protection, backups and platform-specific behavior, [featured cards](featured-cards.md) for card intervals, and [animated backgrounds](animated-backgrounds.md) for motion settings.
