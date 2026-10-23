package com.jeremykenedy.firetv.ui;

final class CardSourceStatus {
    private CardSourceStatus() {
    }

    static String summary(boolean hidden, int preview, int watchNext, boolean failed) {
        if (failed) {
            return "Some card sources could not be read. Review the source results below; an unavailable source is not an empty source.";
        }
        if (hidden) {
            return "Featured cards are hidden. Turn on Featured cards in Appearance > Cards > Featured card setup to show published cards.";
        }
        if (preview == 0 && watchNext == 0) {
            return "Featured cards are enabled, but installed apps have not published any Android TV preview or Continue Watching cards. Enabling cards does not create recommendations. Open a compatible app and use its recommendations or recent channels, then refresh.";
        }
        return "Apps have published " + preview + " preview cards and " + watchNext
                + " System Watch Next cards. Personal Plex feeds are published as preview cards. App and channel visibility settings can hide individual sources. Reload home cards after changing a source.";
    }
}
