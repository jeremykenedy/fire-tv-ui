package com.jeremykenedy.firetv.ui.nativeapp.help;

/** Original guidance for the source preview, kept separate from its presentation. */
public final class HelpTopics {
    public static final class Topic {
        public final String key;
        public final String title;
        public final String body;
        public final String settingsLabel;
        public final String settingsClass;

        private Topic(String key, String title, String body, String label, String route) {
            this.key = key;
            this.title = title;
            this.body = body;
            this.settingsLabel = label;
            this.settingsClass = route == null ? null : "com.jeremykenedy.firetv.ui.nativeapp." + route;
        }
    }

    private static final Topic[] TOPICS = {
        new Topic("start", "Getting started",
                "Use the direction buttons to highlight an app, then press Select to open it. Settings and Help are above the app grid. Hold Select on an app to open its actions.\n\n"
                + "This source preview has its own settings. It does not import your production configuration or take over your normal Home button. Keep the released launcher available while reviewing this preview.", null, null),
        new Topic("remote", "Remote navigation",
                "Direction buttons move focus. Select activates the highlighted action. Hold Select on a Home app for its action menu.\n\n"
                + "In Help, choose a topic on the left. Press Right to read its paragraphs and reach a settings shortcut. Up and Down move through the content and scroll it into view. Left returns to the selected topic.\n\n"
                + "Back closes a settings page and returns to the same Help topic and position. Back from Help returns to the screen that opened it. The remote's Home button follows your device's current launcher configuration. Branded app buttons, voice, power and volume are not remapped here.", null, null),
        new Topic("apps", "Apps and grid",
                "Home shows installed apps that provide a TV launch activity. Hold an app to add or remove a favorite, move it earlier or later, hide it, or change its icon. Favorites appear first.\n\n"
                + "Apps grid settings keeps hidden apps available for recovery. Select the app there and choose Show on Home. Hiding a tile does not uninstall the app.\n\n"
                + "Choose 2 through 12 apps per row. Vertical is the default scroll direction. Horizontal scrolling supports Auto or 1 through 8 rows. Restoring default grid dimensions returns to five columns, vertical scrolling and Auto rows without resetting app organization.\n\n"
                + "Folders, app locks and named input tiles are not available in this preview.", "Open Apps grid", "appgrid.AppGridSettingsActivity"),
        new Topic("icons", "App icons",
                "Hold an app on Home and choose Change icon. The picker opens a curated catalog instead of scanning your gallery. Eight reviewed icons are included: VCR, Stereo Receiver, Laptop, PC, JBL, Sonos, Samsung and LG.\n\n"
                + "Use the picker's personal image option to select a document through Android. The chosen image is copied into this app's storage and displayed proportionally. Canceling the picker keeps your current icon. Unsupported or oversized images may be rejected; choose a smaller readable image.\n\n"
                + "Factory reset and theme reset affect only the selected app. Theme reset currently falls back to factory artwork because theme icon rendering is not integrated. Your other app icons remain unchanged. Return to Home with Back to check the result.", null, null),
        new Topic("focus", "Focus and motion",
                "Choose Instant, Fast, Normal or Slow transitions. Fast is the default. Instant removes the animated transition; None removes focus growth. Growth and speed are independent.\n\n"
                + "The focus border and fill have separate switches, RGB colors and opacity. Turn off focus fill for a border-only highlight. A live sample shows the selected appearance.\n\n"
                + "If a bright highlight hides your artwork, reduce fill opacity or turn fill off. If motion feels distracting, choose Instant and reduce growth. These controls change the visual transition, not the speed at which your remote sends button presses.", "Open focus settings", "focus.FocusSettingsActivity"),
        new Topic("layout", "Cards and widgets layout",
                "Your cards and widgets share one saved order. Use Show or Hide and Move up or Move down in the layout editor. A temporarily unavailable source keeps its saved position so it can return later.\n\n"
                + "Channel card dimensions apply globally. Choose a preset or enter a width from 64 through 1024 and a height from 48 through 1024 display-independent pixels. Widget sizes stay independent.\n\n"
                + "The local clock uses your TV's timezone, locale and system 12/24-hour setting. You can hide or restore it in the layout editor. Other planned widgets are not installed by choosing a layout.", "Open Home layout", "layout.LayoutSettingsActivity"),
        new Topic("cards", "Channel cards",
                "A card represents one channel, with titles changing inside it. Only apps that publish supported TV recommendations can supply cards. An empty result does not mean your subscriptions or libraries are empty. If access is denied, open Cards settings, then Channel Cards and choose Allow TV listings access when shown. If a source is unavailable, browse in the publishing app.\n\n"
                + "Select or hold a card for its available actions. Hide this item removes that title from the channel. Next title advances inside the card. Provider actions appear only when supported; opening an app is separate from opening a particular title. Playback still depends on the publishing app.\n\n"
                + "Open Cards settings, then Card timers to choose Manual, 5, 8, 15 or 30 seconds, or custom whole seconds from 1 through 86400. The default is 8 seconds. A per-channel timer can override the global timer; Use global restores inheritance. Manual stops automatic title rotation.\n\n"
                + "Open Hidden items in Cards settings to restore a hidden title. Entries identify their source. If all titles are hidden, use the card's Card settings action to reach recovery. Series-wide hiding is not available.\n\n"
                + "Use the layout editor for global size, order and channel visibility. This preview does not promise every app's catalog, subscription entitlement or direct playback.", "Open Cards settings", "cards.CardsSettingsActivity"),
        new Topic("limits", "Preview and troubleshooting",
                "A missing app may not provide a TV launch activity. Check Apps grid for hidden apps first. If an app no longer opens, check whether it is still installed using your device's normal app settings.\n\n"
                + "Personal image selection depends on a document provider being installed. A canceled or rejected selection keeps the existing icon. Use a curated icon if the document provider cannot open.\n\n"
                + "Themes and backgrounds, screensaver administration, startup and system Home handling, updates, backup and named saved setups, network diagnostics and the wider widget catalog are not integrated into this preview. Use the released launcher or your device's normal settings for those functions.\n\n"
                + "Preview settings are separate from the released app. Do not remove the released app or clear its data to troubleshoot this preview.", null, null),
        new Topic("licenses", "Licenses and credits",
                "Fire TV UI source preview\nCopyright (c) 2026 Jeremy Kenedy\n\n"
                + "Original project code, VCR, Stereo Receiver, Laptop and PC pictograms, and project tile backgrounds/layout are covered by the MIT license below. The remote illustration in this Help is drawn locally by original source code.\n\n"
                + "JBL, Sonos, Samsung and LG artwork comes from Simple Icons under CC0-1.0. The pinned upstream revision is 90646e4bf7f3b89df6c37e65baf3e7bc759a517c. Sources and source/image hashes are recorded in native/icons/provenance.json; the catalog notice is native/icons/NOTICE.txt. CC0 information: https://creativecommons.org/publicdomain/zero/1.0/\n\n"
                + "Brand names and trademarks belong to their owners. Installed app names and artwork identify those apps. The remaining production icon catalog is not included in this preview.\n\n"
                + "The MIT License (MIT)\n\n"
                + "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\n"
                + "The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\n"
                + "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.", null, null)
    };

    private HelpTopics() {}
    public static int count() { return TOPICS.length; }
    public static Topic at(int index) { return TOPICS[index < 0 || index >= TOPICS.length ? 0 : index]; }
    public static int indexOf(String key) {
        for (int i = 0; i < TOPICS.length; i++) if (TOPICS[i].key.equals(key)) return i;
        return 0;
    }
}
