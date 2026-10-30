# Screenshots

## Fire TV UI 1.16.0, code 77

Native Android TV API 34 captures at 1920x1080 from the final signed release candidate `1df13d6b`. Saved setups restore appearance, layout and card presentation. Cards contains Channel Cards and global/per-channel timers. Backgrounds and overlays can be configured separately, with 41 holiday and seasonal palettes. Existing settings remain the defaults.

<table>
<tr>
<td><img src="../art/screenshots/saved-setups-ui116-code77-api34.png" alt="Fire TV UI 1.16 native saved setups"><br>Saved setups</td>
<td><img src="../art/screenshots/cards-menu-ui116-code77-api34.png" alt="Fire TV UI 1.16 native cards menu"><br>Cards menu</td>
<td><img src="../art/screenshots/card-timers-ui116-code77-api34.png" alt="Fire TV UI 1.16 native card timers"><br>Card timers</td>
</tr>
<tr>
<td><img src="../art/screenshots/card-timer-options-ui116-code77-api34.png" alt="Fire TV UI 1.16 native global timer options"><br>Global timer options</td>
<td><img src="../art/screenshots/backgrounds-overlays-ui116-code77-api34.png" alt="Fire TV UI 1.16 native backgrounds and overlays"><br>Backgrounds and overlays</td>
<td><img src="../art/screenshots/overlay-controls-ui116-code77-api34.png" alt="Fire TV UI 1.16 native overlay controls"><br>Overlay controls</td>
</tr>
<tr>
<td><img src="../art/screenshots/holiday-themes-ui116-code77-api34.png" alt="Fire TV UI 1.16 native holiday themes"><br>Holiday themes</td>
<td><img src="../art/screenshots/holiday-options-ui116-code77-api34.png" alt="Fire TV UI 1.16 native holiday appearance options"><br>Holiday appearance options</td>
<td><img src="../art/screenshots/about-ui116-code77-api34.png" alt="Fire TV UI 1.16 native release version"><br>Release version</td>
</tr>
</table>

