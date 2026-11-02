package com.jeremykenedy.firetv.ui.nativeapp.help;

/** Original guidance kept separate from its presentation. */
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
                + "Preview builds keep separate settings and do not take over your normal Home button. Keep the released launcher available while reviewing a preview.", null, null),
        new Topic("remote", "Remote navigation",
                "Direction buttons move focus. Select activates the highlighted action. Hold Select on a Home app for its action menu.\n\n"
                + "In Help, choose a topic on the left. Press Right to read its paragraphs and reach a settings shortcut. Up and Down move through the content and scroll it into view. Left returns to the selected topic.\n\n"
                + "Back closes a settings page and returns to the same Help topic and position. Back from Help returns to the screen that opened it. The remote's Home button follows your device's current launcher configuration. Voice, power and volume are not remapped by the launcher.", null, null),
        new Topic("apps", "Apps and grid",
                "Home shows installed apps that provide a TV launch activity. Hold an app to add or remove a favorite, move it earlier or later, hide it, or change its icon. Favorites appear first.\n\n"
                + "Apps grid settings keeps hidden apps available for recovery. Select the app there and choose Show on Home. Hiding a tile does not uninstall the app.\n\n"
                + "Choose 2 through 12 apps per row. Vertical is the default scroll direction. Horizontal scrolling supports Auto or 1 through 8 rows. Restoring default grid dimensions returns to five columns, vertical scrolling and Auto rows without resetting app organization.\n\n"
                + "Open Settings, Home layout for App folders, Launcher locks and TV inputs. A folder member appears inside its folder rather than as a duplicate Home tile. Hold a folder or input for its available actions. Inputs depend on the TV exposing them and may be unavailable on other devices.", "Open Apps grid", "appgrid.AppGridSettingsActivity"),
        new Topic("icons", "App icons",
                "Hold an app on Home and choose Change icon. The picker opens a curated catalog instead of scanning your gallery. Choose from the reviewed icons available in this build.\n\n"
                + "Use the picker's personal image option to select a document through Android. The chosen image is copied into this app's storage and displayed proportionally. Canceling the picker keeps your current icon. Unsupported or oversized images may be rejected; choose a smaller readable image.\n\n"
                + "Use factory icon restores that tile's original artwork. Use theme icon follows the selected icon pack where it contains a matching icon, otherwise factory artwork is shown. Your other app icons remain unchanged. Return to Home with Back to check the result.", null, null),
        new Topic("focus", "Focus and motion",
                "Choose Instant, Fast, Normal or Slow transitions. Fast is the default. Instant removes the animated transition; None removes focus growth. Growth and speed are independent.\n\n"
                + "The focus border and fill have separate switches, RGB colors and opacity. Turn off focus fill for a border-only highlight. A live sample shows the selected appearance.\n\n"
                + "If a bright highlight hides your artwork, reduce fill opacity or turn fill off. If motion feels distracting, choose Instant and reduce growth. These controls change the visual transition, not the speed at which your remote sends button presses.", "Open focus settings", "focus.FocusSettingsActivity"),
        new Topic("layout", "Cards and widgets layout",
                "Your cards and widgets share one saved order. Use Show or Hide and Move up or Move down in the layout editor. A temporarily unavailable source keeps its saved position so it can return later.\n\n"
                + "Channel card dimensions apply globally. Choose a preset or enter a width from 64 through 1024 and a height from 48 through 1024 display-independent pixels. Widget sizes stay independent.\n\n"
                + "The local clock uses your TV's timezone and locale. It honors an explicitly saved launcher time format; otherwise it follows the TV's 12/24-hour setting. You can hide or restore it in the layout editor. New widgets start hidden. Select a widget in the editor to show it and configure its available content. Some need a permitted data source, device capability or setup; an unavailable source does not remove its saved position.", "Open Home layout", "layout.LayoutSettingsActivity"),
        new Topic("cards", "Channel cards",
                "A card represents one channel, with titles changing inside it. Only apps that publish supported TV recommendations can supply cards. An empty result does not mean your subscriptions or libraries are empty. If access is denied, open Cards settings, then Channel Cards and choose Allow TV listings access when shown. If a source is unavailable, browse in the publishing app.\n\n"
                + "Select or hold a card for its available actions. Hide this item removes that title from the channel. Next title advances inside the card. Provider actions appear only when supported; opening an app is separate from opening a particular title. Playback still depends on the publishing app.\n\n"
                + "Open Cards settings, then Card timers to choose Manual, 5, 8, 15 or 30 seconds, or custom whole seconds from 1 through 86400. The default is 8 seconds. A per-channel timer can override the global timer; Use global restores inheritance. Manual stops automatic title rotation.\n\n"
                + "Open Hidden items in Cards settings to restore a hidden title or series. Entries identify their source. When the provider supplies a series identity, a card can offer Hide series. If all titles are hidden, use the card's Card settings action to reach recovery.\n\n"
                + "Use the layout editor for global size, order and channel visibility. Availability, subscription entitlement and direct playback depend on the publishing app.", "Open Cards settings", "cards.CardsSettingsActivity"),
        new Topic("accessibility", "Text and contrast",
                "Open Settings, Appearance, Text and contrast to adjust text size and high contrast. Changes apply to supported launcher screens. System accessibility settings and services are managed separately by your device.", "Open text and contrast", "accessibility.AccessibilitySettingsActivity"),
        new Topic("remote-buttons", "Remote app buttons",
                "Open Settings, Device, Remote buttons to learn a supported dedicated app key and choose an available app or launcher action. Launcher-only mappings work while Home has focus. Not all remotes expose these keys to Android.\n\n"
                + "System-wide use requires the production launcher, its enabled key-filter accessibility service and device support. Test the physical button from Home and another app: receiving a key does not guarantee the original app is suppressed. Voice, Home, power and volume are not mapped by this feature. Restore factory actions removes the launcher mappings.", "Open remote buttons", "device.remotebuttons.RemoteButtonsActivity"),
        new Topic("dashboards", "Dashboards and schedules",
                "Open Settings, Home layout, Dashboards and schedules to save layout snapshots and configure rotation or a daily schedule. A manual selection stays active until you choose Resume automatic. The layout editor controls card and widget order and visibility. Schedules use the selected time zone and apply while Home is active.", "Open dashboards", "layoutschedule.LayoutScheduleActivity"),
        new Topic("limits", "Troubleshooting",
                "A missing app may not provide a TV launch activity. Check Apps grid for hidden apps first. If an app no longer opens, check whether it is still installed using your device's normal app settings.\n\n"
                + "Personal image selection depends on a document provider being installed. A canceled or rejected selection keeps the existing icon. Use a curated icon if the document provider cannot open.\n\n"
                + "Cards and widgets depend on their sources, permissions and device capabilities. A provider can be empty, unavailable or require setup; check its settings and source app. Direct playback and entitlement remain with the publishing app.\n\n"
                + "Preview settings are separate from the released app. Do not remove the released app or clear its data to troubleshoot a preview.", null, null),
        new Topic("licenses", "Licenses and credits",
                "Fire TV UI\nCopyright (c) 2026 Jeremy Kenedy\n\n"
                + "Original project code, VCR, Stereo Receiver, Laptop and PC pictograms, and project tile backgrounds/layout are covered by the MIT license below. The remote illustration in this Help is drawn locally by original source code.\n\n"
                + "JBL, Sonos, Samsung and LG artwork comes from Simple Icons under CC0-1.0. The pinned upstream revision is 90646e4bf7f3b89df6c37e65baf3e7bc759a517c. Sources and source/image hashes are recorded in native/icons/provenance.json; the catalog notice is native/icons/NOTICE.txt. CC0 information: https://creativecommons.org/publicdomain/zero/1.0/\n\n"
                + "Brand names and trademarks belong to their owners. Installed app names and artwork identify those apps. The available catalog depends on the installed build.\n\n"
                + "The MIT License (MIT)\n\n"
                + "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\n"
                + "The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\n"
                + "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.", null, null)
    };

    private HelpTopics() {}
    public static int count() { return TOPICS.length; }
    public static Topic at(int index) { return TOPICS[index < 0 || index >= TOPICS.length ? 0 : index]; }
    public static String bodyForPackage(Topic topic, String packageName) {
        if ("com.jeremykenedy.firetv.ui.preview".equals(packageName)) return topic.body;
        if ("start".equals(topic.key)) {
            return topic.body.substring(0, topic.body.indexOf("\n\n"))
                    + "\n\nChoose your preferred Home configuration in device settings. Available options depend on the device and any accessibility service you enable.";
        }
        if ("limits".equals(topic.key)) {
            return topic.body.substring(0, topic.body.lastIndexOf("\n\n"))
                    + "\n\nIf startup cannot finish, follow the on-screen Retry or Open device Home options. Reconnect a missing source app or input before retrying. Do not clear app data or uninstall the launcher to bypass recovery.";
        }
        return topic.body;
    }
    public static int indexOf(String key) {
        for (int i = 0; i < TOPICS.length; i++) if (TOPICS[i].key.equals(key)) return i;
        return 0;
    }
}
