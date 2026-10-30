# Menu backgrounds

Open **Settings > Theme and wallpaper > Backgrounds and overlays**. Choose a moving or still style to see it in the preview and behind Home. The same background follows supported Fire TV UI menu panels, including this settings screen.

| Style | Appearance |
| --- | --- |
| Quiet particles | Small drifting lights |
| Aurora ribbons | Colored flowing ribbons |
| Connected lights | Particles joined by fine lines |
| Prism light | Moving geometric light |
| Flowing silk | Layered soft curves |
| Soft bokeh | Defocused drifting lights |
| Drifting stars | A restrained star field |
| Warm embers | Warm rising particles |
| Ocean ribbons | Flowing blue-green bands |
| Gentle rain | Fine moving rain strokes |
| Orbital arcs | Slowly moving curved paths |
| Light rays | Soft sweeping beams |
| Still: Midnight blue | Dark navy with broad, soft blue light |
| Still: Warm graphite | Neutral charcoal with a warm highlight |
| Still: Forest dusk | Deep green with muted sage light |
| Still: Plum velvet | Deep plum with a soft mauve highlight |

**Off: use my wallpaper** leaves animation disabled and restores your existing wallpaper without replacing its saved choice. Off is the default for an installation without a saved animation. Existing installations retain their selected style and controls.

| Control | Choices | Default |
| --- | --- | --- |
| Speed | 25%, 50%, 100% | 50% |
| Intensity | 25%, 50%, 75%, 100% | 50% |
| Brightness | 25%, 50%, 75%, 100% | 50% |

The four still styles have no motion and do not schedule animation frames. Intensity changes their soft lighting; Brightness changes their overall darkness. Speed appears only for animated styles. Off hides the effect controls while retaining their saved values. Changing styles does not reset any controls.

Style and controls are included in portable launcher backups. Effects use native drawing without downloaded backgrounds or a browser. Their frame callbacks pause when the corresponding menu loses focus, becomes hidden or detaches. They do not send input events or deliberately reset the TV inactivity timer. These are menu backgrounds, not screensavers, and Prism light is separate from the Prism Clock app.

The [current native settings captures](screenshots.md#fire-tv-ui-1140-code-75) show all four still palettes. Earlier native captures show three animated styles. See [verification and platform limits](verification.md#fire-tv-ui-1120-version-code-72) for the scope of the native checks. Menu animation does not guarantee the same performance on every TV.