[Capture provenance](../art/screenshots/ui116-code77-provenance.json) records the exact APK and unchanged image hashes. These captures contain no personal accounts or library history. Native physical tests separately covered independent card timing, transparent overlays and saved setup restore. Video setups were checked on the TV emulator, including safe failure when a source is missing. See [verification](verification.md#fire-tv-ui-1160-version-code-77) for performance measurements and remaining limits. Earlier captures retain their original version labels below.

## Fire TV UI 1.15.0, code 76

Native Android TV API 34 captures at 1920x1080 from the final signed APK `f8a7ac4d`. Horizontal scrolling, built-in icon styles and settings transitions are optional. Vertical layout and existing saved choices remain the defaults.

<table><tr>
<td><img src="../art/screenshots/layout-ui115-code76-api34.png" alt="Fire TV UI 1.15 native layout and focus"><br>Layout and focus</td>
<td><img src="../art/screenshots/horizontal-first-ui115-code76-api34.png" alt="Fire TV UI 1.15 native first horizontal tile"><br>First horizontal tile</td>
<td><img src="../art/screenshots/horizontal-last-ui115-code76-api34.png" alt="Fire TV UI 1.15 native last horizontal tile"><br>Last horizontal tile</td>
</tr><tr>
<td><img src="../art/screenshots/horizontal-two-rows-ui115-code76-api34.png" alt="Fire TV UI 1.15 native two horizontal rows"><br>Two horizontal rows</td>
<td><img src="../art/screenshots/transitions-ui115-code76-api34.png" alt="Fire TV UI 1.15 native settings transitions"><br>Settings transitions</td>
<td><img src="../art/screenshots/icon-packs-ui115-code76-api34.png" alt="Fire TV UI 1.15 native built-in icon choices"><br>Built-in icon choices</td>
</tr><tr>
<td><img src="../art/screenshots/clean-line-ui115-code76-api34.png" alt="Fire TV UI 1.15 native clean line artwork"><br>Clean line artwork</td>
</tr></table>

[Capture provenance](../art/screenshots/ui115-code76-provenance.json) records the exact APK and unchanged PNG hashes. Native row/focus navigation, saved transition choices and icon selection were checked. These captures do not establish physical remote Home, universal animation performance or owner acceptance of other screensaver visuals. Earlier captures remain labeled below.

## Fire TV UI 1.14.0, code 75

Native Android TV API 34 settings at 1920x1080 from the final signed APK `08ca77dd`. The four still palettes use the same Home and menu background layer as the existing animations. Speed is hidden for still styles; Intensity and Brightness remain available. All captures use 50% controls.

<table><tr>
<td><img src="../art/screenshots/background-midnight-ui114-code75-api34.png" alt="Fire TV UI 1.14 Midnight blue still background"><br>Midnight blue</td>
<td><img src="../art/screenshots/background-graphite-ui114-code75-api34.png" alt="Fire TV UI 1.14 Warm graphite still background"><br>Warm graphite</td>
<td><img src="../art/screenshots/background-forest-ui114-code75-api34.png" alt="Fire TV UI 1.14 Forest dusk still background"><br>Forest dusk</td>
</tr><tr>
<td><img src="../art/screenshots/background-plum-ui114-code75-api34.png" alt="Fire TV UI 1.14 Plum velvet still background"><br>Plum velvet</td>
</tr></table>

[Capture provenance](../art/screenshots/ui114-code75-provenance.json) records the exact APK and unmodified PNG hashes. Native controls, still-frame stability and backup round trips were checked. Physical TV appearance remains device dependent. Earlier release screens remain below under their original versions.

## Fire TV UI 1.13.0, code 74

Native Android TV API 34 captures at 1920x1080 from the exact signed APK `0592d60d`. The dedicated Screensavers area keeps Configure, Preview and Set active beside each saver. UI settings groups layout and card controls, and Updates keeps version details and recovery actions visible. Installed saver names and thumbnails depend on the device; their presence does not establish visual acceptance of those savers.

<table><tr>
<td><img src="../art/screenshots/settings-ui113-code74-api34.png" alt="Fire TV UI 1.13 native UI settings panel"><br>UI settings</td>
<td><img src="../art/screenshots/screensavers-ui113-code74-api34.png" alt="Fire TV UI 1.13 separate Configure Preview and Set active actions beside each installed saver"><br>Screensaver management</td>
<td><img src="../art/screenshots/cards-ui113-code74-api34.png" alt="Fire TV UI 1.13 featured card controls with saved default eight-second interval"><br>Featured cards</td>
</tr><tr>
<td><img src="../art/screenshots/updates-ui113-code74-api34.png" alt="Fire TV UI 1.13 update screen with installed and available versions and missing icon access action"><br>Updates</td>
<td><img src="../art/screenshots/layout-focus-ui113-code74-api34.png" alt="Fire TV UI 1.13 native custom layout and icon focus settings restored to defaults"><br>Layout and focus</td>
</tr></table>

[Capture provenance](../art/screenshots/ui113-code74-provenance.json) records the unchanged PNG hashes. Native tests verified inactive-saver configuration and preview fallback with selection/timer preservation and exact focus/scroll return. Custom seven-second timing was saved and restored through a native backup round trip; no published feeds were available to verify live title cadence. These captures do not establish physical remote Home routing, Alexa, receiver behavior or screenshots of newer screensaver visuals. Earlier release evidence remains labeled below.

## Fire TV UI 1.12.0, code 72

Actual API 34 TV emulator settings from the exact final signed APK `930660de` at 1920x1080. Flowing silk, Soft bokeh and Light rays are three of twelve animated styles, plus Off. Each capture shows native 50% controls; Brightness is below the current scroll fold. The unchanged raw settings images contain no personal cards or library history.

<table><tr>
<td><img src="../art/screenshots/silk-settings-ui112-code72-api34.png" alt="Final signed code 72 API 34 Flowing silk background settings"><br>Flowing silk</td>
<td><img src="../art/screenshots/bokeh-settings-ui112-code72-api34.png" alt="Final signed code 72 API 34 Soft bokeh background settings"><br>Soft bokeh</td>
<td><img src="../art/screenshots/rays-settings-ui112-code72-api34.png" alt="Final signed code 72 API 34 Light rays background settings"><br>Light rays</td>
</tr></table>

[Capture provenance](../art/screenshots/ui112-code72-provenance.json) records full APK, source and raw PNG hashes. The six background sources and 14 compiled background class payloads match the initial physical code 71 background candidate; this is not full DEX equality. That separate physical gate selected all 12 styles, checked saved values and maximum controls, reached ordinary Aerial at 301.296 seconds and recorded zero UI presents during a stable 3-second Dream window. [Verification](verification.md#fire-tv-ui-1120-version-code-72) distinguishes those observations from the final native same-process restore-cache check. Neither these stills nor the cache check proves physical hardware Home delivery or universal performance. Earlier feature evidence remains below under its own version.

## Fire TV UI 1.11.1, code 70

Signed `14676d28` output on the API 34 TV emulator at native 1920x1080. Entry 36 is the verified public Lumen Breaker 1.0.0 release. Its thumbnail comes from an actual native code 3 capture. The host's Preview it action opened its real PreviewActivity for 40 seconds, and Back returned with Slot Vision still selected. Both launcher DEX files and all 35 previous catalog entries match public code 69.

<table><tr>
<td><img src="../art/screenshots/lumen-picker-ui1111-code70-api34.png" alt="Code 70 installed picker showing the full native Lumen thumbnail while Slot Vision stays selected"><br>Native picker image</td>
<td><img src="../art/screenshots/lumen-catalog-ui1111-code70-api34.png" alt="Code 70 installation catalog showing Nibbles and the new Lumen Breaker entry with its description"><br>Catalog entry 36</td>
<td><img src="../art/screenshots/lumen-preview-ui1111-code70-api34.png" alt="Lumen gameplay after the code 70 host opened its real native PreviewActivity"><br>Actual app Preview</td>
</tr><tr>
<td><img src="../art/screenshots/lumen-options-ui1111-code70-api34.png" alt="Lumen's declared native settings activity with ball speed, palette, formations, density and balls controls"><br>App-owned settings</td>
<td><img src="../art/screenshots/lumen-notice-ui1111-code70-api34.png" alt="Code 70 offline reader shows the complete Lumen original-work notice"><br>Offline Lumen notice</td>
</tr></table>

Lumen's declared SettingsActivity was opened directly and returned with Back. The selected-saver Settings button continued to address Slot because Dream selection was preserved. The actual Advanced > Preview media credits route displayed the complete Lumen notice and scrolled through its Apache text. The native TextView exactly matched the bundled 11,358-character license body; this is content proof, not a claim that every license page was visually read. A poorly framed Apache-header image and an immediate Back capture holding a stale game surface are excluded. Back is established by the returned Activity and the separately settled picker image. All 14 UI preferences, 19 protected OS values, 34 APK identities, provider settings, grants, UID/appops modes, external files and stopped states were restored apart from the intended stopped UI 70 upgrade. [Capture provenance](../art/screenshots/ui1111-code70-provenance.json) records the unchanged raw bytes. These are native emulator packaging and routing captures; they add no physical Home, system Dream binding or performance claim. Earlier feature evidence remains below with its release identified.


## Fire TV UI 1.11.0, code 69

Signed `d5abce3b` output on the API 34 TV emulator at native 1920x1080. The catalog now has 35 entries and pins the verified public Nibbles HD 1.0.0 APK. Its thumbnail comes from actual physical gameplay, and Preview it opens the app's real PreviewActivity without changing the selected Slot Vision Dream.

<table><tr>
<td><img src="../art/screenshots/nibbles-catalog-ui111-code69-api34.png" alt="Code69 catalog showing the 35th entry, installed Nibbles HD"><br>New catalog entry</td>
<td><img src="../art/screenshots/nibbles-preview-ui111-code69-api34.png" alt="Actual Nibbles HD native game opened by the code 69 Preview action"><br>Native Preview</td>
<td><img src="../art/screenshots/nibbles-options-ui111-code69-api34.png" alt="Nibbles HD's declared settings activity with its own game and appearance controls"><br>App-owned settings</td>
</tr><tr>
<td><img src="../art/screenshots/nibbles-credits-ui111-code69-api34.png" alt="Code69 offline reader showing the Nibbles original-work notice"><br>Nibbles notice</td>
<td><img src="../art/screenshots/nibbles-license-ui111-code69-api34.png" alt="Code69 offline reader showing the full bundled Nibbles Apache license"><br>Full license</td>
</tr></table>

The declared Nibbles settings activity was opened directly, navigated with the D-pad and closed with Back. The selected-saver Settings button remained associated with Slot because the gate did not change Dream selection. Both new offline texts opened through Advanced and scrolled with D-pad/Page Down. The actual 68-to-69 update preserved mixed Home values and a missing override key before Main initialization. Permission-denied Home on/off restored all four prior values and key presence. All 14 UI preferences, 19 OS values, 34 APK identities, readable providers, grants, appops, cached images, external files and stopped states were restored apart from the intended stopped UI 69 upgrade. [Capture provenance](../art/screenshots/ui111-code69-provenance.json) records unchanged raw images. These emulator checks do not establish physical Fire OS Home behavior, a new Dream binding result or performance. The sections below preserve historical feature evidence for their named releases.

## Fire TV UI 1.10.1, code 68

Current signed `ab7b5019` output on the API 34 TV emulator. The catalog still has 34 entries, with the verified public Hearth Presence and Prism Clock 1.1.0 APKs. Both launcher DEX files match public code 67. The picker uses current physical thumbnails. Screensaver settings opened each app's real native controls: Hearth retains eleven provider rows and Prism has twelve, including independent, nonrandom Show clock. These images are emulator output; they do not establish physical performance or system Dream binding.

<table><tr>
<td><img src="../art/screenshots/hearth-prism-picker-ui1101-code68-api34.png" alt="Code 68 picker with the new real Prism thumbnail and its temporary selected state"><br>Current native picker</td>
<td><img src="../art/screenshots/hearth-options-ui1101-code68-api34.png" alt="Hearth native wood settings with the existing Flame height control"><br>App-owned Hearth options</td>
<td><img src="../art/screenshots/prism-options-ui1101-code68-api34.png" alt="Prism native settings with the new Show clock switch"><br>App-owned Prism options</td>
</tr><tr>
<td><img src="../art/screenshots/hearth-preview-ui1101-code68-api34.png" alt="Actual Hearth wood fireplace preview opened from its native controls"><br>Hearth Preview</td>
<td><img src="../art/screenshots/prism-clock-off-ui1101-code68-api34.png" alt="Actual Prism surface with clock, date and seconds absent"><br>Show clock Off</td>
<td><img src="../art/screenshots/prism-clock-on-ui1101-code68-api34.png" alt="Actual Prism surface with the clock and date visible again"><br>Show clock On</td>
</tr></table>

The remote toggled Show clock Off and On, opened both previews and returned with Back. The previous On value was retained. Settings > Advanced > Preview media credits opened the bundled reader, which scrolled with the D-pad and closed with Back. Full Hearth and Prism notices and applicable licenses are byte-verified in the signed APK. The original Slot Vision selection, all fourteen UI preferences, nineteen OS values, readable providers, external files, appops and accessibility/guard state were restored. [Capture provenance](../art/screenshots/ui1101-code68-provenance.json) records the exact images.

## Fire TV UI 1.10.0, code 67

Current native output from signed `1a64f59c` on API 34. The catalog contains 34 screensavers. Its new Skycast Weather entry uses the verified public 1.0.0/code 4 APK and a real word-free physical thumbnail. The host Preview it action opened its exported PreviewActivity and visibly rendered the clouds-only film; Back returned without changing the selected screensaver. The public app's native Show weather control and the host's offline attribution reader are shown below. Weather settings retained the original no-city/display choices and the new default Show weather On after cleanup. No personal Plex data was used.

<table><tr>
<td><img src="../art/screenshots/weather-picker-ui110-code67-api34.png" alt="Final code 67 picker showing the real text-free Weather thumbnail without changing Slot Vision selection"><br>Word-free preview</td>
<td><img src="../art/screenshots/weather-catalog-ui110-code67-api34.png" alt="Final code 67 catalog entry describes optional weather and its real ambient cloud flight"><br>34th catalog entry</td>
<td><img src="../art/screenshots/weather-options-ui110-code67-api34.png" alt="Published Weather native settings with Show weather information turned Off and no city configured"><br>Native Weather options</td>
</tr><tr>
<td><img src="../art/screenshots/weather-preview-ui110-code67-api34.png" alt="Actual clouds-only film reached through the final host's direct Weather preview action"><br>Direct native preview</td>
<td><img src="../art/screenshots/credits-start-ui110-code67-api34.png" alt="Final code 67 offline media credits reader"><br>Offline credits</td>
<td><img src="../art/screenshots/credits-weather-ui110-code67-api34.png" alt="Weather film creator, source, license and resize attribution visible at the end of offline credits"><br>Weather attribution</td>
</tr></table>

## Fixed feeds and backgrounds, code 66 runtime

Both DEX files are byte-identical between code 66 and final code 67. These unretouched API 34 emulator captures show signed `3fe275c0` with two owned sample feeds, not a personal Plex library. The same two card positions retain coherent child titles and artwork while rotating. The native Menu holds the current child. Animated backgrounds are optional and stop away from Main. These images establish emulator behavior, not physical TV performance or Plex playback.

<table><tr>
<td><img src="../art/screenshots/fixed-feeds-first-ui110-api34.png" alt="Two fixed sample feeds with first child titles and cyan artwork"><br>Fixed feed positions</td>
<td><img src="../art/screenshots/fixed-feeds-next-ui110-api34.png" alt="The same positions show next titles with matching red and yellow artwork"><br>Independent child rotation</td>
<td><img src="../art/screenshots/card-menu-ui110-api34.png" alt="Current sample card menu holds its title and offers item and feed actions"><br>Current item actions</td>
</tr><tr>
<td><img src="../art/screenshots/background-particles-ui110-api34.png" alt="Quiet particles behind the native launcher"><br>Quiet particles</td>
<td><img src="../art/screenshots/background-aurora-ui110-api34.png" alt="Aurora ribbons behind the native launcher"><br>Aurora ribbons</td>
<td><img src="../art/screenshots/background-prism-ui110-api34.png" alt="Prism light behind the native launcher"><br>Prism light</td>
</tr></table>

## Fire TV UI 1.9.2, code 63

The current picker and Blue Meridian action were captured from signed `f6e859da` on a physical API 28 Fire TV. The full button labels fit on one line. Black Screen remained selected and enabled after returning from the previews. The Split-Flap image is its own unchanged released launcher, reached through final code 62's preview-controls route before the host-only label adjustment.

<table><tr>
<td><img src="../art/screenshots/screensavers-ui-1.9.2-api28.png" alt="Final code 63 Screensavers picker with fully readable Preview controls labels"><br>Readable preview actions</td>
<td><img src="../art/screenshots/split-flap-preview-controls-ui-1.9.2-api28.png" alt="Split-Flap Drift launcher exposes Open full-screen preview separately from Settings"><br>App-owned preview controls</td>
<td><img src="../art/screenshots/blue-preview-ui-1.9.2-api28.png" alt="Blue Meridian visibly rendering detailed Earth after the final host preview action"><br>Direct app preview</td>
</tr></table>

The unchanged direct route also showed the [Cloud Drift Clock scene](../art/screenshots/cloud-preview-ui-1.9.2-api28.png) for 40 seconds on code 61. Preview controls requires choosing the app's own Preview or Show aquarium control. All 25 reviewed direct routes and eight controls routes returned with state restored on code 62. The layout-only code 63 then passed the current-label check, direct Blue route and Contour controls/full-screen Preview/Back route. These are activity-preview checks, not system DreamService binding claims. Aerial's video area remained absent from its capture; AndroSaver's selected slideshow had no images. App content and video visibility are separate from opening its preview.

[Preview behavior and available controls](screensaver-previews.md) documents the extra step. The existing offline [media attribution](screensaver-previews.md#image-sources) applies to these screensaver captures.

## Fire TV UI 1.9.1, code 59

Actual signed `2ac87445` output on the API 34 emulator. The catalog pins the public Cloud Drift Clock 2.0.0 and Blue Meridian 1.0.1 APKs. The installed picker loads their real native captures while Slot Vision stays selected. Cloud was initially absent, temporarily installed from its verified public release for this check, then removed to restore that absence. No account or existing screensaver settings were changed.

<table><tr>
<td><img src="../art/screenshots/screensavers-ui-1.9.1-api34.png" alt="Current installed picker showing the new real Cloud and Blue previews with Slot Vision still selected"><br>Real picker previews</td>
<td><img src="../art/screenshots/cloud-catalog-ui-1.9.1-api34.png" alt="Cloud Drift Clock 2.0.0 catalog entry with accurate real-flight description"><br>Cloud public catalog</td>
<td><img src="../art/screenshots/blue-catalog-ui-1.9.1-api34.png" alt="Blue Meridian installed entry with archival Earth imagery and gentle screen-drift description"><br>Blue public catalog</td>
</tr><tr>
<td><img src="../art/screenshots/cloud-2-settings-api34.png" alt="Cloud Drift Clock 2.0 native playback and clock settings without inactive palette or density widgets"><br>Cloud native controls</td>
<td><img src="../art/screenshots/preview-credits-ui-1.9.1-api34.png" alt="Current offline preview attribution and full license reader"><br>Offline media credits</td>
<td><img src="../art/screenshots/preview-credits-scroll-ui-1.9.1-api34.png" alt="Full bundled license text scrolling with the TV remote"><br>Remote scrolling</td>
</tr></table>

The Cloud settings image is that screensaver's own native activity. Fire TV UI's Screensaver settings action resolves the app-provided activity rather than inventing a generic scene editor. The credits reader opened, scrolled with the D-pad and closed with Back. All 14 portable preferences, 11 OS values, external backup bytes, storage appop and actual accessibility state were restored, with the guard stopped and the emulator alive at its prior Home. Hearth and preexisting fixtures were left untouched.

## Fire TV UI 1.9.0, code 57

Actual signed `be47742d` API 34 public self-update check before publication. The previous stable 1.8.0 APK was downloaded and verified, its compiled code 53 appeared, and the newer installed 1.9.0 build was preserved. No installation was requested.

<table><tr><td><img src="../art/screenshots/public-ui-update-ui-1.9.0-api34.png" alt="Fire TV UI 1.9.0 public self-check verifies previous stable app 1.8.0 code 53 and disables downgrade"><br>Verified public self-check</td></tr></table>

### Unchanged profile and credit controls, code 56

Actual signed `f32cbc7a` API 34 output. The profile controls use an isolated sample connection with fictional names; no account authorization, personal server details or library content are shown. The bundled media credits opened offline, scrolled with the D-pad and closed with Back. The fixture was removed after capture.

<table><tr>
<td><img src="../art/screenshots/plex-home-ui-1.9.0-api34.png" alt="Separate authorized Plex Home Continue Watching profiles using safe sample names"><br>Plex Home profiles</td>
<td><img src="../art/screenshots/preview-media-credits-ui-1.9.0-api34.png" alt="Offline preview media attribution and complete license text"><br>Preview media credits</td>
<td><img src="../art/screenshots/preview-media-credits-scroll-ui-1.9.0-api34.png" alt="Full bundled preview license scrolled with the TV remote"><br>Remote scrolling</td>
</tr></table>

## Fire TV UI 1.8.0, code 53

Actual signed `e7bcb815` API 34 output with safe sample cards. Remote Menu opened Arrange top-level cards. Moving Recently Added left changed Home feed order while preserving the items inside each feed.

<table><tr>
<td><img src="../art/screenshots/top-feed-options-ui-1.8.0-api34.png" alt="Focused card Menu includes Arrange top-level cards"><br>Card options</td>
<td><img src="../art/screenshots/top-feed-order-ui-1.8.0-api34.png" alt="Top-level feed ordering with Move left and Move right"><br>Feed order</td>
<td><img src="../art/screenshots/top-feed-home-ui-1.8.0-api34.png" alt="Recently Added moved before Continue Watching on Home"><br>Saved Home order</td>
</tr></table>

## Fire TV UI 1.7.0, code 52

Actual signed `a3c6c343` API 34 output with public sample cards. Remote Menu opened the focused card's options. Move down reordered its feed, and returning to Home showed the next item first while the other feed stayed unchanged.

<table><tr>
<td><img src="../art/screenshots/card-options-ui-1.7.0-api34.png" alt="Current item Menu offers Hide, feed ordering and source settings"><br>Inside the card</td>
<td><img src="../art/screenshots/card-order-ui-1.7.0-api34.png" alt="Individual feed cards each have Hide, Move up and Move down controls"><br>Individual card order</td>
<td><img src="../art/screenshots/card-order-home-ui-1.7.0-api34.png" alt="Home honors the changed first card while preserving the other feed"><br>Saved order on Home</td>
</tr></table>

## Fire TV UI 1.6.0 interface, code 50

Signed `45345c43` output on the API 34 TV emulator uses public sample recommendations, not personal library metadata. The actual original producer action opened item B after remote Up and Center. The recommendation setup capture precedes access approval; the later Home frame shows the published cards. Listener revoke, reconnect and disable were checked separately with exact state restoration. Final code 51 changes only package update startup dispatch; its card interface and artwork are unchanged.

<table><tr>
<td><img src="../art/screenshots/cards-home-ui-1.6.0-api34.png" alt="Fire TV UI 1.6.0 with a real sample Netflix recommendation and producer source label"><br>Recommendation on Home</td>
<td><img src="../art/screenshots/recommendation-setup-ui-1.6.0-api34.png" alt="Fire TV UI 1.6.0 optional app recommendation setup before notification access approval"><br>Explicit source access</td>
<td><img src="../art/screenshots/recommendation-action-ui-1.6.0-api34.png" alt="Original sample producer activity confirms item B opened from the featured card using the remote"><br>Exact original action</td>
</tr></table>

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
